# seeAll — especificação da interface (reconstrução a partir do iCSee)

**Status:** proposta para validação. Nenhuma alteração de APK/publicação foi feita.
**Evidências:** 137 capturas ADB, 83 telas únicas, APK iCSee `7.9.6_G` (SHA-256 `4ff77acb3b92b096eb844eea5a4edb4286385341ecd4af9061c99bc8f9cfe7bf`).
**Pacote de evidências na nuvem:** `sempapixver-rgb/seeAll`, release `icsee-evidencias-20261118`.
**Confirmação REA/JADX:** 41.038 classes, 4.324 recursos, 7 DEX; `evidence_id` principal `ev_474440bd29a50ac231fb74ef93e6dbfba89e59cd2d7c0211e419eee9e2d3994a`.

---

## 1. O que o iCSee realmente faz (observado, não inventado)

### 1.1 Estrutura de navegação
- Activity principal: `com.xworld.activity.monitor.view.MonitorActivity` — **801 métodos, 350 campos, 110 classes internas**.
- Abas superiores no monitor: **Tempo real** (`tv_monitor`) · **Gravação** (`tv_playback`) · **Mensagem** (`tv_alarm_msg`).
- Barra inferior de funções (`tab_tv_bottom`): **AI · PTZ · Alarme sonoro e luminoso · Rastreamento de movimento · Favoritos · Controle de navegação · Inversão PTZ · Vinculação**.
- Abas globais do app: **Dispositivo · Mensagem · Serviço · Eu**.
- Métodos confirmados no `MonitorActivity`: `changePtzLocate(boolean)`, `setPtzViewVisible(boolean)`, `updateSupportPtz(boolean)`, `hideBottomFunction()`, `getBottomHeight()`, `switchAlarmFragment()`, `onShowVRDetectTrack()`, `on180VRPTZCtrl(int, boolean)`, `onPanoramaPTZStart/Move/End`.

### 1.2 PTZ
- O controle é um **D-pad visual desenhado sobre o vídeo** (setas), não uma lista de botões de texto.
- Classes REA: `com.xm.ui.widget.ptzview.PtzView`, `com.xworld.widget.PtzLocateView`, `com.xworld.manager.PtzControlCmdManager`, `com.xworld.manager.DevPtzAbilityManager`, `com.lib.sdk.bean.PtzCtrlInfoBean`, `com.lib.sdk.bean.PtzAbility`, `com.lib.sdk.struct.SDK_PtzLinkConfig`, `com.lib.sdk.bean.MultiChnPtzLocate`.
- Recruit features observadas: **Favoritar**, **Iniciar Patrulha**, **Editar**, **Ordem positiva**, **Definir como ponto daemon** (toast: "Posição alterada com sucesso"), **Girar para cima-baixo**, **Girar da esquerda para direita**, **Inversão PTZ**, **Vinculação** (calibração Figura A/B entre câmera bullet e speed dome).
- `DeviceManager.devPTZControl(PtzCtrlInfoBean, OnDevManagerListener)` é o envio de comando.

### 1.3 IA e regras
- `Alerta inteligente` → **Detecção de corpo humano**, **Mostrar rastros**, **Configurações de regra inteligente**, **Linha de aviso**, **Área de aviso**.
- `SmartAnalyzeFunctionView` (REA): `RecyclerView` + `AdaptiveLayoutManager`, 13 métodos, 7 campos — chips horizontais destas opções.
- Linha de aviso: **Direção positiva / Direção negativa / Aviso bidirecional**, dica "Um alarme será acionado somente quando a linha de advertência na direção da seta for cruzada", **Restaurar / Desfazer / Concluído**.
- Área de aviso: **Retângulo / Pentágono / Forma de L / Côncavo**, mesma barra Restaurar/Desfazer/Concluído.
- Configurações avançadas de alarme (u044): mostrar rastros, intervalo de notificação (30 s – 30 min), tempo de alarme (24 h ou personalizado), sensibilidade (baixo/médio/alto).
- Detecção de chamas, pessoas/veículos/não motorizados/animais e queda aparecem na aba **Serviço**, atreladas a planos pagos — **fora de escopo no seeAll** (IA será local no agente).
- O REA também confirmou `com.lib.sdk.bean.smartanalyze.DetectAnalyze`, `com.lib.sdk.struct.SDK_AlarmAnalysisAreaInfo`, `com.xworld.activity.AddAlarmDetectionActivity`, `com.xworld.activity.alarm.*`.

