package com.ise.proxy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private var waitingForVpn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.toggle)
        status = findViewById(R.id.status)
        toggle.setOnClickListener { toggleProxy() }
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        if (waitingForVpn && Settings.canDrawOverlays(this)) {
            val prepare = VpnService.prepare(this)
            if (prepare == null) {
                startProxyService()
                waitingForVpn = false
            } else {
                startActivityForResult(prepare, REQ_VPN)
            }
        }
    }

    private fun toggleProxy() {
        if (ProxyVpnService.isRunning) {
            stopService(Intent(this, ProxyVpnService::class.java))
            updateUi()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            waitingForVpn = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")))
            return
        }

        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            waitingForVpn = true
            startActivityForResult(prepare, REQ_VPN)
        } else {
            startProxyService()
        }
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        updateUi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) {
            startProxyService()
        }
        waitingForVpn = false
        updateUi()
    }

    private fun updateUi() {
        val on = ProxyVpnService.isRunning
        status.text = if (on) "Proxy đang bật • Bong bóng đang hiển thị" else "Proxy đang tắt"
        toggle.text = if (on) "TẮT PROXY" else "BẬT PROXY"
    }

    companion object { private const val REQ_VPN = 1001 }
}
