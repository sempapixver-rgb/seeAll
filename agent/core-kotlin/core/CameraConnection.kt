package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Contrato de conexão com uma câmera, independente do protocolo. */
interface CameraConnection {
    val device: CameraDevice
    val state: Flow<ConnectionState>

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()

    suspend fun streams(): Result<List<StreamDescriptor>>
    suspend fun snapshot(): Result<ByteArray>

    suspend fun openLive(descriptor: StreamDescriptor): Result<LiveSession>
    suspend fun openRecording(descriptor: StreamDescriptor, from: Long, to: Long): Result<LiveSession>

    suspend fun ptz(command: PtzCommand): Result<Unit> = Result.failure(UnsupportedOperationException())
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }

enum class PtzCommand { UP, DOWN, LEFT, RIGHT, ZOOM_IN, ZOOM_OUT, STOP }
