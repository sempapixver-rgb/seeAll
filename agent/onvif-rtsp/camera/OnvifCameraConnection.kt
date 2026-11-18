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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** CameraConnection baseado em ONVIF + RTSP. */
class OnvifCameraConnection(
    override val device: CameraDevice,
    private val xaddr: String,
    private val username: String? = null,
    private val password: String? = null
) : CameraConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableSharedFlow<ConnectionState>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val state: Flow<ConnectionState> = _state.asSharedFlow()

    private lateinit var client: OnvifClient
    private var profiles: List<OnvifClient.Profile> = emptyList()
    private var streams: List<StreamDescriptor> = emptyList()

    override suspend fun connect(): Result<Unit> = runCatching {
        _state.emit(ConnectionState.CONNECTING)
        client = OnvifClient(xaddr, username, password)
        profiles = client.getProfiles()

        streams = profiles.mapNotNull { profile ->
            val uri = runCatching { client.getStreamUri(profile.token).uri }.getOrNull() ?: return@mapNotNull null
            val profileKind = when {
                profile.name?.contains("sub", true) == true -> StreamProfile.SUB
                profile.name?.contains("main", true) == true -> StreamProfile.MAIN
                else -> if (streams.isEmpty()) StreamProfile.MAIN else StreamProfile.SUB
            }
            StreamDescriptor(
                uri = uri,
                profile = profileKind,
                codec = if (profile.encoding?.contains("H265", true) == true) VideoCodec.H265 else VideoCodec.H264
            )
        }
        check(streams.isNotEmpty()) { "Nenhum stream RTSP obtido via ONVIF" }
        _state.emit(ConnectionState.CONNECTED)
    }.onFailure {
        _state.emit(ConnectionState.ERROR)
    }

    override suspend fun disconnect() {
        _state.emit(ConnectionState.DISCONNECTED)
        scope.cancel()
    }

    override suspend fun streams(): Result<List<StreamDescriptor>> =
        if (::client.isInitialized) Result.success(streams)
        else Result.failure(IllegalStateException("Conexão não inicializada"))

    override suspend fun snapshot(): Result<ByteArray> = runCatching {
        val token = profiles.firstOrNull()?.token ?: error("Sem profile ONVIF")
        val url = client.getSnapshotUri(token) ?: error("Sem SnapshotUri")
        val request = okhttp3.Request.Builder().url(url).build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Snapshot HTTP ${response.code}" }
            response.body?.bytes() ?: error("Snapshot vazio")
        }
    }

    override suspend fun openLive(descriptor: StreamDescriptor): Result<LiveSession> = runCatching {
        RtspLiveSession(descriptor)
    }

    override suspend fun openRecording(
        descriptor: StreamDescriptor,
        from: Long,
        to: Long
    ): Result<LiveSession> = Result.failure(UnsupportedOperationException("Use ONVIF Recording Search / playback separado"))

    inner class RtspLiveSession(private val descriptor: StreamDescriptor) : LiveSession {
        private val _frames = MutableSharedFlow<VideoFrame>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        private val _events = MutableSharedFlow<SessionEvent>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val frames: Flow<VideoFrame> = _frames.asSharedFlow()
        override val events: Flow<SessionEvent> = _events.asSharedFlow()

        private var clientRtsp: RtspClient? = null
        private var running = false

        override suspend fun start() {
            if (running) return
            running = true
            val rtsp = RtspClient(descriptor.uri, username, password)
            clientRtsp = rtsp
            _events.emit(SessionEvent.Connected(descriptor))

            rtsp.connectAndPlay().onFailure { error ->
                _events.emit(SessionEvent.Error(error))
                running = false
                return
            }

            scope.launch {
                rtsp.frames.collect { frame ->
                    _frames.tryEmit(
                        VideoFrame(
                            timestampMs = frame.timestampMs,
                            data = frame.nalu,
                            keyFrame = frame.keyFrame,
                            width = descriptor.width ?: 0,
                            height = descriptor.height ?: 0
                        )
                    )
                }
            }
        }

        override suspend fun stop() {
            running = false
            clientRtsp?.close()
            clientRtsp = null
            _events.emit(SessionEvent.Disconnected)
        }

        override fun isRunning(): Boolean = running
    }
}
