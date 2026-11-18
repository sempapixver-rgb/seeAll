package com.seeall.agent.core

import kotlinx.coroutines.flow.Flow

/** Orquestrador do agente: connect -> record -> detect -> upload. */
interface AgentController {
    val status: Flow<AgentStatus>

    suspend fun start(): Result<Unit>
    suspend fun stop()

    suspend fun addCamera(request: AddCameraRequest): Result<CameraDevice>
    suspend fun startRecording(cameraId: String): Result<Unit>
    suspend fun stopRecording(cameraId: String): Result<Unit>

    suspend fun updateConfig(config: AgentConfig): Result<Unit>
    suspend fun currentConfig(): AgentConfig
}

data class AddCameraRequest(
    val name: String,
    val transport: Transport,
    val address: String? = null,
    val uid: String? = null,
    val username: String? = null,
    val password: String? = null
)

sealed class AgentStatus {
    object Idle : AgentStatus()
    object Starting : AgentStatus()
    data class Running(val activeCameras: Int, val recording: Int) : AgentStatus()
    data class Error(val throwable: Throwable) : AgentStatus()
}
