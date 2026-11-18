package com.seeall.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Fila local e histórico de eventos — funciona offline, envia quando o bucket volta. */
data class CameraEvent(
    val id: String,
    val camera: String,
    val type: String,
    val time: String,
    val path: String? = null,
    val uploaded: Boolean = false
)

class EventRepo(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "seeall").apply { mkdirs() }
    private val file = File(dir, "events.json")
    private val queue = File(dir, "queue").apply { mkdirs() }

    suspend fun all(): List<CameraEvent> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CameraEvent(
                    id = o.optString("id"),
                    camera = o.optString("camera"),
                    type = o.optString("type"),
                    time = o.optString("time"),
                    path = o.optString("path").takeIf { it.isNotBlank() },
                    uploaded = o.optBoolean("uploaded")
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun add(event: CameraEvent) = withContext(Dispatchers.IO) {
        val list = all().toMutableList()
        list.add(0, event)
        save(list)
    }

    suspend fun markUploaded(id: String) = withContext(Dispatchers.IO) {
        val list = all().map { if (it.id == id) it.copy(uploaded = true) else it }
        save(list)
    }

    suspend fun pending(): List<CameraEvent> = all().filter { !it.uploaded && it.path != null }

    suspend fun pendingCount(): Int = pending().size

    /** Tenta enviar todos os eventos pendentes para o bucket do cliente. */
    suspend fun flush(cfg: BucketConfig): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (!cfg.enabled || !cfg.isValid) return@withContext 0 to "Bucket não configurado"
        val uploader = S3Uploader(cfg)
        var ok = 0
        var lastError = ""
        for (event in pending()) {
            val path = event.path ?: continue
            val local = File(path)
            if (!local.exists()) continue
            val result = uploader.upload(local)
            if (result.isSuccess) {
                markUploaded(event.id)
                local.delete()
                ok++
            } else {
                lastError = result.exceptionOrNull()?.message ?: "erro desconhecido"
            }
        }
        ok to lastError
    }

    fun queueFile(): File = File(queue, "seg-${System.currentTimeMillis()}.mp4")

    private fun save(list: List<CameraEvent>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id); o.put("camera", e.camera); o.put("type", e.type)
            o.put("time", e.time); o.put("path", e.path ?: ""); o.put("uploaded", e.uploaded)
            arr.put(o)
        }
        file.writeText(arr.toString())
    }
}
