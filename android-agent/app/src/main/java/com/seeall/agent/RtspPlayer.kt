package com.seeall.agent

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import android.util.Base64

/** Cliente RTSP + decodificador H.264 para Surface (versão Android). */
class RtspPlayer(
    private val uri: String,
    private val username: String?,
    private val password: String?,
    private val surface: Surface,
    private val onStatus: (String) -> Unit
) {
    private var socket: Socket? = null
    private var session: String? = null
    private var cseq = 0
    private var codec: MediaCodec? = null
    @Volatile private var running = false

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            onStatus("Connecting...")
            val parsed = parseUri(uri)
            socket = Socket().apply {
                connect(InetSocketAddress(parsed.host, parsed.port), 15000)
                soTimeout = 20000
                tcpNoDelay = true
            }
            val out = socket!!.getOutputStream()
            val input = DataInputStream(BufferedInputStream(socket!!.getInputStream()))

            send(out, "OPTIONS ${parsed.path} RTSP/1.0", "CSeq: ${next()}")
            read(input)

            send(out, "DESCRIBE ${parsed.path} RTSP/1.0", "CSeq: ${next()}", "Accept: application/sdp")
            val describe = read(input)
            val sdp = describe.body
            val control = Regex("a=control:(.*)", RegexOption.IGNORE_CASE)
                .find(sdp)?.groupValues?.get(1)?.trim()?.takeIf { it != "*" } ?: parsed.path
            if (!sdp.contains("H264", true) && !sdp.contains("96", true)) {
                throw IllegalStateException("Camera não anuncia H.264")
            }

            val setupUrl = if (control.startsWith("rtsp")) control else parsed.base + control
            send(out, "SETUP $setupUrl RTSP/1.0",
                "CSeq: ${next()}", "Transport: RTP/AVP/TCP;unicast;interleaved=0-1")
            val setup = read(input)
            session = setup.headers["Session"]?.substringBefore(';')

            val playUrl = if (control.startsWith("rtsp")) control else parsed.base + control
            send(out, "PLAY $playUrl RTSP/1.0", "CSeq: ${next()}", "Session: $session")
            read(input)

            initCodec()
            onStatus("Playing")
            running = true
            readFrames(input)
        } catch (e: Exception) {
            onStatus("Error: ${e.message}")
        } finally {
            stop()
        }
    }

    private fun initCodec() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, surface, null, 0)
            start()
        }
    }

    private fun readFrames(input: DataInputStream) {
        val buffer = ByteArray(65536)
        val fu = ByteArrayOutputStream()
        while (running) {
            val b = input.read()
            if (b == -1) break
            if (b != 0x24) continue
            val channel = input.read()
            val length = input.readUnsignedShort()
            if (length <= 0 || length > buffer.size) continue
            input.readFully(buffer, 0, length)
            if (channel != 0) continue
            val nal = unwrap(buffer, length, fu) ?: continue
            feed(nal)
        }
    }

    private fun feed(nal: ByteArray) {
        val c = codec ?: return
        try {
            val index = c.dequeueInputBuffer(10000)
            if (index >= 0) {
                c.getInputBuffer(index)?.let { buf ->
                    buf.clear()
                    buf.put(nal)
                    c.queueInputBuffer(index, 0, nal.size, System.currentTimeMillis() * 1000, 0)
                }
            }
            val info = MediaCodec.BufferInfo()
            var out = c.dequeueOutputBuffer(info, 1000)
            while (out >= 0) {
                c.releaseOutputBuffer(out, true)
                out = c.dequeueOutputBuffer(info, 0)
            }
        } catch (_: Exception) {}
    }

    private fun unwrap(pkt: ByteArray, len: Int, fu: ByteArrayOutputStream): ByteArray? {
        if (len < 13) return null
        val pt = pkt[1].toInt() and 0x7F
        if (pt != 96 && pt != 97) return null
        val off = 12 + ((pkt[0].toInt() and 0x0F) * 4)
        if (off >= len) return null
        val h = pkt[off].toInt() and 0xFF
        val type = h and 0x1F
        val payload = pkt.copyOfRange(off + 1, len)
        return when (type) {
            28 -> {
                val start = payload[0].toInt() and 0x80 != 0
                val end = payload[1].toInt() and 0x40 != 0
                val nh = (payload[0].toInt() and 0xE0) or (payload[1].toInt() and 0x1F)
                if (start) { fu.reset(); fu.write(nh) }
                fu.write(payload, 2, payload.size - 2)
                if (end) fu.toByteArray().also { fu.reset() } else null
            }
            else -> byteArrayOf(h.toByte()) + payload
        }
    }

    fun stop() {
        running = false
        try { codec?.stop(); codec?.release() } catch (_: Exception) {}
        codec = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun next(): Int = ++cseq

    private fun send(out: OutputStream, line: String, vararg extra: String) {
        val lines = buildList {
            add(line); addAll(extra)
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                add("Authorization: Basic " + Base64.encodeToString(
                    "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            }
            add("User-Agent: seeAll-agent/1.0"); add(""); add("")
        }
        out.write(lines.joinToString("\r\n").toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private data class Resp(val status: Int, val headers: Map<String, String>, val body: String)

    private fun read(input: DataInputStream): Resp {
        val headers = mutableMapOf<String, String>()
        var status = 0
        var line = readLine(input)
        if (line.isNotBlank()) status = line.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
        while (true) {
            line = readLine(input)
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        val len = headers["Content-Length"]?.toIntOrNull() ?: 0
        val body = if (len > 0) {
            val bytes = ByteArray(len)
            input.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        } else ""
        if (status !in 200..299) throw IllegalStateException("RTSP $status: ${body.take(120)}")
        return Resp(status, headers, body)
    }

    private fun readLine(input: DataInputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("US-ASCII").trim()
    }

    private data class Parsed(val host: String, val port: Int, val path: String, val base: String)

    private fun parseUri(value: String): Parsed {
        val s = value.removePrefix("rtsp://")
        val authority = s.substringBefore('/')
        val path = "/" + s.substringAfter('/', "")
        return Parsed(
            authority.substringBefore(':'),
            authority.substringAfter(':', "554").toIntOrNull() ?: 554,
            path,
            "rtsp://$authority"
        )
    }
}
