package com.seeall.agent

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var cameraList: LinearLayout
    private val discovery by lazy {
        OnvifDiscovery(applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        cameraList = findViewById(R.id.cameraList)

        findViewById<Button>(R.id.discoverButton).setOnClickListener { discover() }
        findViewById<Button>(R.id.addManualButton).setOnClickListener { addManual() }
    }

    private fun discover() {
        statusText.text = "Discovering cameras on the network..."
        lifecycleScope.launch {
            val found = discovery.discover()
            cameraList.removeAllViews()
            if (found.isEmpty()) {
                statusText.text = getString(R.string.no_cameras)
                return@launch
            }
            statusText.text = "Found ${found.size} camera(s)"
            found.forEach { cam -> addCameraRow(cam.name, cam.host, "rtsp://${cam.host}:554/") }
        }
    }

    private fun addManual() {
        val input = EditText(this).apply { hint = "rtsp://192.168.1.10:554/stream1" }
        AlertDialog.Builder(this)
            .setTitle("Add RTSP camera")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("rtsp://")) addCameraRow("Manual camera", url, url)
                else Toast.makeText(this, "Use rtsp://...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCameraRow(name: String, host: String, uri: String) {
        val row = TextView(this).apply {
            text = "$name\n$uri"
            textSize = 16f
            setPadding(12, 20, 12, 20)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { askCredentials(name, uri) }
        }
        cameraList.addView(row)
    }

    private fun askCredentials(name: String, uri: String) {
        val view = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val user = view.findViewById<EditText>(R.id.userInput)
        val pass = view.findViewById<EditText>(R.id.passInput)
        AlertDialog.Builder(this)
            .setTitle("Connect to $name")
            .setView(view)
            .setPositiveButton("Connect") { _, _ ->
                val intent = Intent(this, CameraActivity::class.java).apply {
                    putExtra("uri", uri)
                    putExtra("user", user.text.toString())
                    putExtra("pass", pass.text.toString())
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
