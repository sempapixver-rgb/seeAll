package com.seeall.agent.camera

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Cliente SOAP mínimo para ONVIF Device + Media. */
class OnvifClient(
    private val xaddr: String,
    private val username: String?,
    private val password: String?,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val soapMediaType = "application/soap+xml; charset=utf-8".toMediaType()

    data class DeviceInfo(
        val manufacturer: String?,
        val model: String?,
        val firmwareVersion: String?,
        val serialNumber: String?
    )

    data class Profile(
        val token: String,
        val name: String?,
        val encoding: String?
    )

    data class StreamInfo(val uri: String)

    suspend fun getDeviceInformation(): DeviceInfo = soap(
        """<tds:GetDeviceInformation/>""",
        "http://www.onvif.org/ver10/device/wsdl/GetDeviceInformation"
    ).let { xml ->
        val root = parseXml(xml)
        DeviceInfo(
            manufacturer = root.childText("Manufacturer"),
            model = root.childText("Model"),
            firmwareVersion = root.childText("FirmwareVersion"),
            serialNumber = root.childText("SerialNumber")
        )
    }

    suspend fun getProfiles(): List<Profile> = soap(
        """<trt:GetProfiles/>""",
        "http://www.onvif.org/ver10/media/wsdl/GetProfiles"
    ).let { xml ->
        parseXml(xml).descendants("Profiles").map { node ->
            val token = node.getAttribute("token").takeIf { it.isNotBlank() }
                ?: node.childText("token").orEmpty()
            Profile(
                token = token,
                name = node.childText("Name"),
                encoding = node.descendants("Encoding").firstOrNull()?.textContent?.trim()
            )
        }
    }

    suspend fun getStreamUri(profileToken: String): StreamInfo {
        val body = """
            <trt:GetStreamUri>
              <trt:StreamSetup>
                <tt:Stream>RTP unicast</tt:Stream>
                <tt:Transport>
                  <tt:Protocol>RTSP</tt:Protocol>
                </tt:Transport>
              </trt:StreamSetup>
              <trt:ProfileToken>${escapeXml(profileToken)}</trt:ProfileToken>
            </trt:GetStreamUri>
        """.trimIndent()
        return soap(body, "http://www.onvif.org/ver10/media/wsdl/GetStreamUri").let { xml ->
            val uri = parseXml(xml).descendants("Uri").firstOrNull()?.textContent?.trim()
                ?: error("ONVIF GetStreamUri sem Uri")
            StreamInfo(uri)
        }
    }

    suspend fun getSnapshotUri(profileToken: String): String? = try {
        val body = """
            <trt:GetSnapshotUri>
              <trt:ProfileToken>${escapeXml(profileToken)}</trt:ProfileToken>
            </trt:GetSnapshotUri>
        """.trimIndent()
        soap(body, "http://www.onvif.org/ver10/media/wsdl/GetSnapshotUri").let { xml ->
            parseXml(xml).descendants("Uri").firstOrNull()?.textContent?.trim()
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun soap(body: String, action: String): String {
        val header = if (username != null && password != null) wsseUsernameToken(username, password) else ""
        val request = Request.Builder()
            .url(xaddr)
            .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
            .addHeader("SOAPAction", "\"$action\"")
            .post(soapEnvelope(body, header).toRequestBody(soapMediaType))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("ONVIF SOAP ${response.code}: ${text.take(300)}")
            if (text.contains("s:Fault", ignoreCase = true)) error("ONVIF Fault: ${text.take(300)}")
            return text
        }
    }
}
