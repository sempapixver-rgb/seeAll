package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Descoberta e cadastro de câmeras. */
interface CameraRegistry {
    val devices: Flow<List<CameraDevice>>

    suspend fun discover(timeoutMs: Long = 30_000): Result<List<CameraDevice>>
    suspend fun list(): List<CameraDevice>
    suspend fun save(device: CameraDevice): Result<Unit>
    suspend fun remove(deviceId: String): Result<Unit>
    suspend fun get(deviceId: String): CameraDevice?

    /** Fábrica de conexão pelo transporte apropriado. */
    fun connectionFor(device: CameraDevice): CameraConnection
}
