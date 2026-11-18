package com.seeall.agent.core

/** Identificação de uma câmera descoberta ou cadastrada. */
data class CameraDevice(
    val id: String,
    val name: String,
    val model: String? = null,
    val uid: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val transport: Transport,
    val capabilities: Set<Capability> = emptySet()
)

enum class Transport { ONVIF, RTSP, P2P_XM_EYE, P2P_TUYA, P2P_TAPO, MANUAL_RTSP }

enum class Capability {
    LIVE_MAIN, LIVE_SUB, SNAPSHOT, PTZ, AUDIO, TWO_WAY_AUDIO,
    LOCAL_RECORD, CLOUD_RECORD, ONBOARD_AI, MOTION, PIR, LOW_LIGHT
}

/** Um fluxo de vídeo disponível na câmera. */
data class StreamDescriptor(
    val uri: String,
    val profile: StreamProfile,
    val codec: VideoCodec = VideoCodec.H264,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null
)

enum class StreamProfile { MAIN, SUB, SNAPSHOT }
enum class VideoCodec { H264, H265, MJPEG, UNKNOWN }

/** Configuração do agente, controlada pelo cliente. */
data class AgentConfig(
    val agentId: String,
    val cloudTarget: CloudTarget,
    val recording: RecordingConfig = RecordingConfig(),
    val detection: DetectionConfig = DetectionConfig(),
    val network: NetworkConfig = NetworkConfig()
)

data class RecordingConfig(
    val segmentDurationSeconds: Int = 300,
    val retentionDays: Int = 30,
    val enabled: Boolean = true,
    val profile: StreamProfile = StreamProfile.MAIN
)

data class DetectionConfig(
    val enabled: Boolean = true,
    val rules: List<DetectionRule> = emptyList(),
    val modelPath: String? = null,
    val minConfidence: Float = 0.5f
)

data class NetworkConfig(
    val heartbeatSeconds: Int = 60,
    val wifiOnlyUpload: Boolean = false,
    val maxRetryAttempts: Int = 10
)
