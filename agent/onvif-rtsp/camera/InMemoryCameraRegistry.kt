package com.seeall.agent.camera

import com.seeall.agent.core.CameraConnection
import com.seeall.agent.core.CameraDevice
import com.seeall.agent.core.CameraRegistry
import com.seeall.agent.core.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Registry simples em memória, usado pelo M2 antes do persistence do M7. */
class InMemoryCameraRegistry : CameraRegistry {

    private val store = ConcurrentHashMap<String, CameraDevice>()
    private val credentials = ConcurrentHashMap<String, Pair<String?, String?>>()
    private val _devicesFlow = MutableStateFlow<List<CameraDevice>>(emptyList())
    override val devices: Flow<List<CameraDevice>> = _devicesFlow.asStateFlow()

    private fun publish() {
        _devicesFlow.value = store.values.sortedBy { it.name }
    }

    override suspend fun discover(timeoutMs: Long): Result<List<CameraDevice>> = runCatching {
        OnvifDiscovery(timeoutMs.toInt()).discover().map { found ->
            CameraDevice(
                id = found.xaddr.hashCode().toString(),
                name = found.model ?: found.hardware ?: found.address,
                model = found.model,
                uid = found.hardware,
                host = found.address,
                transport = Transport.ONVIF
            )
        }.also { list ->
            list.forEach { store[it.id] = it }
            publish()
        }
    }

    override suspend fun list(): List<CameraDevice> = store.values.toList()

    override suspend fun save(device: CameraDevice): Result<Unit> = runCatching {
        store[device.id] = device
        publish()
    }

    override suspend fun remove(deviceId: String): Result<Unit> = runCatching {
        store.remove(deviceId)
        credentials.remove(deviceId)
        publish()
    }

    override suspend fun get(deviceId: String): CameraDevice? = store[deviceId]

    fun setCredentials(deviceId: String, username: String?, password: String?) {
        credentials[deviceId] = username to password
    }

    override fun connectionFor(device: CameraDevice): CameraConnection {
        val (user, pass) = credentials[device.id] ?: (null to null)
        return when (device.transport) {
            Transport.ONVIF -> OnvifCameraConnection(
                device = device,
                xaddr = device.host?.let { "http://$it/onvif/device_service" } ?: error("ONVIF sem host"),
                username = user,
                password = pass
            )
            Transport.RTSP, Transport.MANUAL_RTSP -> RtspCameraConnection(
                device = device,
                uri = device.host ?: error("RTSP sem URI"),
                username = user,
                password = pass
            )
            else -> throw UnsupportedOperationException("Transporte ${device.transport} ainda não implementado")
        }
    }
}
