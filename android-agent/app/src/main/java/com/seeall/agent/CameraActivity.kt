package com.seeall.agent

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CameraActivity : AppCompatActivity() {
    private var player: RtspPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val uri = intent.getStringExtra("uri").orEmpty()
        val user = intent.getStringExtra("user")
        val pass = intent.getStringExtra("pass")
        val status = findViewById<TextView>(R.id.cameraStatus)
        val surface = findViewById<SurfaceView>(R.id.videoSurface)

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player = RtspPlayer(uri, user, pass, holder.surface) { msg ->
                    runOnUiThread { status.text = msg }
                }
                lifecycleScope.launch { player?.start() }
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { player?.stop() }
        })
    }

    override fun onDestroy() {
        player?.stop()
        super.onDestroy()
    }
}
