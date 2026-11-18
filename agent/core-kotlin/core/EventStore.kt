package com.seeall.agent.core

/** Armazenamento e busca de eventos de detecção. */
interface EventStore {
    suspend fun save(event: DetectionEvent): Result<Unit>
    suspend fun search(query: EventQuery): Result<List<DetectionEvent>>
    suspend fun countByType(cameraId: String, from: Long, to: Long): Map<DetectionType, Int>
    suspend fun purgeBefore(timestampMs: Long): Result<Int>
}

data class EventQuery(
    val cameraId: String? = null,
    val types: Set<DetectionType> = emptySet(),
    val from: Long? = null,
    val to: Long? = null,
    val minConfidence: Float = 0f,
    val limit: Int = 100,
    val cursor: String? = null
)
