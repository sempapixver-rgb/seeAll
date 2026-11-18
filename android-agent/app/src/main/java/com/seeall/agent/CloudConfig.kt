package com.seeall.agent

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuração da nuvem do cliente. Digitada pelo usuário no app;
 * nunca enviada ao backend e nunca solicitada no chat.
 */
data class BucketConfig(
    val endpoint: String = "",
    val bucket: String = "",
    val region: String = "us-east-1",
    val accessKey: String = "",
    val secretKey: String = "",
    val prefix: String = "seeall/",
    val enabled: Boolean = false
) {
    val isValid: Boolean
        get() = endpoint.startsWith("http") && bucket.isNotBlank() &&
                accessKey.isNotBlank() && secretKey.isNotBlank()
}

class ConfigStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("seeall_config", Context.MODE_PRIVATE)

    fun load(): BucketConfig = BucketConfig(
        endpoint = sp.getString("endpoint", "") ?: "",
        bucket = sp.getString("bucket", "") ?: "",
        region = sp.getString("region", "us-east-1") ?: "us-east-1",
        accessKey = sp.getString("accessKey", "") ?: "",
        secretKey = sp.getString("secretKey", "") ?: "",
        prefix = sp.getString("prefix", "seeall/") ?: "seeall/",
        enabled = sp.getBoolean("enabled", false)
    )

    fun save(cfg: BucketConfig) {
        sp.edit()
            .putString("endpoint", cfg.endpoint.trim())
            .putString("bucket", cfg.bucket.trim())
            .putString("region", cfg.region.trim().ifEmpty { "us-east-1" })
            .putString("accessKey", cfg.accessKey.trim())
            .putString("secretKey", cfg.secretKey.trim())
            .putString("prefix", cfg.prefix.trim().ifEmpty { "seeall/" })
            .putBoolean("enabled", cfg.enabled)
            .apply()
    }

    fun cameraName(): String = sp.getString("cameraName", "Camera") ?: "Camera"
    fun saveCameraName(name: String) { sp.edit().putString("cameraName", name).apply() }
}
