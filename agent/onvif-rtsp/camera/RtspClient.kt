package com.seeall.agent.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64

/**
 *Cliente RTSP mínimo sobre TCP interleaved (RTP/RTCP no mesmo socket).
 *Faz OPTIONS -> DESCRIBE -> SETUP -> PLAY e emite NALUs H.264 completas.
 */
class RtspClient(
    private val uri: String,
    private val username: String? = null,
    private val password: String? = null,
    private val socketTimeoutMs: Int = 15_000
) {
    private var socket: Socket? = null
    private var sessionId: String? = null
    private var cseq = 0
    private var videoChannel = 0
    @Volatile private var playing = false

    val frames: Flow<H264Frame> = flow {
        check(playing) { "RTSP não está tocando" }
        val input = DataInputStream(BufferedInputStream(socket!!.getInputStream()))
        val buffer = ByteArray(1500)
        val fuBuffer = java.io.ByteArrayOutputStream()

        while (playing) {
            // Procurar starts de interleaved frame: '$'
            var b = input.read()
            if (b == -1) break
            if (b != 0x24) continue
            val channel = input.read()
            val length = input.readUnsignedShort()
            if (length <= 0 || length > buffer.size * 4) continue
            input.readFully(buffer, 0, length)
            if (channel != videoChannel) continue // RTCP ou outro track

            val nal = unwrapRtpToNal(buffer, length, fuBuffer) ?: continue
            emit(H264Frame(nal, System.currentTimeMillis(), isKeyFrame(nal)))
        }
    }

    suspend fun connectAndPlay(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = parseUri(uri)
            socket = Socket().apply {
                connect(InetSocketAddress(parsed.host, parsed.port), socketTimeoutMs)
                soTimeout = socketTimeoutMs
                tcpNoDelay = true
            }
            val out = socket!!.getOutputStream()
            val input = socket!!.getInputStream()

            send(out, "OPTIONS ${parsed.path} RTSP/1.0", extra = listOf("CSeq: ${nextCseq()}"))
            readResponse(input)

            send(
                out, "DESCRIBE ${parsed.path} RTSP/1.0",
                extra = listOf("CSeq: ${nextCseq()}", "Accept: application/sdp")
            )
            val describeResponse = readResponse(input)
            val sdp = describeResponse.body
            val control = parseSdpControl(sdp) ?: parsed.path
            val isH264 = sdp.contains("m=video", ignoreCase = true) &&
                (sdp.contains("96", ignoreCase = true) || sdp.contains("H264", ignoreCase = true))
            check(isH264) { "SDP não anuncia H.264" }

            val setupUrl = if (control.startsWith("rtsp://")) control else "${parsed.baseUrl}$control"
            send(
                out, "SETUP $setupUrl RTSP/1.0",
                extra = listOf(
                    "CSeq: ${nextCseq()}",
                    "Transport: RTP/AVP/TCP;unicast;interleaved=0-1"
                )
            )
            val setupResponse = readResponse(input)
            sessionId = setupResponse.headers["Session"]?.substringBefore(';')
            check(!sessionId.isNullOrBlank()) { "SETUP sem Session" }
            videoChannel = 0

            val playUrl = if (control.startsWith("rtsp://")) control else "${parsed.baseUrl}$control"
            send(
                out, "PLAY $playUrl RTSP/1.0",
                extra = listOf("CSeq: ${nextCseq()}", "Session: $sessionId", "Range: npt=0.000-")
            )
            readResponse(input)
            playing = true
        }.onFailure {
            close()
        }
    }

    fun close() {
        playing = false
        socket?.close()
        socket = null
    }

    private fun nextCseq(): Int = ++cseq

    private fun send(out: OutputStream, requestLine: String, extra: List<String>): String {
        val lines = buildList {
            add(requestLine)
            addAll(extra)
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                add("Authorization: Basic $token")
            }
            add("User-Agent: seeAll-agent/1.0")
            add("")
            add("")
        }
        val payload = lines.joinToString("\r\n")
        out.write(payload.toByteArray(Charsets.US_ASCII))
        out.flush()
        return payload
    }

    private data class RtspResponse(val status: Int, val headers: Map<String, String>, val body: String)

    private fun readResponse(input: java.io.InputStream): RtspResponse {
        val headers = mutableMapOf<String, String>()
        var status = 0
        var line = readLine(input)
        if (line.isNotEmpty()) {
            val parts = line.split(" ", limit = 3)
            status = parts.getOrNull(1)?.toIntOrNull() ?: 0
        }
        while (true) {
            line = readLine(input)
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        val length = headers["Content-Length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) String(input.readNBytes(length), Charsets.UTF_8) else ""
        check(status in 200..299) { "RTSP status $status: ${body.take(200)}" }
        return RtspResponse(status, headers, body)
    }

    private fun readLine(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString(Charsets.US_ASCII.name()).trim()
    }

    private data class ParsedUri(val host: String, val port: Int, val path: String, val baseUrl: String)

    private fun parseUri(value: String): ParsedUri {
        val stripped = value.removePrefix("rtsp://").removePrefix("rtsps://")
        val authority = stripped.substringBefore('/')
        val path = "/" + stripped.substringAfter('/', "")
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "554").toIntOrNull() ?: 554
        return ParsedUri(host, port, path, "rtsp://$authority")
    }

    private fun parseSdpControl(sdp: String): String? =
        sdp.lineSequence().mapNotNull { line ->
            line.takeIf { it.startsWith("a=control:", ignoreCase = true) }
                ?.substringAfter(':', "")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "*" }
        }.firstOrNull()

    private fun unwrapRtpToNal(packet: ByteArray, length: Int, fu: java.io.ByteArrayOutputStream): ByteArray? {
        if (length < 12) return null
        val payloadType = packet[1].toInt() and 0x7F
        if (payloadType != 96 && payloadType != 97) return null
        val marker = packet[1].toInt() and 0x80 != 0
        val offset = 12 + ((packet[0].toInt() and 0x0F) * 4)
        if (offset >= length) return null
        val nalHeader = packet[offset].toInt() and 0xFF
        val type = nalHeader and 0x1F
        val payload = packet.copyOfRange(offset + 1, length)

        return when (type) {
            28 -> { // FU-A
                val start = payload[0].toInt() and 0x80 != 0
                val end = payload[1].toInt() and 0x40 != 0
                val reconstructedHeader = (payload[0].toInt() and 0xE0) or (payload[1].toInt() and 0x1F)
                if (start) {
                    fu.reset()
                    fu.write(reconstructedHeader)
                }
                fu.write(payload, 2, payload.size - 2)
                if (end || marker) fu.toByteArray().also { fu.reset() } else null
            }
            29 -> null // FU-B, ignorado
            else -> byteArrayOf(nalHeader.toByte()) + payload // NAL unit única (IDR/SPS/PPS)
        }
    }

    private fun isKeyFrame(nal: ByteArray): Boolean {
        if (nal.isEmpty()) return false
        return (nal[0].toInt() and 0x1F) == 5 // IDR
    }

    data class H264Frame(val nalu: ByteArray, val timestampMs: Long, val keyFrame: Boolean)
}