### 1.4 Gravação e nuvem
- `Configurações de gravação`: **Botão REC**, **Gravação 24 horas**, **Registro de alarme**, segmento 5–120 min, gravação de áudio, qualidade (Péssimo→Excelente), modo REC HD, aviso "Erro de gravação na partição. Formate o cartão SD ou substitua-o."
- `Modo de alarme`: sino, luz de aviso (10 s), capturar imagem, gravar vídeo. Aviso: "Cartão SD ou armazenamento na nuvem não detectado, não é possível guardar".
- `Busca IA Inteligente` / `Pesquisa de registros de IA` separados da reprodução comum.
- Nuvem do iCSee é **proprietária e expirada**: disco 10 G, planos, renovação, cupons, WeChat, 4 G — **tudo isso será substituído** no seeAll.

### 1.5 Configurações da câmera (dentro do monitor, não em tela solta)
- Básica: nome, girar imagem L/R e cima/baixo, idioma, velocidade de rotação, comunicação de voz full duplex, volume, mudança dia/noite (automática/sensibilidade/modo diurno/noturno/programada), hora e fuso.
- Vinculação da câmera (calibração A/B).
- Armazenamento (sem SD inserido: aviso).
- Alarme inteligente (push, não perturbe, notificação do sistema, rastros, regra, intervalo, sensibilidade).
- Rede (AP/router, Wi-Fi, senha digitada pelo usuário).
- Avançadas (rastreamento de movimento, WDR, conexão NVR/PC).
- Sobre o dispositivo: serial, modelo `X6E-WEQ`, PID, firmware, data, fuso, IP.

---

## 2. Tradução iCSee → seeAll

| iCSee (observado) | seeAll (proposto) | Motivo |
|---|---|---|
| Nuvem proprietária, planos, cupons, WeChat, 4 G | **Bucket do cliente** (S3/S3-compatible), credenciais digitadas pelo usuário no app | Cloud-first sem vídeo no backend; nenhuma cobrança/assinatura |
| IA na nuvem ("análise baseada em grande modelo na nuvem") | **IA local-first no agente da LAN**, eventos com miniatura e metadados | Privacidade, custo, funcionamento sem internet |
| Lista de botões de texto para PTZ | **D-pad visual sobre o vídeo** + favoritos/patrulha | O iCSee já faz assim; é mais rápido e é o que o usuário reconhece |
| Login/contas/compartilhamento | **Sem login na loja**; vínculo por código MAC do aparelho | Regra Raptor/seeAll: usuário digita credenciais no app, nunca no chat |
| Serial/PID expostos na tela de sobre | Exibir **código MAC** e modelo, sem serial completo | Segurança |
| Detecção de queda/chamas/veículos premium | Fora do M1; apenas pessoa e regras de linha/área | Escopo local-first primeiro |

---

## 3. Interface proposta — seeAll

### 3.1 Abas inferiores (4, fixas)
1. **Dispositivos** — lista de câmeras com estado Online/Offline, sinal e último evento.
2. **Ao vivo** — tela central (abaixo).
3. **Eventos** — linha do tempo de detecções locais com miniatura, filtro por câmera/tipo, e indicador "no seu bucket".
4. **Ajustes** — bucket/credenciais do cliente, agente LAN, IA, sobre.

