package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Sessão de vídeo ao vivo ou playback. */
interface LiveSession {
    val frames: Flow<VideoFrame>
    val events: Flow<SessionEvent>

    suspend fun start()
    suspend fun stop()

    fun isRunning(): Boolean
}

data class VideoFrame(
    val timestampMs: Long,
    val data: ByteArray,
    val keyFrame: Boolean,
    val width: Int,
    val height: Int
)

sealed class SessionEvent {
    data class Connected(val descriptor: StreamDescriptor) : SessionEvent()
    data class Error(val throwable: Throwable) : SessionEvent()
    object Disconnected : SessionEvent()
    data class Reconnecting(val attempt: Int) : SessionEvent()
}
