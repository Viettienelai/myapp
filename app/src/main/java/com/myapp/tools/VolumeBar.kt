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
import kotlin.math.abs
import android.graphics.drawable.GradientDrawable

class VolumeBar(private val ctx: Context) {

    private val wm = ctx.getSystemService(WINDOW_SERVICE) as WindowManager
    private val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
    private var barView: View? = null

    private val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    // Hàm quy đổi dp
    private fun dp(px: Int): Int = (ctx.resources.displayMetrics.density * px).toInt()

    fun setup() {
        destroy() // Rất quan trọng: Gỡ view cũ khi DPI thay đổi

        // y: 410px -> ~136dp | h: 290px -> ~96dp
        addBar(136, 96) {
            am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBar(yPosDp: Int, hDp: Int, act: () -> Unit) {
        val p = WindowManager.LayoutParams(
            dp(20), dp(hDp), layoutType, // 20dp thay vì fix cứng 60px
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            -3
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = dp(yPosDp)
            windowAnimations = 0
        }

        // 1. Vùng cảm ứng (Trong suốt)
        val v = FrameLayout(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            // 2. Tạo Indicator (Thanh mờ nhỏ bên trong)
            val indicator = View(ctx).apply {
                val indicatorWidth = dp(4)
                layoutParams = FrameLayout.LayoutParams(
                    indicatorWidth,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.END
                    rightMargin = dp(3)
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.argb(90, 120, 120, 120))
                    cornerRadius = indicatorWidth / 2f
                }
            }

            addView(indicator)

            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
            }

            var x0 = 0f
            var y0 = 0f
            var d = false
            var isVertical = false

            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = e.rawX
                        y0 = e.rawY
                        d = false
                        isVertical = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!d) {
                            if (e.rawX - x0 < -dp(7)) { // 20px -> 7dp
                                d = true
                                isVertical = false
                                act()
                            }
                            else if (abs(e.rawY - y0) > dp(7)) {
                                d = true
                                isVertical = true
                                y0 = e.rawY
                            }
                        } else if (isVertical) {
                            val diff = y0 - e.rawY
                            val step = dp(13).toFloat() // 40px -> 13dp
                            if (abs(diff) > step) {
                                val direction = if (diff > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                am.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
                                y0 = e.rawY
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!d) {
                            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            wm.updateViewLayout(this, p)

                            postDelayed({
                                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                if (isAttachedToWindow) {
                                    wm.updateViewLayout(this, p)
                                }
                            }, 500)
                        }
                    }
                }
                true
            }
        }

        wm.addView(v, p)
        barView = v
    }

    fun destroy() {
        barView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
    }
}