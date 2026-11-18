package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Detecção local e aplicação de regras configuráveis. */
interface DetectionEngine {
    val events: Flow<DetectionEvent>

    suspend fun initialize(config: DetectionConfig): Result<Unit>
    suspend fun analyze(frame: VideoFrame, camera: CameraDevice): List<DetectionEvent>
    suspend fun release()
}

data class DetectionEvent(
    val id: String,
    val cameraId: String,
    val type: DetectionType,
    val timestampMs: Long,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
    val ruleId: String? = null,
    val thumbnailPath: String? = null
)

enum class DetectionType {
    PERSON, VEHICLE, FACE, MOTION, PIR,
    LINE_CROSSING, PERIMETER, ABANDONED_OBJECT, REMOVED_OBJECT,
    NO_PARKING, CAMERA_TAMPER, LOW_LIGHT, SOUND
}

data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float)

sealed class DetectionRule {
    abstract val id: String
    abstract val enabled: Boolean

    data class Line(
        override val id: String,
        override val enabled: Boolean = true,
        val start: PointF,
        val end: PointF,
        val direction: Direction = Direction.BOTH,
        val minConfidence: Float = 0.5f,
        val cooldownSeconds: Int = 30
    ) : DetectionRule()

    data class Perimeter(
        override val id: String,
        override val enabled: Boolean = true,
        val polygon: List<PointF>,
        val minConfidence: Float = 0.5f,
        val cooldownSeconds: Int = 30
    ) : DetectionRule()

    data class ObjectRule(
        override val id: String,
        override val enabled: Boolean = true,
        val kind: Kind,
        val zone: List<PointF>? = null,
        val minConfidence: Float = 0.5f,
        val dwellSeconds: Int = 10
    ) : DetectionRule() {
        enum class Kind { ABANDONED, REMOVED, NO_PARKING }
    }
}

data class PointF(val x: Float, val y: Float)
enum class Direction { A_TO_B, B_TO_A, BOTH }
