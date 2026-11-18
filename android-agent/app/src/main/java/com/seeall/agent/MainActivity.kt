package com.seeall.agent

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var config: ConfigStore
    private lateinit var events: EventRepo
    private lateinit var content: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var discovery: OnvifDiscovery
    private var currentTab = TAB_DEVICES

    companion object {
        const val TAB_DEVICES = 0
        const val TAB_LIVE = 1
        const val TAB_EVENTS = 2
        const val TAB_SETTINGS = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        events = EventRepo(this)
        discovery = OnvifDiscovery(applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0d1117"))
        }
        val title = TextView(this).apply {
            text = "seeAll"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(24, 28, 24, 6)
        }
        val subtitle = TextView(this).apply {
            text = "Câmeras locais • nuvem do cliente"
            setTextColor(Color.parseColor("#8b949e"))
            textSize = 12f
            setPadding(24, 0, 24, 16)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(20, 4, 20, 12)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#161b22"))
        }
        root.addView(title); root.addView(subtitle); root.addView(scroll); root.addView(tabBar)
        setContentView(root)

        buildTabs()
        selectTab(TAB_DEVICES)
    }

    private fun buildTabs() {
        val labels = listOf("Dispositivos", "Ao vivo", "Eventos", "Ajustes")
        labels.forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, 120, 1f)
                setTextColor(Color.parseColor("#8b949e"))
                textSize = 11f
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { selectTab(index) }
            }
            tabBar.addView(b)
        }
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        for (i in 0 until tabBar.childCount) {
            val b = tabBar.getChildAt(i) as Button
            val active = i == tab
            b.setTextColor(if (active) Color.parseColor("#58a6ff") else Color.parseColor("#8b949e"))
            b.setBackgroundColor(if (active) Color.parseColor("#1f6feb30") else Color.TRANSPARENT)
        }
        content.removeAllViews()
        when (tab) {
            TAB_DEVICES -> renderDevices()
            TAB_LIVE -> renderLive()
            TAB_EVENTS -> renderEvents()
            TAB_SETTINGS -> renderSettings()
        }
    }

    private fun section(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#58a6ff"))
            textSize = 18f
            setPadding(0, 8, 0, 14)
        })
    }

    private fun button(label: String, primary: Boolean = false, click: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(if (primary) Color.WHITE else Color.parseColor("#c9d1d9"))
        b.setBackgroundColor(Color.parseColor(if (primary) "#238636" else "#21262d"))
        b.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 10 }
        b.setOnClickListener { click() }
        return b
    }

    private fun info(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#c9d1d9"))
        textSize = 13f
        setPadding(0, 6, 0, 12)
    }

    private fun renderDevices() {
        section("Dispositivos")
        content.addView(info("Descubra câmeras na rede ou adicione uma câmera RTSP."))
        content.addView(button("Descobrir câmeras (ONVIF)", true) { discover() })
        content.addView(button("Adicionar câmera RTSP") { addManual() })
        content.addView(TextView(this).apply {
            text = "Câmeras salvas"
            setTextColor(Color.parseColor("#8b949e"))
            textSize = 12f
            setPadding(0, 16, 0, 6)
        })
        val name = config.cameraName()
        content.addView(TextView(this).apply {
            text = "● $name  —  pronta para ao vivo"
            setTextColor(Color.parseColor("#3fb950"))
            textSize = 14f
            setPadding(0, 4, 0, 4)
        })
    }

    private fun renderLive() {
        section("Ao vivo")
        content.addView(info("Abra o vídeo da câmera. O controle PTZ fica sobre o vídeo."))
        content.addView(button("Abrir vídeo ao vivo", true) { openCamera(null, null, null) })
        content.addView(info("Dica: use 'Adicionar câmera RTSP' em Dispositivos para trocar o endereço."))
    }

    private fun renderEvents() {
        section("Eventos")
        lifecycleScope.launch {
            val list = events.all()
            content.removeAllViews()
            section("Eventos")
            val cfg = config.load()
            content.addView(info("Fila local: ${events.pendingCount()} pendente(s) • " +
                    if (cfg.enabled) "Bucket do cliente ativo" else "Bucket não configurado"))
            content.addView(button("Enviar fila para o bucket") { flushQueue() })
            if (list.isEmpty()) {
                content.addView(info("Nenhum evento ainda. Eventos de detecção aparecerão aqui."))
            } else {
                list.take(30).forEach { e ->
                    content.addView(TextView(this@MainActivity).apply {
                        text = "${if (e.uploaded) "☁" else "◷"} ${e.type} • ${e.camera} • ${e.time}"
                        setTextColor(Color.parseColor("#c9d1d9"))
                        textSize = 13f
                        setPadding(0, 6, 0, 6)
                    })
                }
            }
        }
    }

    private fun renderSettings() {
        section("Ajustes")
        val cfg = config.load()
        content.addView(info("Bucket do cliente (S3-compatible): " +
                if (cfg.enabled && cfg.isValid) "${cfg.bucket} • ${cfg.endpoint}" else "não configurado"))
        content.addView(button("Configurar bucket", true) { configureBucket() })
        content.addView(button("Trocar nome da câmera") { renameCamera() })
        content.addView(info("Credenciais são digitadas aqui e ficam apenas neste aparelho. " +
                "O backend nunca recebe vídeo nem credenciais."))
    }

    private fun renameCamera() {
        val input = EditText(this).apply { setText(config.cameraName()) }
        AlertDialog.Builder(this)
            .setTitle("Nome da câmera")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                config.saveCameraName(input.text.toString().ifBlank { "Camera" })
                selectTab(TAB_DEVICES)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun configureBucket() {
        val view = layoutInflater.inflate(R.layout.dialog_bucket, null)
        val cfg = config.load()
        view.findViewById<EditText>(R.id.endpointInput).setText(cfg.endpoint)
        view.findViewById<EditText>(R.id.bucketInput).setText(cfg.bucket)
        view.findViewById<EditText>(R.id.regionInput).setText(cfg.region)
        view.findViewById<EditText>(R.id.accessInput).setText(cfg.accessKey)
        view.findViewById<EditText>(R.id.secretInput).setText(cfg.secretKey)
        view.findViewById<EditText>(R.id.prefixInput).setText(cfg.prefix)
        AlertDialog.Builder(this)
            .setTitle("Nuvem do cliente")
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val c = BucketConfig(
                    endpoint = view.findViewById<EditText>(R.id.endpointInput).text.toString(),
                    bucket = view.findViewById<EditText>(R.id.bucketInput).text.toString(),
                    region = view.findViewById<EditText>(R.id.regionInput).text.toString(),
                    accessKey = view.findViewById<EditText>(R.id.accessInput).text.toString(),
                    secretKey = view.findViewById<EditText>(R.id.secretInput).text.toString(),
                    prefix = view.findViewById<EditText>(R.id.prefixInput).text.toString(),
                    enabled = true
                )
                config.save(c)
                Toast.makeText(this, "Bucket salvo neste aparelho", Toast.LENGTH_SHORT).show()
                selectTab(TAB_SETTINGS)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun flushQueue() {
        lifecycleScope.launch {
            val result = events.flush(config.load())
            Toast.makeText(this@MainActivity,
                if (result.first > 0) "${result.first} evento(s) enviado(s) ao bucket"
                else result.second.ifBlank { "Fila vazia" }, Toast.LENGTH_LONG).show()
            selectTab(TAB_EVENTS)
        }
    }

    private fun discover() {
        Toast.makeText(this, "Procurando câmeras na rede...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val found = discovery.discover()
            if (found.isEmpty()) {
                Toast.makeText(this@MainActivity, "Nenhuma câmera ONVIF encontrada", Toast.LENGTH_LONG).show()
                return@launch
            }
            val cam = found.first()
            config.saveCameraName(cam.name)
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Câmeras encontradas")
                .setItems(found.map { "${it.name} (${it.host})" }.toTypedArray()) { _, i ->
                    openCamera(found[i].xaddr, found[i].name, null)
                }
                .show()
        }
    }

    private fun addManual() {
        val view = layoutInflater.inflate(R.layout.dialog_rtsp, null)
        AlertDialog.Builder(this)
            .setTitle("Adicionar câmera RTSP")
            .setView(view)
            .setPositiveButton("Abrir") { _, _ ->
                val url = view.findViewById<EditText>(R.id.uriInput).text.toString().trim()
                if (!url.startsWith("rtsp://")) {
                    Toast.makeText(this, "Use rtsp://...", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                openCamera(null, "Câmera RTSP", url)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openCamera(xaddr: String?, name: String?, uri: String?) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("xaddr", xaddr)
            putExtra("name", name ?: config.cameraName())
            putExtra("uri", uri)
        }
        startActivity(intent)
    }
}
