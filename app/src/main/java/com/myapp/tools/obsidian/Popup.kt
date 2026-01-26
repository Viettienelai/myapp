package com.myapp.tools.obsidian

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.myapp.R
import java.io.File

class Popup(
    private val c: Context,
    private val root: FrameLayout,
    private val onClose: () -> Unit
) {

    fun checkAndShowSync(grid: View) {
        // SỬA ĐỔI QUAN TRỌNG TẠI ĐÂY:
        // Không kiểm tra file rclone.conf nữa vì SyncWorker sẽ tự tạo nó khi chạy lần đầu.
        // Chỉ cần kiểm tra xem user đã đăng nhập email và đã chọn thư mục (nếu cần) hay chưa.

        val hasEmail = Config.getUserEmail(c) != null

        // (Tùy chọn) Nếu bạn bắt buộc user phải chọn folder đích thì thêm check này:
        // val hasRootFolder = Config.getRootFolderId(c) != null
        // val isReady = hasEmail && hasRootFolder

        if (hasEmail) { // Hoặc if (isReady)
            grid.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
                root.removeView(grid)
                showSyncSlider()
            }.start()
        } else {
            val intent = Intent(c, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            c.startActivity(intent)
            onClose()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSyncSlider() {
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
        }

        // 1. NÚT SETTING
        val settingsBtn = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180).apply { bottomMargin = 150 }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(120, 0, 0, 0))
            }
            addView(ImageView(c).apply {
                setImageResource(R.drawable.setting)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(80, 80, Gravity.CENTER)
            })
            setOnClickListener {
                val intent = Intent(c, SetupActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                c.startActivity(intent)
                onClose()
            }
        }
        container.addView(settingsBtn)

        // 2. SLIDER SYNC
        val sliderW = 300; val sliderH = 1320; val thumbSize = 247
        val padding = (sliderW - thumbSize) / 2
        // Vị trí Y bắt đầu của thumb (chính giữa slider)
        val centerY = (sliderH - thumbSize) / 2

        val slider = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(sliderW, sliderH)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 150f; setColor(Color.argb(60, 255, 255, 255)) }
        }
        slider.addView(ImageView(c).apply { setImageResource(R.drawable.drive); layoutParams = FrameLayout.LayoutParams(120, 120, 49).apply { topMargin = 75 } })
        slider.addView(ImageView(c).apply { setImageResource(R.drawable.phone); layoutParams = FrameLayout.LayoutParams(150, 150, 81).apply { bottomMargin = 75 } })

        // --- THÊM: 6 Dấu chấm hiệu ứng ---
        val dotSize = 16
        val dotGap = 50
        val dotStartDist = 100

        for (i in 0 until 3) {
            val delay = (i * 150).toLong() // Gần nhất delay = 0, xa nhất delay lớn

            // Hàm tạo animation chung
            fun animateDot(v: View) {
                ObjectAnimator.ofFloat(v, "alpha", 0.1f, 1f).apply {
                    duration = 500
                    startDelay = delay
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }

            // Dấu chấm phía TRÊN (giữa Thumb và Drive)
            val topDot = View(c).apply {
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = centerY - dotStartDist - (i * dotGap) - dotSize
                }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                alpha = 0.3f
            }
            animateDot(topDot)
            slider.addView(topDot)

            // Dấu chấm phía DƯỚI (giữa Thumb và Phone)
            val botDot = View(c).apply {
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = centerY + thumbSize + dotStartDist + (i * dotGap)
                }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                alpha = 0.3f
            }
            animateDot(botDot)
            slider.addView(botDot)
        }
        // ---------------------------------

        val thumb = FrameLayout(c).apply {
            layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize, 51)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            addView(ImageView(c).apply {
                setImageResource(R.drawable.obsidian2)
                layoutParams = FrameLayout.LayoutParams(135, 135, 17)
            })
            elevation = 10f
            translationX = padding.toFloat(); translationY = centerY.toFloat()
        }

        val maxY = sliderH - thumbSize - padding; val minY = padding

        thumb.setOnTouchListener(object : View.OnTouchListener {
            var dY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> dY = v.translationY - event.rawY
                    MotionEvent.ACTION_MOVE -> {
                        var newY = event.rawY + dY
                        if (newY < minY) newY = minY.toFloat()
                        if (newY > maxY) newY = maxY.toFloat()
                        v.translationY = newY
                    }
                    MotionEvent.ACTION_UP -> {
                        // --- ĐÃ SỬA: Đóng ngay lập tức khi thả tay ở vị trí kích hoạt ---
                        if (v.translationY <= minY + 20) {
                            triggerSync(true)
                            onClose() // Đóng ngay
                        } else if (v.translationY >= maxY - 20) {
                            triggerSync(false)
                            onClose() // Đóng ngay
                        } else {
                            // Nếu chưa tới đích thì hồi vị trí cũ
                            v.animate().translationY(centerY.toFloat()).setInterpolator(OvershootInterpolator()).setDuration(300).start()
                        }
                    }
                }
                return true
            }
        })

        slider.addView(thumb)
        container.addView(slider)
        root.addView(container)
    }

    private fun triggerSync(isUpload: Boolean) {
        val wm = WorkManager.getInstance(c)
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("is_upload" to isUpload)).addTag("manual_sync").build()
        wm.enqueue(req)
    }
}