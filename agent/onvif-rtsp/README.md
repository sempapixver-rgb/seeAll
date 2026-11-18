# seeAll Agent — M2 ONVIF/RTSP

Implementação da camada 1 de conexão: descoberta ONVIF na LAN e reprodução RTSP sobre TCP interleaved.

## Arquivos

```
camera/
  Soap.kt                    — envelope SOAP, WS-Security PasswordDigest, helpers XML
  OnvifDiscovery.kt          — WS-Discovery UDP multicast 239.255.255.250:3702
  OnvifClient.kt             — GetDeviceInformation, GetProfiles, GetStreamUri, GetSnapshotUri
  RtspClient.kt              — OPTIONS/DESCRIBE/SETUP/PLAY + RTP -> NALUs H.264
  OnvifCameraConnection.kt   — CameraConnection via ONVIF + RTSP
  RtspCameraConnection.kt    — CameraConnection por URL RTSP manual
  InMemoryCameraRegistry.kt  — registry/descoberta (temporário, vira Room no M7)
```

## Como testar

```kotlin
val registry = InMemoryCameraRegistry()
val found = registry.discover()
found.onSuccess { list ->
    list.forEach { println("${it.name} @ ${it.host}") }
}

val camera = registry.list().first()
registry.setCredentials(camera.id, "admin", "senha")
val connection = registry.connectionFor(camera)
connection.connect()

val streams = connection.streams().getOrThrow()
val session = connection.openLive(streams.first()).getOrThrow()
session.events.collect { println(it) }
session.frames.collect { frame ->
    println("NALU ${frame.nalu.size} bytes key=${frame.keyFrame}")
}
```

## Limitações conhecidas

- WS-Discovery responde apenas em dispositivos com ONVIF habilitado
- XML parser usa `DocumentBuilderFactory` com features de segurança (sem DTD/entidades externas)
- RTSP assume H.264 payload type 96/97; H.265 e áudio ainda não são decodificados
- Playback ONVIF (Recording Search) fica para o M4
- Registry em memória some ao reiniciar; persistência no M7

## Status

M2 pronto para teste com câmera ONVIF real ou URL RTSP.
