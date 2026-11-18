package com.seeall.agent.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

/**
 * WS-Discovery sobre UDP multicast (239.255.255.250:3702).
 * Retorna os endereços XAddr de dispositivos ONVIF na LAN.
 */
class OnvifDiscovery(
    private val timeoutMs: Int = 3_000,
    private val repeats: Int = 2
) {
    private val multicastAddress: InetAddress = InetAddress.getByName("239.255.255.250")
    private val port = 3702

    suspend fun discover(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, DiscoveredDevice>()
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
        }
        try {
            val message = probeMessage()
            val bytes = message.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, multicastAddress, port)
            val buffer = ByteArray(16_384)

            repeat(repeats) {
                socket.send(packet)
                val deadline = System.currentTimeMillis() + timeoutMs.toLong()
                while (System.currentTimeMillis() < deadline) {
                    socket.soTimeout = maxOf(200, (deadline - System.currentTimeMillis()).toInt())
                    val response = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(response)
                        val xml = String(response.data, response.offset, response.length, Charsets.UTF_8)
                        parseProbeMatch(xml, response.address.hostAddress)?.let { device ->
                            results[device.xaddr] = device
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } finally {
            socket.close()
        }
        results.values.toList()
    }

    private fun probeMessage(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<e:Probe xmlns:e="http://schemas.xmlsoap.org/ws/2005/04/discovery">""")
        append("""<e:Types>tds:Device</e:Types>""")
        append("""<e:Scopes>onvif://www.onvif.org/type</e:Scopes>""")
        append("""</e:Probe>""")
        // Header com MessageID e To é opcional em WS-Discovery, mas alguns devices exigem.
    }

    private fun parseProbeMatch(xml: String, from: String): DiscoveredDevice? = try {
        val root = parseXml(xml)
        val match = root.descendants("ProbeMatches").firstOrNull()
            ?.let { it.descendants("ProbeMatch").firstOrNull() } ?: return null
        val xaddr = match.descendants("XAddr").firstOrNull()?.textContent?.trim()
            ?: return null
        val types = match.descendants("Types").firstOrNull()?.textContent?.trim().orEmpty()
        val scopes = match.descendants("Scopes").firstOrNull()?.textContent?.trim().orEmpty()
        val model = scopes.split(" ")
            .firstOrNull { it.contains("/name/", ignoreCase = true) }
            ?.substringAfterLast("/")
        val hardware = scopes.split(" ")
            .firstOrNull { it.contains("/hardware/", ignoreCase = true) }
            ?.substringAfterLast("/")
        DiscoveredDevice(
            xaddr = xaddr,
            address = from,
            types = types,
            model = model,
            hardware = hardware
        )
    } catch (_: Exception) {
        null
    }

    data class DiscoveredDevice(
        val xaddr: String,
        val address: String,
        val types: String,
        val model: String?,
        val hardware: String?
    )
}
