# iCSee — camada de IA / segurança inteligente

Análise estática complementar (JADX/REA) sobre os recursos de IA do `iCSee.apk`. O APK não foi executado. Observações vêm de classes, recursos e strings do DEX.

## Evidência de que é um recurso premium

Recursos e strings encontrados:

- `home_list_ai_expiry.png`, `home_list_ai_expiry`, `home_list_ai_expire`, `home_list_ai_no`
- `home_list_cloud_expire`, `cloud_state_expire`
- `aimarket_background.xml`, `layout_monitor_ai_market`, `item_monitor_ai_market_more`
- `monitor_click_ai_market_time`
- `show_cloud_event_buy`, `page_cloud_play_back_buy_cloud`
- `vip_state`, `color_text_vip_title`, `vipAwardCoins`
- `activity_dev_ai_alarm_setting`, `activity_dev_intelligent_alert`

Indica que a IA é vendida como serviço: há "mercado" de IA (`AIMarket`), indicadores de expiração na lista de dispositivos e telas de compra de cloud playback.

## Regras de análise inteligente (`com.lib.sdk.bean.smartanalyze`)

28 classes de regras/eventos:

| Classe | Função aparente |
|---|---|
| `Tripwire`, `Tripwirerule` | linha de cruzamento / cerca virtual |
| `Perimeterrule`, `Boundary`, `Line`, `Points`, `Startpt`, `Endpt` | área/perímetro e geometria |
| `Abandumrule` | objeto abandonado |
| `Stolenrule` | objeto removido/furtado |
| `Noparkingrule` | estacionamento proibido |
| `Oscrule`, `Oscpara`, `Oscrg`, `Spclrgs`, `Subrga/b/c` | análise de oscilação/cena |
| `Cpcrule`, `Pearule` | regras adicionais de cena |
| `DetectAnalyze`, `Eventhandler`, `Ruleconfig`, `Limit`, `Limitpara` | motor de regras e limites |

## Detecção de pessoa e AOV (`com.xworld.devset.idr.*`)

- `HumanDetectionActivity`, `HumanDetectionContract`
- `llHumanAndCarDetect`, `lis_human_detection_*` (switch, area, line, perimeter, rule, track, alarm)
- `AOVIntelligentAlarmActivity`, `AOVIntelligentDetectActivity`
- `AOVWorkingMode`, `AovPIRSetting`, `AovWorkModeBean`, `DevAovPowerModeParam`
- `DetectionDurationSetActivity`, `IntervalManagerActivity`, `BatteryWorkModeActivity`
- `intelligent_vigilance_set`, `intelligent_model_setting`, `Intelligent duration`

Tipos de alarme observados em strings `libfunsdk_alarm_type_*`:

`human_detect`, `face_detect`, `face_recognition`, `motion_detect`, `pir`, `trespassing`, `video_blind`, `video_loss`, `stay_away`, `interval_wake`, `remote_call`, `remote_snap`, `door_bell`, `door_open/close`, `card_open`, `finger_open`, `low_battery`, `storage_low_space`, `storage_not_exist`, `force_dismantle`

## Busca inteligente

- `assets/img/ai_search.json`, `shape_ai_search.xml`, `bg_ai_search_data_match.xml`
- `ai_icon_search`, `ai_icon_robot`, `ai_icon_describe`, `default_ai_img_search`
- `PlayBackFragment$initAiSearchView$1`, `playback_click_ai_search`, `playback_show_ai_search`
- `dialog_feedback_ai_alarm`

Provavelmente busca de eventos por descrição/tipo no playback, possivelmente com backend de IA.

## Modelos locais e ML Kit

- `assets/mlkit_odt_localizer/localizer_with_validation.tflite`
- `assets/mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite`
- `assets/mlkit_barcode_models/oned_auto_regressor_mobile.tflite`
- `assets/mlkit_barcode_models/oned_feature_extractor_mobile.tflite`
- `object-detection-custom.properties`, `object-detection-common.properties`
- Pacotes `com.google.android.gms.internal.mlkit_vision_object_detection_custom.*`

Isso é ML Kit (detecção de objetos e QR/barcode) usando modelos locais empacotados. A detecção de pessoas/veículos pode ser feita no dispositivo, enquanto regras avançadas e busca dependem do serviço premium.

## O que isso significa para o seeAll

1. **IA local primeiro**: detecção de pessoa/movimento no agente, usando modelo leve (TFLite/ML Kit), sem custo de nuvem.
2. **Regras configuráveis pelo cliente**: linha, perímetro, objeto abandonado/removido, estacionamento, duração, zonas.
3. **Eventos ricos**: tipo de alarme, imagem/thumbnail, timestamp, score de confiança — enviados ao backend e/ou nuvem do cliente.
4. **Busca inteligente**: indexar eventos (tipo, hora, descrição) no storage do cliente.
5. **Plano premium opcional**: se houver cobrança, deve ser transparente — o cliente paga apenas storage/processamento extra; nunca vendemos como "serviço de vigilância" sem base legal.

## Limitações

- Não sabemos se a IA roda na câmera, no app ou no servidor — evidências apontam para os três casos em recursos diferentes.
- A precisão e os limiares não podem ser avaliados sem execução e teste com câmera real.
- Modelos `.tflite` e nomes de recursos são fatos observados; não foram reutilizados nem copiados.
