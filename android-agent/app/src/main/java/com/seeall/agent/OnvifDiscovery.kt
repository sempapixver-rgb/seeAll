package com.seeall.agent

import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class FoundCamera(val name: String, val host: String, val xaddr: String)

class OnvifDiscovery(private val wifiManager: WifiManager?) {
    private val multicast = InetAddress.getByName("239.255.255.250")
    private val port = 3702

    suspend fun discover(timeoutMs: Int = 4000): List<FoundCamera> = withContext(Dispatchers.IO) {
        val lock = wifiManager?.createMulticastLock("seeall-onvif")?.apply { acquire() }
        val results = LinkedHashMap<String, FoundCamera>()
        val socket = DatagramSocket(null).apply { reuseAddress = true; broadcast = true }
        try {
            val probe = """<?xml version="1.0" encoding="UTF-8"?>
<e:Probe xmlns:e="http://schemas.xmlsoap.org/ws/2005/04/discovery">
  <e:Types>tds:Device</e:Types>
  <e:Scopes>onvif://www.onvif.org/type</e:Scopes>
</e:Probe>""".trimIndent()
            val bytes = probe.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, multicast, port)
            val buffer = ByteArray(16384)

            repeat(2) {
                socket.send(packet)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    socket.soTimeout = maxOf(200, (deadline - System.currentTimeMillis()).toInt())
                    val response = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(response)
                        val xml = String(response.data, response.offset, response.length, Charsets.UTF_8)
                        parse(xml, response.address.hostAddress)?.let { results[it.xaddr] = it }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } finally {
            socket.close()
            try { lock?.release() } catch (_: Exception) {}
        }
        results.values.toList()
    }

    private fun parse(xml: String, from: String): FoundCamera? {
        if (!xml.contains("ProbeMatch", ignoreCase = true)) return null
        val xaddr = extract(xml, "XAddr") ?: return null
        val scopes = extract(xml, "Scopes").orEmpty()
        val name = scopes.split(" ")
            .firstOrNull { it.contains("/name/", ignoreCase = true) }
            ?.substringAfterLast("/") ?: from
        return try {
            FoundCamera(name, from, xaddr)
        } catch (_: Exception) {
            null
        }
    }

    private fun extract(xml: String, tag: String): String? {
        val regex = Regex("<[^>]*$tag[^>]*>(.*?)</[^>]*$tag[^>]*>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }
}
