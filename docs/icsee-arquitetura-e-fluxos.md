# iCSee — arquitetura e fluxos mapeados com REA

Este documento complementa `icsee-analise-inicial.md`. Toda a observação vem de análise estática (JADX/REA); o APK não foi executado. Nenhum código proprietário foi copiado — aqui só descrevemos comportamento e contratos para orientar uma implementação própria no `seeAll`.

## Evidências

- `com.lib.SDKCONST` — evidência `ev_695ba37770d514ba3e323660bb038d06582d97a9281e65bdabc3bebeb73cf61d`
- `com.manager.device.DeviceManager` — 77 métodos, 22 campos, 8 inner classes
- `com.manager.device.media.monitor.MonitorManager` — 28 métodos, 5 campos
- Varredura de strings nos 7 DEX — 527 URLs/URIs, 247 hosts

## Constantes do SDK (`com.lib.SDKCONST`)

43 campos e 88 inner classes. Limites e enums típicos de SDK de câmera:

- `NET_MAX_CHANNUM = 64`, `NET_MAX_USER_NUM = 60`, `NET_MAX_GROUP_NUM = 50`
- `NET_MAX_MAC_LEN = 32`, `NET_MAX_USER_LENGTH = 32`, `NET_NAME_PASSWORD_LEN = 80`
- `SDK_CAPTURE_SIZE_NR = 20`, `WNDS_MAX = 4`
- Enums: `StreamType`, `StreamTypeV2`, `ConnectMode`, `DEVICE_TYPE`, `DevStatus`, `PlayBackAction`, `PlayType`, `PlayState`, `RecordState`, `SDK_RET_CODE`, `UserLoginRet`, `PTZ_ControlType`, `PushMsgTypes`, `SocketStyle`, `NetWorkType`, `VideoType`, `AudioState`, `VoiceIntercomState`, `EBleEncryptType`, `ERPSProtocolMode`

Isso indica um SDK de dispositivo com canais múltiplos, PTZ, áudio bidirecional, estados de conexão e playback com tipos/estados explícitos.

## `DeviceManager` — centro de operações do dispositivo

Métodos observados (seleção):

| Área | Métodos |
|---|---|
| Login/sessão | `loginDev`, `loginDevByLowPower`, `logoutDev`, `setLocalDevPwd`, `getEncryptVersion` |
| Descoberta | `searchLanDevice`, `getLanDevice`, `initDevToRouterByQrCode`, `startDevToRouterByQrCode`, `stopDevToRouterByQrCode`, `unInitDevToRouterByQrCode` |
| Wi-Fi rápido | `startQuickSetWiFi`, `startQuickSetWiFiByDevLogin`, `startQuickSetWiFiSimple`, `stopQuickSetWiFi` |
| Mídia | `createMonitorPlayer`, `createMonitorPlayers`, `createRecordPlayer`, `captureFromDevAndSaveToDev` |
| Informações | `getDeviceModel`, `getDevType`, `getSerialNo`, `getChannelCount`, `getHardWareVersion`, `getSoftWareVersion`, `getBuildTime`, `getDevAbility` |
| Config | `getDevConfigManager`, `getConfigFromDev`, `resetDevConfig`, `modifyDevName`, `modifyDevPwd`, `syncDevTime`, `syncDevTimeZone`, `switchDevNetworkMode` |
| Manutenção | `checkDevUpgrade`, `startDevUpgrade`, `startDevUpgradeByLocalFile`, `rebootDev`, `setTimeOut` |
| Callback | `OnFunSDKResult` |

Padrão: chamadas de gerenciamento assíncronas com resultado entregue em `OnFunSDKResult(Message, MsgContent)`.

## `MonitorManager` — monitoramento ao vivo e intercom