### 3.2 Ao vivo (coração da experiência)
```
┌─────────────────────────────────────────┐
│ ‹ Dispositivo1        AO VIVO  • online │  ← status + vazão (KB/s)
├─────────────────────────────────────────┤
│                                         │
│              [ VÍDEO ]                  │
│      moldura de pessoas (IA local)      │
│                      ┌───┐              │
│                    ┌─│ ▲ │─┐            │  ← D-pad sobre o vídeo
│                    │◀│ ● │▶│            │
│                    └─│ ▼ │─┘            │
│                      └───┘              │
│  [★ ponto] [≡ patrulha] [⇄ inverter]    │
├─────────────────────────────────────────┤
│ Tempo real | Gravação | Mensagem        │  ← abas do iCSee mantidas
├─────────────────────────────────────────┤
│ AI  PTZ  Luz/Som  Rastrear  Favoritos   │  ← barra de funções (scroll)
├─────────────────────────────────────────┤
│ Dispositivos   Ao vivo   Eventos  Ajustes│
└─────────────────────────────────────────┘
```
- **D-pad** translúcido sobre o vídeo; aparece só para câmeras com `PtzAbility`; inversão e velocidade vêm do agente.
- **Favoritos**: salvar posição atual, patrulhar entre posições (equivalente a "Definir como ponto daemon").
- **AI**: alterna molduras de detecção local; abre regras (linha/área) sem sair do ao vivo.
- **Luz/Som**: sino e luz de aviso com duração (1–120 s), sempre com confirmação visual.
- **Rastrear**: rastreamento de movimento (tracking local).

### 3.3 Regras de IA (linha/área)
- Mesma interação do iCSee: arrastar linha/polígono diretamente sobre o vídeo.
- Chips horizontais: **Direção positiva / negativa / bidirecional** (linha) e **Retângulo / Pentágono / Forma de L / Côncavo** (área).
- Barra **Restaurar · Desfazer · Concluído**; dica contextual igual à observada.
- Sensibilidade e intervalo de notificação ficam na mesma folha, com valores locais.

### 3.4 Gravação (SD vs bucket do cliente)
- Alternador claro: **Cartão SD** (se presente) · **Seu bucket** (configurado em Ajustes).
- Segmentos 5–120 min, áudio ligado/desligado, qualidade, modo contínuo ou por evento.
- Fila local no agente: se o bucket estiver fora, eventos e clipes ficam enfileirados e sobem quando voltar — **backend nunca recebe vídeo**.
- Busca IA: filtrar eventos por pessoa/linha/área/câmera, com miniatura do evento.

### 3.5 Ajustes
- **Bucket do cliente**: endpoint, bucket, região, chave de acesso — digitados pelo usuário no app; nunca enviados ao chat nem ao backend.
- **Agente LAN**: descobrir agente, status de saúde, atualização automática.
- **IA local**: modelo, sensibilidade, horas de operação, desenhar molduras.
- **Rede da câmera**: Wi-Fi/AP — senha digitada pelo usuário no aparelho.
- **Sobre**: MAC do aparelho, modelo, versão do agente, caminhos de snapshot/vídeo.

---

## 4. Ordem de implementação sugerida

| Marco | Entrega |
|---|---|
| M3 | P2P proprietário (XMEye) como fallback após ONVIF/RTSP |
| M4 | Gravação S3 no bucket do cliente com fila local (esta spec) |
| M5 | IA local no agente (pessoa + linha/área) |
| M6 | Busca de eventos no app |
| M7 | Backend/distribuição (sem vídeo) |
| UI | Nova interface acima — **implementar junto com M4/M5**, depois da validação desta spec |

---

## 5. Restrições (não negociáveis)

- Não publicar scanner, nova UI ou APK sem validação explícita do usuário.
- Credenciais, senhas, documentos e códigos são digitados pelo usuário no app/site — nunca no chat.
- Raptor/seeAll em inglês público nas lojas (EUA/IE/UK); PT apenas em `/pt/`.
- Sem termos de IPTV, lista, M3U, Xtream, assinatura, provedor, servidor, canais, filmes ou séries.
- Nenhum vídeo passa pelo backend; nuvem é sempre do cliente.
