package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Segmentação, fila local e upload para a nuvem do cliente. */
interface RecordingPipeline {
    val uploads: Flow<UploadStatus>

    suspend fun start(session: LiveSession, camera: CameraDevice): Result<Unit>
    suspend fun stop()

    /** Tamanho da fila local pendente. */
    suspend fun pendingCount(): Int

    /** Forçar tentativa de envio da fila. */
    suspend fun flush(): Result<Unit>
}

data class RecordingSegment(
    val id: String,
    val cameraId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val localPath: String,
    val sizeBytes: Long,
    val profile: StreamProfile
)

sealed class UploadStatus {
    data class Uploading(val segment: RecordingSegment, val progress: Float) : UploadStatus()
    data class Uploaded(val segment: RecordingSegment, val remoteKey: String) : UploadStatus()
    data class Failed(val segment: RecordingSegment, val reason: Throwable) : UploadStatus()
    data class Queued(val segment: RecordingSegment) : UploadStatus()
}
