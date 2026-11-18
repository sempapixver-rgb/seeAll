package com.seeall.agent.camera

import android.content.Context
import com.seeall.agent.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Helper ONVIF mínimo (independente): resolve URI de stream e envia comandos PTZ. */
class OnvifPtz(private val context: Context, private val xaddr: String, private val config: ConfigStore) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val soapType = "application/soap+xml; charset=utf-8".toMediaType()

    companion object {
        suspend fun resolveUri(xaddr: String, config: ConfigStore): String? = withContext(Dispatchers.IO) {
            runCatching {
                val xml = soapCall(
                    xaddr = xaddr,
                    body = "<trt:GetProfiles/>",
                    action = "http://www.onvif.org/ver10/media/wsdl/GetProfiles"
                )
                val tokens = Regex("""<trt:Profiles[^>]*token="([^"]+)"|<tt:token>([^<]+)</tt:token>""")
                    .findAll(xml).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
                val token = tokens.firstOrNull() ?: return@runCatching null
                val uriXml = soapCall(
                    xaddr = xaddr,
                    body = """
                        <trt:GetStreamUri>
                          <trt:StreamSetup>
                            <tt:Stream>RTP unicast</tt:Stream>
                            <tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>
                          </trt:StreamSetup>
                          <trt:ProfileToken>$token</trt:ProfileToken>
                        </trt:GetStreamUri>
                    """.trimIndent(),
                    action = "http://www.onvif.org/ver10/media/wsdl/GetStreamUri"
                )
                Regex("""<tt:Uri>([^<]+)</tt:Uri>""").find(uriXml)?.groupValues?.get(1)?.trim()
            }.getOrNull()
        }

        private fun soapCall(xaddr: String, body: String, action: String): String {
            val envelope = """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
 xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
<soap:Body>$body</soap:Body></soap:Envelope>""".trimIndent()
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
            val type = "application/soap+xml; charset=utf-8".toMediaType()
            val req = Request.Builder()
                .url(xaddr)
                .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
                .addHeader("SOAPAction", "\"$action\"")
                .post(envelope.toRequestBody(type))
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("ONVIF SOAP ${r.code}")
                return text
            }
        }
    }

    suspend fun move(direction: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val xml = soapCall(
                xaddr = xaddr,
                body = "<trt:GetProfiles/>",
                action = "http://www.onvif.org/ver10/media/wsdl/GetProfiles"
            )
            val token: String = Regex("""<trt:Profiles[^>]*token="([^"]+)"|<tt:token>([^<]+)</tt:token>""")
                .find(xml)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                ?: return@runCatching "Câmera sem perfil PTZ"
            val (x: Double, y: Double) = when (direction) {
                "up" -> 0.0 to 1.0
                "down" -> 0.0 to -1.0
                "left" -> -1.0 to 0.0
                "right" -> 1.0 to 0.0
                else -> 0.0 to 0.0
            }
            val moveBody = """
                <tptz:ContinuousMove>
                  <tptz:ProfileToken>$token</tptz:ProfileToken>
                  <tptz:Velocity><tt:PanTilt x="$x" y="$y"/></tptz:Velocity>
                </tptz:ContinuousMove>
            """.trimIndent()
            val envelope = """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
 xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
<soap:Body>$moveBody</soap:Body></soap:Envelope>""".trimIndent()
            val req: Request = Request.Builder()
                .url(xaddr)
                .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
                .addHeader("SOAPAction", "\"http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove\"")
                .post(envelope.toRequestBody(soapType))
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) "PTZ falhou: HTTP ${r.code}" else "PTZ: $direction enviado"
            }
        }.getOrElse { e -> "PTZ indisponível: ${e.message?.take(60)}" }
    }
}
