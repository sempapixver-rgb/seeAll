# iCSee — análise inicial com REA

## Alvo

- Arquivo: `C:\Users\GOOLD BEER\Desktop\iCSee.apk`
- SHA-256: `4ff77acb3b92b096eb844eea5a4edb4286385341ecd4af9061c99bc8f9cfe7bf`
- Pacote: `com.xm.csee`
- Versão: `7.9.6_G` (`versionCode` 79633)
- minSdk 24 / targetSdk 37
- 41.038 classes / 4.324 recursos
- Assinatura: v2 + v3, certificado `63e81de1f66a2fc7e9a9f4efdf75519954c3a0c7b71beedb9fafd1ef03866c3c`, self-consistent. Source stamp presente. A autenticidade criptográfica não foi verificada.

## O que o app faz

App de câmeras IP/XMEye: login em conta, adicionar dispositivo por QR/SN/Wi-Fi/Bluetooth/IP, monitorar ao vivo, playback local/na nuvem, alarmes, compartilhamento e configurações do aparelho.

## Componentes de entrada

- Launcher: `com.xworld.activity.welcome.view.WelcomePageActivity`
- Login: `com.xworld.activity.account.LoginPageActivity`
- Principal: `com.xworld.MainActivity`
- Adicionar dispositivo: `com.xworld.activity.adddevice.*`
- Live: `com.xworld.activity.monitor.view.MonitorActivity`, `WbsMonitorActivity`, `com.xm.activity.device.monitor.view.XMMonitorActivity`
- Playback: `PlayBackActivity`, `CloudPlayBackActivity`
- QR: `GoogleScanQRCodeActivity`, `QRConfigResultManager`

## Permissões relevantes

`INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, Wi-Fi, Bluetooth, armazenamento, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `AD_ID`.

## Arquitetura observada

- `com.lib.sdk` / `com.lib.SDKCONST`: constantes e structs do SDK da câmera.
- `com.manager.device.DeviceManager`, `com.manager.account.*`, `com.manager.device.media.*`: camada de gerenciamento do app.
- `com.secmtp.sdk.*`: mídia/P2P e anúncios.
- `com.manager.websocket.XMWebSocketManager`: sinalização/push.
- Alibaba OSS: armazenamento de mídia na nuvem.
- Room: banco local.
- ZXing / ML Kit: leitura de QR.
- Vários SDKs de anúncio (AppLovin/AnyThink, Bigo, Vungle, Yandex, Pangle, Facebook, Firebase).

## Inventário do pacote

- 7 arquivos DEX (`classes.dex` a `classes7.dex`).
- **Nenhuma biblioteca nativa `.so`** no APK — a lógica de mídia/P2P parece estar inteira em DEX.
- 4.058 arquivos em `res/`, 90 em `assets/`.
- Arquivos relevantes em `assets/`: `key/sdkkey.txt`, `key/qrkey.txt`, `language/*.txt`, `country/*.txt`, `network_debug_*.json`, `supplierconfig.json`, modelos TFLite do ML Kit para leitura de código de barras/QR.
- `supplierconfig.json` traz apenas IDs de lojas chinesas (vivo, xiaomi, huawei, oppo).

## Limitações até agora

- O apktool não está disponível no PATH, então as string resources ainda não foram decodificadas.
- O JADX do REA estourou o heap padrão de 1 GB; a análise está sendo feita via CLI com `REA_JADX_HEAP_MIB=4096`.

## Próximos passos

- Inspecionar `DeviceManager`, `SDKCONST` e `MonitorManager` (em execução).
- Mapear os fluxos: login → adicionar dispositivo → iniciar live → playback.
- Documentar contratos e comportamento para implementar a nossa versão em `seeAll`, sem copiar código proprietário.
