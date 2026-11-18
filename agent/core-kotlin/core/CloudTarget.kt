package com.seeall.agent.core

/** Destino de storage pertencente ao cliente (S3-compatible). */
data class CloudTarget(
    val provider: CloudProvider,
    val endpoint: String,
    val bucket: String,
    val region: String? = null,
    val prefix: String = "seeall/",
    val credentials: CloudCredentials,
    val retentionDays: Int = 30,
    val serverSideEncryption: Boolean = true
)

enum class CloudProvider { AWS_S3, CLOUDFLARE_R2, BACKBLAZE_B2, MINIO, CUSTOM_S3 }

sealed class CloudCredentials {
    data class AccessKey(val accessKeyId: String, val secretAccessKey: String) : CloudCredentials()
    data class SessionToken(val accessKeyId: String, val secretAccessKey: String, val token: String) : CloudCredentials()
    object PresignedOnly : CloudCredentials()
}
