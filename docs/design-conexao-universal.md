# seeAll — conexão universal com câmeras e gravação na nuvem do cliente

## Objetivo

Qualquer câmera IP conecta ao nosso sistema, e as gravações ficam no armazenamento do próprio cliente. Sem depender da nuvem do fabricante para guardar vídeo.

## Princípio central

A câmera barata **não** tem capacidade de falar com a nossa nuvem. Ela só fala:

- protocolo P2P do fabricante (XMEye/EZNAT, Tuya, Tapo, Reolink, Hikvision, etc.), ou
- RTSP/ONVIF (câmeras IP de rede).

Então quem conecta é **o nosso agente**, de dentro da rede do cliente:

```
câmera --(LAN / P2P)--> agente seeAll --(HTTPS)--> nuvem do cliente
```

O agente roda num celular Android velho, Raspberry Pi ou mini PC. Isso resolve:

- NAT/firewall (agente está dentro da LAN)
- câmeras que só fazem P2P (agente é o cliente do protocolo)
- custo (nosso backend não recebe vídeo, só metadados)

## Camadas de conexão (ordem de tentativa)

### 1. ONVIF + RTSP (automático, zero RE)

- WS-Discovery na LAN encontra câmeras compatíveis
- ONVIF `GetStreamUri` devolve a URL RTSP
- Reprodução via player com suporte a RTSP
- Cobre: Hikvision, Dahua, Reolink, Axis, Uniview, câmeras PC/NVR

### 2. P2P proprietário (engenharia reversa, em andamento)

Primeiro alvo: **XMEye/EZNAT** (iCSee, XMEye, V380, 360, many generic).

Evidências já coletadas no `iCSee.apk`:

- `P2P_DNS_SERVER = secu100.net` — DNS de resolução de UID
- `SysGetDevList`, `SysGetDevListEx`, `SysSmsGetDevList`, `SysVmsCloudGetDevList`
- `isP2POnline`, `isP2PV0Online`, `setP2POnline`, `P2P_Mode`, `P2P_V0`
- `CONNECT_TYPE_P2P`, `CONNECT_TYPE_RTS_P2P`, `NET_TYPE_P2P`, `DEV_STATE_P2P`
- `QueryP2PEnable`, `SetP2PDataEncryptEnable`, `P2P_Data_Encrypt_Enable_new`
- `com.lib.sdk.struct.SDK_NatConfig`, `SDK_NatStatusInfo`
- `com.lib.sdk.bean.NatInfoBean`, `NetWorkRemoteDeviceV3`
- `com.xworld.devset.wbs.RelayModeSwitchActivity`
- Nenhuma `.so`: o protocolo está em DEX, reversível via JADX

Fluxo típico a implementar:

1. UID do dispositivo → DNS `secu100.net` → IP do servidor de sinalização
2. Handshake P2P / verificação de NAT (`SDK_NatStatusInfo`)
3. Se P2P falhar, relay via servidor do fabricante
4. Estabelecer canal de mídia (main/sub stream)
5. Receber H.264/H.265 e convertê-lo em segmentos MP4

Próximos alvos depois do XMEye: Tuya/Smart Life, TP-Link Tapo, Reolink P2P.

### 3. RTSP manual (fallback universal)

Se o usuário souber a URL RTSP, ele informa. Funciona com praticamente qualquer câmera IP.

## Gravação na nuvem do cliente

- Agente segmenta o vídeo em MP4 de 1–5 minutos
- Fila local persistente (Room/SQLite) para funcionar offline
- Upload multipart para bucket S3-compatible do cliente (AWS S3, Cloudflare R2, Backblaze B2, MinIO)
- Cliente fornece bucket + credenciais no app
- Lifecycle/reenção configurado no bucket
- Criptografia em repouso no bucket (SSE)
- Nenhum vídeo passa pelo nosso backend

## Backend (mínimo, stateless)

Responsabilidades:

- Cadastro de usuários/agente
- Registro de dispositivos descobertos (UID, modelo, capacidades)
- Emissão de signed URLs para upload
- Chaves de criptografia / segredos do cliente (em cofre, nunca em texto claro)
- Push (FCM) de eventos de movimento / status
- Endpoint `/version` para auto-update do APK

Stack sugerido: API leve (Node/Go) + Postgres gerenciado + Cloudflare. Custo baixo porque não há tráfego de vídeo.

## "Manter online" e APK nos celulares dos clientes

- Agente reporta heartbeat a cada 30–60 s
- Se cair, reconecta com backoff exponencial
- Fila de vídeo local garante que nada se perca
- Auto-update embutido: checa `/version`, valida SHA-256 do APK assinado, instala via `REQUEST_INSTALL_PACKAGES`
- Sideload obrigatório (sem Google Play)
- Monitoramento: healthcheck do backend e alerta se agente ficar offline

## Roadmap

1. Desenhar contratos (`DeviceSession`, `LivePlayer`, `RecordingPipeline`) — nuvem, agora
2. Implementar camada 1 (ONVIF/RTSP) — base limpa
3. Reversar e implementar XMEye P2P a partir do APK
4. Implementar upload S3 + fila local
5. Backend minimalista + auto-update
6. Adicionar protocolos conforme demanda

## Limitações e riscos

- Protocolos P2P são proprietários e podem mudar sem aviso
- Cada fabricante é um protocolo diferente — "todas" é um esforço contínuo
- Análise estática não prova runtime; será necessário teste com câmera real
- Não reutilizar código, chaves, endpoints ou recursos proprietários
- O DNS `secu100.net` e nomes de função são fatos observados no APK, não instruções a seguir
