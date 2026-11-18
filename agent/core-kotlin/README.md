# seeAll Agent — M1 contratos

Contratos de domínio (Kotlin) para o agente seeAll. Esta é a camada de abstração que desacopla protocolos de câmera, gravação, IA e storage.

## Estrutura

```
core/
  Models.kt            — CameraDevice, StreamDescriptor, configuração
  CameraConnection.kt  — connect/disconnect, streams, snapshot
  LiveSession.kt       — frames e eventos de uma sessão
  CameraRegistry.kt    — descoberta e cadastro
  RecordingPipeline.kt — segmentação + fila + upload
  CloudTarget.kt       — bucket do cliente (S3-compatible)
  DetectionEngine.kt   — detecção local + regras
  EventStore.kt        — eventos + busca
  AgentController.kt   — orquestração
```

## Princípios

- Transporte é um detalhe: `ONVIF`, `RTSP`, `P2P_XM_EYE`, etc. implementam a mesma interface.
- Cliente é dono do `CloudTarget` e das credenciais.
- Eventos e vídeo são separados: backend nunca recebe vídeo.
- Nenhum contrato depende de bibliotecas proprietárias.

## Próximo módulo

M2 — implementação ONVIF/RTSP.
