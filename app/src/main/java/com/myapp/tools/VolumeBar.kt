package com.myapp.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.view.*
import android.widget.FrameLayout

class VolumeBar(private val ctx: Context) {

    private val wm = ctx.getSystemService(WINDOW_SERVICE) as WindowManager
    private var barView: View? = null

    // SỬA: Xác định loại Overlay
    private val layoutType =
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    fun setup() {
        addBar(400, 320, Color.argb(50, 255, 0, 0)) {
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBar(yPos: Int, h: Int, color: Int, act: () -> Unit) {
        val v = FrameLayout(ctx).apply {
            setBackgroundColor(color)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null) // Tối ưu pin

            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
            }

            var x0 = 0f; var d = false
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { x0 = e.rawX; d = false }
                    MotionEvent.ACTION_MOVE -> if (!d && e.rawX - x0 < -20) { d = true; act() }
                }
                true
            }
        }

        val p = WindowManager.LayoutParams(
            40, h, layoutType, // SỬA: Dùng Type mới
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            -3
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = yPos
        }

        wm.addView(v, p)
        barView = v
    }

    fun destroy() {
        barView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
    }
}