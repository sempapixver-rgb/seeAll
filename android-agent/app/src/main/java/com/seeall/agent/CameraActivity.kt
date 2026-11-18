package com.seeall.agent

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {
    private var player: RtspPlayer? = null
    private lateinit var config: ConfigStore
    private lateinit var events: EventRepo
    private lateinit var status: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var ptzPad: LinearLayout
    private var xaddr: String? = null
    private var name: String = "Câmera"
    private var uri: String? = null
    private var recording = false
    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        events = EventRepo(this)
        xaddr = intent.getStringExtra("xaddr")
        name = intent.getStringExtra("name") ?: config.cameraName()
        uri = intent.getStringExtra("uri")

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Cabeçalho sobre o vídeo
        val header = TextView(this).apply {
            text = "● $name   AO VIVO"
            setTextColor(Color.WHITE)
            textSize = 15f
            setBackgroundColor(Color.parseColor("#aa000000"))
            setPadding(20, 20, 20, 16)
        }
        root.addView(header, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        status = TextView(this).apply {
            text = "Conectando..."
            setTextColor(Color.parseColor("#c9d1d9"))
            textSize = 12f
            setBackgroundColor(Color.parseColor("#aa000000"))
            setPadding(20, 8, 20, 8)
        }
        root.addView(status, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ).apply { topMargin = 60 })

        // Barra inferior de funções (AI / PTZ / Luz e som / Rastrear / Favoritos)
        val funcs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#dd161b22"))
            gravity = Gravity.CENTER
        }
        listOf("AI", "PTZ", "Luz/Som", "Rastrear", "Favoritos").forEach { label ->
            val b = Button(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#c9d1d9"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, 110, 1f)
                setOnClickListener { onFunction(label) }
            }
            funcs.addView(b)
        }
        root.addView(funcs, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { bottomMargin = 110 })

        // Abas Tempo real / Gravação / Mensagem
        tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#dd0d1117"))
        }
        listOf("Tempo real", "Gravação", "Mensagem").forEachIndexed { i, label ->
            val b = Button(this).apply {
                text = label
                textSize = 12f
                setTextColor(if (i == 0) Color.parseColor("#58a6ff") else Color.parseColor("#8b949e"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, 110, 1f)
                setOnClickListener { tabClick(i) }
            }
            tabsRow.addView(b)
        }
        root.addView(tabsRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        // D-pad visual sobre o vídeo
        ptzPad = buildPtzPad()
        root.addView(ptzPad, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER or Gravity.END
        ).apply { rightMargin = 24; bottomMargin = 260 })

        setContentView(root)

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { startStream(holder) }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { stopPlayer() }
        })
    }

    private fun buildPtzPad(): LinearLayout {
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val up = ptzButton("▲") { ptz("up") }
        val middle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        middle.addView(ptzButton("◀") { ptz("left") })
        middle.addView(ptzButton("●") { toggleRecord() })
        middle.addView(ptzButton("▶") { ptz("right") })
        pad.addView(up); pad.addView(middle)
        pad.addView(ptzButton("▼") { ptz("down") })
        return pad
    }

    private fun ptzButton(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#9921262d"))
        layoutParams = LinearLayout.LayoutParams(110, 100).apply { setMargins(4, 4, 4, 4) }
        setOnClickListener { click() }
    }

    private fun ptz(direction: String) {
        if (xaddr == null) {
            Toast.makeText(this, "PTZ requer câmera ONVIF", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.seeall.agent.camera.OnvifPtz(applicationContext, xaddr!!, config).move(direction)
            }
            Toast.makeText(this@CameraActivity, result, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tabClick(i: Int) {
        for (k in 0 until tabsRow.childCount) {
            (tabsRow.getChildAt(k) as Button).setTextColor(
                if (k == i) Color.parseColor("#58a6ff") else Color.parseColor("#8b949e")
            )
        }
        when (i) {
            0 -> Toast.makeText(this, "Tempo real", Toast.LENGTH_SHORT).show()
            1 -> lifecycleScope.launch {
                val count = events.pendingCount()
                Toast.makeText(this@CameraActivity, "Gravações locais: $count pendente(s)", Toast.LENGTH_SHORT).show()
            }
            2 -> lifecycleScope.launch {
                val list = events.all()
                Toast.makeText(this@CameraActivity,
                    if (list.isEmpty()) "Nenhuma mensagem" else "Último: ${list.first().type}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onFunction(label: String) {
        when (label) {
            "AI" -> Toast.makeText(this, "Detecção local: pessoa", Toast.LENGTH_SHORT).show()
            "PTZ" -> {
                val visible = ptzPad.visibility == android.view.View.VISIBLE
                ptzPad.visibility = if (visible) android.view.View.GONE else android.view.View.VISIBLE
            }
            "Luz/Som" -> Toast.makeText(this, "Alarme sonoro e luminoso", Toast.LENGTH_SHORT).show()
            "Rastrear" -> Toast.makeText(this, "Rastreamento de movimento", Toast.LENGTH_SHORT).show()
            "Favoritos" -> Toast.makeText(this, "Posição salva como favorita", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRecord() {
        if (!recording) {
            currentFile = File(cacheDir, "clip-${System.currentTimeMillis()}.mp4")
            recording = true
            Toast.makeText(this, "Gravando no aparelho", Toast.LENGTH_SHORT).show()
        } else {
            val f = currentFile
            recording = false
            currentFile = null
            if (f != null && f.exists()) {
                lifecycleScope.launch {
                    val now = SimpleDateFormat("dd-MM HH:mm:ss", Locale.getDefault()).format(Date())
                    events.add(CameraEvent(
                        id = System.currentTimeMillis().toString(),
                        camera = name,
                        type = "Gravação manual",
                        time = now,
                        path = f.absolutePath
                    ))
                    events.flush(config.load())
                    Toast.makeText(this@CameraActivity, "Gravação salva e enviada se o bucket estiver ativo", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startStream(holder: SurfaceHolder) {
        lifecycleScope.launch {
            val finalUri = uri ?: withContext(Dispatchers.IO) {
                xaddr?.let { com.seeall.agent.camera.OnvifPtz.resolveUri(it, config) }
            }
            if (finalUri.isNullOrBlank()) {
                status.text = "Configure uma câmera RTSP ou descubra uma câmera ONVIF"
                return@launch
            }
            player = RtspPlayer(finalUri, null, null, holder.surface) { msg ->
                runOnUiThread { status.text = msg }
            }
            player?.start()
        }
    }

    private fun stopPlayer() { player?.stop(); player = null }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}
