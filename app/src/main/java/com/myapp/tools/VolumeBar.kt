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

    fun setup() {
        addBar(410, 290) {
            am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBar(yPos: Int, h: Int, act: () -> Unit) {
        val p = WindowManager.LayoutParams(
            60, h, layoutType, // 60 là chiều rộng vùng cảm ứng
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            -3
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = yPos
            windowAnimations = 0
        }

        // 1. Vùng cảm ứng (Trong suốt)
        val v = FrameLayout(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT) // Giữ vùng này trong suốt
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            // 2. Tạo Indicator (Thanh mờ nhỏ bên trong)
            val indicator = View(ctx).apply {
                val density = ctx.resources.displayMetrics.density

                // 1. Tùy chỉnh kích thước
                val indicatorWidth = (4 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(
                    indicatorWidth,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.END
                    rightMargin = (3 * density).toInt()
                }

                // 2. Tạo hình dạng bo tròn bằng GradientDrawable (thay thế setBackgroundColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.argb(90, 120, 120, 120)) // Màu xám mờ

                    // Bo tròn góc: lấy bán kính bằng 1/2 chiều rộng để tạo hình viên thuốc (capsule)
                    cornerRadius = indicatorWidth / 2f
                }
            }

            // Thêm indicator vào trong vùng cảm ứng
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
                            // Check vuốt ngang
                            if (e.rawX - x0 < -20) {
                                d = true
                                isVertical = false
                                act()
                            }
                            // Check vuốt dọc
                            else if (abs(e.rawY - y0) > 20) {
                                d = true
                                isVertical = true
                                y0 = e.rawY
                            }
                        } else if (isVertical) {
                            val diff = y0 - e.rawY
                            val step = 40f
                            if (abs(diff) > step) {
                                val direction = if (diff > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                am.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
                                y0 = e.rawY
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Nếu nhấc tay lên mà chưa detect được vuốt -> Là CLICK
                        if (!d) {
                            // 1. Thêm flag NOT_TOUCHABLE: Chuyển sang chế độ "xuyên thấu"
                            // Bar vẫn hiện nhưng không còn bắt sự kiện chạm nữa
                            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            wm.updateViewLayout(this, p)

                            // 2. Sau 0.5s thì gỡ flag để nhận cảm ứng lại
                            postDelayed({
                                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                // Kiểm tra view còn tồn tại không trước khi update
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