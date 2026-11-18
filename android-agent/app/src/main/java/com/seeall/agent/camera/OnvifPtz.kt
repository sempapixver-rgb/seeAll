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

/** Helper ONVIF mínimo: resolve URI de stream e envia comandos PTZ. */
class OnvifPtz(private val context: Context, private val xaddr: String, private val config: ConfigStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val soapType = "application/soap+xml; charset=utf-8".toMediaType()

    companion object {
        suspend fun resolveUri(xaddr: String, config: ConfigStore): String? = withContext(Dispatchers.IO) {
            runCatching {
                val client = OnvifClient(xaddr, null, null)
                val profiles = client.getProfiles()
                val token = profiles.firstOrNull()?.token ?: return@withContext null
                client.getStreamUri(token).uri
            }.getOrNull()
        }
    }

    suspend fun move(direction: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val client = OnvifClient(xaddr, null, null)
            val token = client.getProfiles().firstOrNull()?.token
                ?: return@withContext "Câmera sem perfil PTZ"
            val (x, y) = when (direction) {
                "up" -> 0.0 to 1.0
                "down" -> 0.0 to -1.0
                "left" -> -1.0 to 0.0
                "right" -> 1.0 to 0.0
                else -> 0.0 to 0.0
            }
            val body = """
                <tptz:ContinuousMove>
                  <tptz:ProfileToken>$token</tptz:ProfileToken>
                  <tptz:Velocity>
                    <tt:PanTilt x="$x" y="$y"/>
                  </tptz:Velocity>
                </tptz:ContinuousMove>
            """.trimIndent()
            val envelope = """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
 xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
<soap:Body>$body</soap:Body></soap:Envelope>""".trimIndent()
            val req = Request.Builder()
                .url(xaddr)
                .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
                .addHeader("SOAPAction", "\"http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove\"")
                .post(envelope.toRequestBody(soapType))
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) "PTZ falhou: HTTP ${r.code}" else "PTZ: $direction enviado"
            }
        }.getOrElse { "PTZ indisponível: ${it.message?.take(60)}" }
    }
}
