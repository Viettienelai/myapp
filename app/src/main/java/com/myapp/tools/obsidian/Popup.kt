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
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.myapp.R

class Popup(
    private val c: Context,
    private val root: FrameLayout,
    private val onClose: () -> Unit
) {

    // Hàm chuyển đổi px sang dp
    private fun dp(px: Int): Int = (c.resources.displayMetrics.density * px).toInt()

    fun checkAndShowSync(grid: View) {
        val hasEmail = Config.getUserEmail(c) != null

        if (hasEmail) {
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
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            // Đặt scale khởi tạo nhỏ hơn để hiệu ứng bung Spring rõ ràng hơn
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
        }

        // Fade in độ mờ mượt mà
        container.animate().alpha(1f).setDuration(200).start()

        // Spring Animation bung lụa cho Scale X
        SpringAnimation(container, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.6f
                stiffness = SpringForce.STIFFNESS_LOW
            }
            start()
        }

        // Spring Animation bung lụa cho Scale Y
        SpringAnimation(container, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.6f
                stiffness = SpringForce.STIFFNESS_LOW
            }
            start()
        }

        // 1. NÚT SETTING
        val settingsBtn = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                bottomMargin = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(120, 0, 0, 0))
            }
            addView(ImageView(c).apply {
                setImageResource(R.drawable.setting)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
            })
            setOnClickListener {
                val intent = Intent(c, SetupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                c.startActivity(intent)
                onClose()
            }
        }
        container.addView(settingsBtn)

        // 2. SLIDER SYNC
        val sliderW = dp(48)
        val sliderH = dp(250)
        val thumbSize = dp(40)
        val padding = (sliderW - thumbSize) / 2
        val centerY = (sliderH - thumbSize) / 2

        val slider = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(sliderW, sliderH)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 150f // Corner radius thường để f trực tiếp hoặc dùng dp nếu muốn chính xác tuyệt đối
                setColor(Color.argb(60, 255, 255, 255))
            }
        }

        slider.addView(ImageView(c).apply {
            setImageResource(R.drawable.drive)
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), 49).apply {
                topMargin = dp(14)
            }
        })

        slider.addView(ImageView(c).apply {
            setImageResource(R.drawable.phone)
            layoutParams = FrameLayout.LayoutParams(dp(25), dp(25), 81).apply {
                bottomMargin = dp(13)
            }
        })

        // --- 6 Dấu chấm hiệu ứng ---
        val dotSize = dp(3)
        val dotGap = dp(10)
        val dotStartDist = dp(21)

        for (i in 0 until 3) {
            val delay = (i * 150).toLong()

            fun animateDot(v: View) {
                ObjectAnimator.ofFloat(v, "alpha", 0.1f, 1f).apply {
                    duration = 500
                    startDelay = delay
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }

            // Dấu chấm phía TRÊN
            val topDot = View(c).apply {
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = centerY - dotStartDist - (i * dotGap) - dotSize
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                alpha = 0.3f
            }
            animateDot(topDot)
            slider.addView(topDot)

            // Dấu chấm phía DƯỚI
            val botDot = View(c).apply {
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = centerY + thumbSize + dotStartDist + (i * dotGap)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                alpha = 0.3f
            }
            animateDot(botDot)
            slider.addView(botDot)
        }

        val thumb = FrameLayout(c).apply {
            layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize, 51)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            addView(ImageView(c).apply {
                setImageResource(R.drawable.obsidian2)
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), 17)
            })
            elevation = 10f
            translationX = padding.toFloat()
            translationY = centerY.toFloat()
        }

        val maxY = (sliderH - thumbSize - padding).toFloat()
        val minY = padding.toFloat()
        val activationThreshold = dp(2)

        thumb.setOnTouchListener(object : View.OnTouchListener {
            var dY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> dY = v.translationY - event.rawY
                    MotionEvent.ACTION_MOVE -> {
                        var newY = event.rawY + dY
                        if (newY < minY) newY = minY
                        if (newY > maxY) newY = maxY
                        v.translationY = newY
                    }
                    MotionEvent.ACTION_UP -> {
                        if (v.translationY <= minY + activationThreshold) {
                            triggerSync(true)
                            onClose()
                        } else if (v.translationY >= maxY - activationThreshold) {
                            triggerSync(false)
                            onClose()
                        } else {
                            v.animate()
                                .translationY(centerY.toFloat())
                                .setInterpolator(OvershootInterpolator())
                                .setDuration(300)
                                .start()
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
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf("is_upload" to isUpload))
            .addTag("manual_sync")
            .build()
        wm.enqueue(req)
    }
}