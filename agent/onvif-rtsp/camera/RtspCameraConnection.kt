package com.seeall.agent.camera

import com.seeall.agent.core.CameraConnection
import com.seeall.agent.core.CameraDevice
import com.seeall.agent.core.ConnectionState
import com.seeall.agent.core.LiveSession
import com.seeall.agent.core.SessionEvent
import com.seeall.agent.core.StreamDescriptor
import com.seeall.agent.core.StreamProfile
import com.seeall.agent.core.VideoCodec
import com.seeall.agent.core.VideoFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Conexão direta por URL RTSP informada manualmente. */
class RtspCameraConnection(
    override val device: CameraDevice,
    private val uri: String,
    private val username: String? = null,
    private val password: String? = null
) : CameraConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableSharedFlow<ConnectionState>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val state: Flow<ConnectionState> = _state.asSharedFlow()

    private val descriptor = StreamDescriptor(uri = uri, profile = StreamProfile.MAIN, codec = VideoCodec.H264)

    override suspend fun connect(): Result<Unit> = runCatching {
        _state.emit(ConnectionState.CONNECTING)
        _state.emit(ConnectionState.CONNECTED)
    }

    override suspend fun disconnect() {
        _state.emit(ConnectionState.DISCONNECTED)
        scope.cancel()
    }

    override suspend fun streams(): Result<List<StreamDescriptor>> = Result.success(listOf(descriptor))
    override suspend fun snapshot(): Result<ByteArray> = Result.failure(UnsupportedOperationException("RTSP puro não fornece snapshot HTTP"))

    override suspend fun openLive(descriptor: StreamDescriptor): Result<LiveSession> = runCatching { Session(descriptor) }
    override suspend fun openRecording(descriptor: StreamDescriptor, from: Long, to: Long): Result<LiveSession> =
        Result.failure(UnsupportedOperationException("Playback requer servidor ONVIF/NVR"))

    inner class Session(private val descriptor: StreamDescriptor) : LiveSession {
        private val _frames = MutableSharedFlow<VideoFrame>(64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val _events = MutableSharedFlow<SessionEvent>(8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        override val frames: Flow<VideoFrame> = _frames.asSharedFlow()
        override val events: Flow<SessionEvent> = _events.asSharedFlow()
        private var rtsp: RtspClient? = null
        private var running = false

        override suspend fun start() {
            if (running) return
            running = true
            val client = RtspClient(descriptor.uri, username, password)
            rtsp = client
            client.connectAndPlay().onFailure { error ->
                _events.emit(SessionEvent.Error(error))
                running = false
                return
            }
            _events.emit(SessionEvent.Connected(descriptor))
            scope.launch {
                client.frames.collect { frame ->
                    _frames.tryEmit(
                        VideoFrame(frame.timestampMs, frame.nalu, frame.keyFrame, descriptor.width ?: 0, descriptor.height ?: 0)
                    )
                }
            }
        }

        override suspend fun stop() {
            running = false
            rtsp?.close()
            rtsp = null
            _events.emit(SessionEvent.Disconnected)
        }

        override fun isRunning(): Boolean = running
    }
}
