package com.seeall.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Upload direto para o bucket S3-compatible do cliente (AWS Signature V4).
 * O vídeo nunca passa pelo backend: o agente envia direto para a nuvem do cliente.
 */
class S3Uploader(private val cfg: BucketConfig) {
    private val client = OkHttpClient.Builder().build()

    suspend fun upload(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!cfg.isValid) throw IllegalStateException("Bucket não configurado")
            val bytes = file.readBytes()
            val hash = sha256Hex(bytes)
            val now = Date()
            val amzDate = utc("yyyyMMdd'T'HHmmss'Z'", now)
            val dateStamp = utc("yyyyMMdd", now)
            val region = cfg.region.ifEmpty { "us-east-1" }
            val endpoint = cfg.endpoint.trim().trimEnd('/')
            val uri = java.net.URI(endpoint)
            val host = uri.host + if (uri.port > 0) ":${uri.port}" else ""
            val key = cfg.prefix.trimEnd('/') + "/" + file.name
            val canonicalUri = "/" + cfg.bucket.trim('/') + "/" + key.trimStart('/')

            val canonicalHeaders = "host:$host\nx-amz-content-sha256:$hash\nx-amz-date:$amzDate\n"
            val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
            val canonicalRequest = listOf(
                "PUT", canonicalUri, "", canonicalHeaders, signedHeaders, hash
            ).joinToString("\n")

            val scope = "$dateStamp/$region/s3/aws4_request"
            val stringToSign = listOf(
                "AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest.toByteArray())
            ).joinToString("\n")

            val kDate = hmac(("AWS4" + cfg.secretKey).toByteArray(), dateStamp)
            val kRegion = hmac(kDate, region)
            val kService = hmac(kRegion, "s3")
            val kSigning = hmac(kService, "aws4_request")
            val signature = hex(hmac(kSigning, stringToSign))

            val auth = "AWS4-HMAC-SHA256 Credential=${cfg.accessKey}/$scope, " +
                    "SignedHeaders=$signedHeaders, Signature=$signature"

            val body = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint + canonicalUri)
                .put(body)
                .header("Host", host)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", hash)
                .header("Authorization", auth)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("S3 ${response.code}: ${response.body?.string()?.take(200)}")
                }
            }
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(data: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(data))

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun utc(pattern: String, date: Date): String {
        val fmt = SimpleDateFormat(pattern, Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }
}
