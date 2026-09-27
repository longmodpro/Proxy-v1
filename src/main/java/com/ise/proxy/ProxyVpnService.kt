package com.ise.proxy

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.net.VpnService
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ProxyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var closeButton: TextView? = null

    companion object {
        @Volatile var isRunning = false
        private const val CHANNEL = "ise_proxy"
        private const val NOTIFICATION_ID = 7
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        startOverlay()
        // This VPN interface is a permission/overlay shell. It does not bypass
        // game security or redirect/block game traffic.
        try {
            vpnInterface = Builder()
                .setSession("ISE Proxy")
                .addAddress("10.8.0.2", 32)
                .addRoute("10.8.0.1", 32)
                .setBlocking(true)
                .establish()
            isRunning = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun startOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(this)
        bubble = TextView(this).apply {
            text = "ISE"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.ic_circle)
            elevation = 12f
        }
        closeButton = TextView(this).apply {
            text = "×"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.ic_x)
            visibility = View.GONE
            setOnClickListener { stopSelf() }
        }

        val bubbleSize = dp(58)
        val closeSize = dp(28)
        root.addView(bubble, FrameLayout.LayoutParams(bubbleSize, bubbleSize))
        root.addView(closeButton, FrameLayout.LayoutParams(closeSize, closeSize).apply {
            gravity = Gravity.TOP or Gravity.END
        })

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(72), dp(72), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30; y = 220
        }

        bubble?.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0
            var downY = 0
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX.toInt()
                        downY = event.rawY.toInt()
                        startX = params.x
                        startY = params.y
                        moved = false
                        closeButton?.visibility = View.VISIBLE
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - downX
                        val dy = event.rawY.toInt() - downY
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) closeButton?.visibility =
                            if (closeButton?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(root, params)
        bubble = root
    }

    override fun onDestroy() {
        isRunning = false
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        bubble?.let { root ->
            try { windowManager?.removeView(root) } catch (_: Exception) {}
        }
        bubble = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("ISE Proxy")
            .setContentText("Proxy overlay đang hoạt động")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "ISE Proxy", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