- `startMonitor()`, `startGetRealTimeStream()`, `closeGetRealTimeStream()`
- `saveRealTimeStream(path, ..., fileName, ...)`, `setSaveThumbnailPath`, `setSaveThumbnailFileName`
- Intercom: `startTalkByHalfDuplex`, `startTalkByDoubleDirection`, `stopTalkByDoubleDirection`, `uploadTalk`, `doubleDirectionSound`, `destroyTalk`, `isTalking`, `getTalkManager`
- Callback: `OnFunSDKResult(Message, MsgContent)`, `onStartTalkResult`, `onStopTalkResult`, `onTalkDisconnect`, `onVoiceOperateResult`

Construtor recebe `ViewGroup` + `PlayerAttribute`, ou seja, o player é acoplado a uma view e a atributos de reprodução.

## Backends observados nas strings dos DEX

| Domínio | Papel aparente |
|---|---|
| `ais.jftechws.com`, `aisvr-new.bcloud365.net`, `aisvr-google.bcloud365.net` | servidores de sinalização/AI |
| `jvss.jftechws.com` | mensagens, arquivos de dispositivo (`/device/file/get`, `/v3/message/list/`) |
| `jfdrive.bcloud365.net` | upload/download/exclusão de arquivos, modify user |
| `d.jfapp.net/CSee?shareKey=...` / `transKey=` / `shareInfo=` | links de compartilhamento |
| `pairing.jftechws.com/api/` | pareamento |
| `boss-api.jftechsoft.com`, `boss2.jftechsoft.com`, `quicktracking-api.jftechws.com` | backend/telemetria |
| `api.mossturbo.com`, `api-sec.mossturbo.com`, `adx/tk/da.mossturbo.com` | monetização/ads |
| `app-support.jftech.com/h5/iCSee`, `support-help.jftech.com` | suporte |
| `m.xm030.com/user/act/scanCode.html` | scan de QR legado |
| `integral-mall.jftechws.com`, `api-integral-mall.jftechws.com` | loja de pontos |

`assets/key/sdkkey.txt` e `assets/key/qrkey.txt` contêm valores ofuscados em base64; os valores decodificados **não** foram publicados neste repositório.

## Fluxo funcional reconstruído (alto nível)

1. `WelcomePageActivity` → `LoginPageActivity` → `MainActivity`.
2. Usuário autentica; `AccountManager` / `XMAccountManager` guardam sessão.
3. `AddDeviceActivity` oferece QR/SN, Wi-Fi, Bluetooth, IP e estação base.
4. `DeviceManager.loginDev` + `searchLanDevice` / `initDevToRouterByQrCode` estabelecem o vínculo.
5. `createMonitorPlayer` + `MonitorManager.startMonitor` iniciam o ao vivo; `OnFunSDKResult` entrega eventos.
6. Playback usa `createRecordPlayer` com `PlayBackAction`/`PlayType`/`RecordState`; cloud playback passa por `jfdrive.bcloud365.net`.
7. Alarmes e notificações usam `XMWebSocketManager` e `jvss.jftechws.com`.

## Implicações para o `seeAll`

- Precisamos de uma camada de abstração própria (`DeviceSession`, `LivePlayer`, `PlaybackSource`) sem dependência do SDK XMEye.
- O contrato mínimo a imitar é: descobrir/parear → autenticar dispositivo → selecionar stream (principal/secundária) → start/stop stream → eventos de estado/erro → snapshot/gravação opcional.
- Evitar SDKs de anúncio/telemetria do apk original.
- Não reutilizar chaves, endpoints privados, nomes de pacote, recursos ou código proprietário.
- A implementação deve ser compatível com os termos das lojas: sem vocabulário bloqueado na edição pública.

## Limitações

- Análise estática não prova comportamento em runtime.
- Nomes de métodos ofuscados (`a`, `b`, `c`) são aliases locais do JADX.
- Chamadas dinâmicas/reflexivas podem não aparecer.
- Assinatura criptográfica do APK não foi validada contra uma fonte confiável.
