package com.replit.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class FloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = TextView(this).apply {
            text = "JARVIS Overlay Active ⚡"
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.CYAN)
            setPadding(40, 40, 40, 40)
            textSize = 16f
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager?.addView(floatingView, params)
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("float_ch", "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            
            val notification = Notification.Builder(this, "float_ch")
                .setContentTitle("Floating Window Running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { 
            windowManager?.removeView(it) 
        }
    }
}
