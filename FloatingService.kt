package com.example.myno.floatingwindow

import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.max

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootWindowView: FrameLayout
    private lateinit var floatingBubbleView: ImageView
    
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    
    private val CHANNEL_ID = "JARVIS_Floating_OS_Channel"
    private var savedWidth = 650
    private var savedHeight = 500
    private var isWindowOpen = false // শুরুতে শুধু গোল বাবলটি দেখাবে

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        // ফোরগ্রাউন্ড সার্ভিস নোটিফিকেশন (Anti-Kill System)
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS OS Active")
            .setContentText("Floating bubble is ready on screen.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, notification)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // ================= ১. মেইন ফ্লোটিং উইন্ডো লেআউট সেটআপ =================
        rootWindowView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1E1E24")) // প্রিমিয়াম ডার্ক ব্যাকগ্রাউন্ড
        }

        windowParams = WindowManager.LayoutParams(
            savedWidth, savedHeight, layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 150
            y = 300
            windowAnimations = android.R.style.Animation_Dialog // স্মুথ পপ-আপ অ্যানিমেশন
        }

        val containerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // টপ ড্র্যাগ বার (উইন্ডো সরানোর জন্য)
        val topDragBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#20FFFFFF")) // হালকা গ্লাস ইফেক্ট টপ বার
            setPadding(20, 15, 20, 15)
        }
        
        val titleText = TextView(this).apply {
            text = "JARVIS Custom OS"
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        // মিনিমাইজ বাটন (যা ক্লিক করলে উইন্ডোটি আবার গোল বাবল হয়ে যাবে)
        val minimizeButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_minus)
            setPadding(0, 0, 20, 0)
            setOnClickListener { minimizeToBubble() }
        }

        // ক্লোজ বাটন (সার্ভিস পুরোপুরি বন্ধ করার জন্য)
        val closeButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener { stopSelf() }
        }
        
        topDragBar.addView(titleText)
        topDragBar.addView(minimizeButton)
        topDragBar.addView(closeButton)
        containerLayout.addView(topDragBar)

        // উইন্ডোর কন্টেন্ট এরিয়া
        val contentArea = LinearLayout(this).apply { setPadding(40, 60, 40, 60) }
        val insideText = TextView(this).apply {
            text = "Welcome to JARVIS UI\n\nDrag from bottom-right corner to resize.\nClick '-' to minimize into a floating bubble."
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        contentArea.addView(insideText)
        containerLayout.addView(contentArea)
        rootWindowView.addView(containerLayout)

        // রিসাইজ হ্যান্ডেল (নিচের ডান কোনায় ছোট আইকন)
        val resizeHandle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            layoutParams = FrameLayout.LayoutParams(60, 60).apply { gravity = Gravity.BOTTOM or Gravity.END }
        }
        rootWindowView.addView(resizeHandle)


        // ================= ২. ফ্লোটিং বাবল সেটআপ (Floating Bubble) =================
        floatingBubbleView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info) // বাবলের আইকন (চাইলে পরে কাস্টম ইমেজ দিতে পারবেন)
            setBackgroundColor(Color.parseColor("#FF00D2FF")) // নিয়ন সায়ান বাবল ব্যাকগ্রাউন্ড
            setPadding(10, 10, 10, 10)
        }

        bubbleParams = WindowManager.LayoutParams(
            120, 120, layoutFlag, // বাবলের সাইজ ১২০x১২০ পিক্সেল
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 500
        }

        // শুরুতে আমরা স্ক্রিনে শুধু বাবলটি দেখাবো
        windowManager.addView(floatingBubbleView, bubbleParams)


        // ================= ৩. লজিক, ড্র্যাগ ও ম্যাগনেটিক স্ন্যাপ =================

        // বাবলের ড্র্যাগ এবং স্মার্ট ম্যাগনেটিক স্ন্যাপ লজিক
        floatingBubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val displayWidth = windowManager.defaultDisplay.width

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.rawX - initialTouchX) > 10 || Math.abs(event.rawY - initialTouchY) > 10) {
                            isClick = false
                        }
                        bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingBubbleView, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            expandToWindow() // ক্লিক করলে উইন্ডো বড় হবে
                        } else {
                            // ম্যাজিক স্ন্যাপ: ছেড়ে দিলে স্বয়ংক্রিয়ভাবে ডান অথবা বামের দেয়ালে আটকে যাবে!
                            val centerOfScreen = displayWidth / 2
                            if (bubbleParams.x + (floatingBubbleView.width / 2) < centerOfScreen) {
                                bubbleParams.x = 0 // বামে স্ন্যাপ
                            } else {
                                bubbleParams.x = displayWidth - floatingBubbleView.width // ডানে স্ন্যাপ
                            }
                            windowManager.updateViewLayout(floatingBubbleView, bubbleParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // মেইন উইন্ডো ড্র্যাগ লজিক
        topDragBar.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val displayWidth = windowManager.defaultDisplay.width

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowParams.x
                        initialY = windowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        windowParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        windowParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(rootWindowView, windowParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // উইন্ডোর জন্যও স্মার্ট স্ন্যাপ লজিক (ছেড়ে দিলে বর্ডারে চলে যাবে)
                        val centerOfScreen = displayWidth / 2
                        if (windowParams.x + (rootWindowView.width / 2) < centerOfScreen) {
                            windowParams.x = 10 // বাম দিকে বর্ডারের কাছে স্ন্যাপ
                        } else {
                            windowParams.x = displayWidth - rootWindowView.width - 10 // ডান দিকে বর্ডারের কাছে স্ন্যাপ
                        }
                        windowManager.updateViewLayout(rootWindowView, windowParams)
                        return true
                    }
                }
                return false
            }
        })

        // উইন্ডো রিসাইজ (ছোট-বড়) করার লজিক
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = windowParams.width
                        initialHeight = windowParams.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        savedWidth = max(380, initialWidth + (event.rawX - initialTouchX).toInt())
                        savedHeight = max(300, initialHeight + (event.rawY - initialTouchY).toInt())
                        windowParams.width = savedWidth
                        windowParams.height = savedHeight
                        windowManager.updateViewLayout(rootWindowView, windowParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    // উইন্ডো ছোট করে বাবলে রূপান্তর করা
    private fun minimizeToBubble() {
        if (isWindowOpen) {
            isWindowOpen = false
            windowManager.removeView(rootWindowView)
            windowManager.addView(floatingBubbleView, bubbleParams)
        }
    }

    // বাবল ক্লিক করে মেইন উইন্ডো বড় করা
    private fun expandToWindow() {
        if (!isWindowOpen) {
            isWindowOpen = true
            windowManager.removeView(floatingBubbleView)
            windowManager.addView(rootWindowView, windowParams)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "JARVIS Floating OS Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isWindowOpen && ::rootWindowView.isInitialized && rootWindowView.windowToken != null) {
            windowManager.removeView(rootWindowView)
        }
        if (!isWindowOpen && ::floatingBubbleView.isInitialized && floatingBubbleView.windowToken != null) {
            windowManager.removeView(floatingBubbleView)
        }
    }
}