# seeAll — plano de execução

## Visão

Um agente que roda na rede do cliente, conecta qualquer câmera, grava na nuvem do próprio cliente e adiciona IA de segurança local. Nosso backend é mínimo e stateless — só coordena, não guarda vídeo.

```
[câmera] --LAN/P2P--> [agente seeAll] --HTTPS--> [nuvem do cliente]
                            |
                            +--eventos/metadados--> [backend seeAll]
```

## Documentos de base

| Doc | Conteúdo |
|---|---|
| `icsee-analise-inicial.md` | Identificação, assinatura, permissões, inventário do APK |
| `icsee-arquitetura-e-fluxos.md` | SDK, DeviceManager, MonitorManager, backends |
| `design-conexao-universal.md` | Arquitetura de conexão e gravação |
| `ia-seguranca-icsee.md` | IA, regras, alarmes e busca do iCSee |

## Módulos

### M1 — Contratos e domínio (nuvem, agora)

Definir interfaces e modelos sem implementação:

- `CameraConnection` — descobrir, conectar, obter streams, eventos
- `LiveSession` — start/stop, main/sub, snapshots
- `RecordingPipeline` — segmentar MP4, fila local, upload S3
- `DetectionEngine` — detectar, aplicar regras, gerar eventos
- `EventStore` — indexar e buscar eventos
- `CloudTarget` — bucket do cliente, credenciais, retenção
- `AgentConfig` — config do agente

### M2 — Camada ONVIF/RTSP (base limpa)

- WS-Discovery na LAN
- ONVIF `GetCapabilities`, `GetStreamUri`, `GetDeviceInformation`
- Player RTSP (main/sub) com reconexão
- Snapshot via ONVIF `GetSnapshotUri`
- Detecção automática de câmeras na rede

### M3 — P2P XMEye/EZNAT (engenharia reversa)

- Resolver UID via `secu100.net`
- Handshake e negociação NAT
- Canal de mídia proprietário
- Conversão para MP4
- Base: `com.lib.sdk.*`, `SysGetDevList`, `isP2POnline`, `SDK_NatConfig`

### M4 — Gravação na nuvem do cliente

- Segmentação MP4 (1–5 min) com ffmpeg ou MediaCodec
- Fila SQLite local com retry/backoff
- Upload multipart S3 (R2/B2/S3/MinIO)
- Lifecycle/reenção configurável pelo cliente
- Criptografia em repouso

### M5 — IA de segurança local

- Detecção de pessoa/movimento com TFLite no agente
- Regras: linha, perímetro, objeto abandonado/removido, estacionamento
- Zonas configuráveis
- Evento rico: tipo, timestamp, thumbnail, confiança
- Push via FCM para o celular do cliente

### M6 — Busca inteligente

- Indexar eventos no storage do cliente
- Busca por tipo, período e descrição
- Reutilizar padrões de `PlayBackFragment$initAiSearchView`

### M7 — Backend e distribuição

- API stateless: devices, agentes, auth, signed URLs, `/version`
- Postgres gerenciado + Cloudflare
- Heartbeat e monitoramento
- Auto-update do APK com validação SHA-256
- Sideload (sem Google Play)

## Ordem sugerida

1. **M1** — contratos (define tudo; rápido)
2. **M2** — ONVIF/RTSP (entrega valor imediato com câmeras padrão)
3. **M4** — gravação na nuvem (o que o cliente mais quer)
4. **M5** — IA local (diferencial)
5. **M6** — busca
6. **M3** — P2P (trabalho pesado de RE, depois que a base estiver sólida)
7. **M7** — backend e auto-update (pode ser paralelo)

## Princípios

- Nenhum código, chave, endpoint ou recurso proprietário reutilizado
- Documentação baseada em evidência observada, não em suposição
- Cliente é dono do storage e das credenciais
- Sem termos bloqueados nas versões públicas
- Backend nunca recebe vídeo

## Critérios de sucesso

- Câmera ONVIF descoberta automaticamente em < 30 s
- Vídeo segmentado e enviado ao bucket com fila offline
- Evento de detecção com thumbnail em < 5 s
- Agente sobrevive a queda de internet sem perder gravação
- APK atualiza sozinho com validade criptográfica
