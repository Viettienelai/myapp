package com.tilescan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.*
import android.widget.FrameLayout

class SidebarService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var touchView: View
    private var initialY = 0f
    private var isTriggered = false

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        touchView = FrameLayout(this).apply {
            // Mẹo: Đổi thành Color.TRANSPARENT khi chạy thật
            setBackgroundColor(Color.argb(1, 0, 0, 0))

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.rawY
                        isTriggered = false
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTriggered) {
                            val deltaY = event.rawY - initialY
                            if (deltaY > 30) {
                                isTriggered = true
                                openPopup()

                                val cancelEvent = MotionEvent.obtain(event)
                                cancelEvent.action = MotionEvent.ACTION_CANCEL
                                touchView.dispatchTouchEvent(cancelEvent)
                                cancelEvent.recycle()
                            }
                        }
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isTriggered = false
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }

        val params = WindowManager.LayoutParams().apply {
            width = 60
            height = 600
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.END

            // CẬP NHẬT VỊ TRÍ: Cách mép trên 600px
            y = 550
        }

        runCatching { wm.addView(touchView, params) }
    }

    private fun openPopup() {
        val intent = Intent(this, SidebarPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (::touchView.isInitialized) wm.removeView(touchView)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}