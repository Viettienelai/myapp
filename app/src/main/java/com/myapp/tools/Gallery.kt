package com.myapp.tools

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class Gallery(private val c: Context, private val w: WindowManager) {

    private fun dp(px: Int): Int = (c.resources.displayMetrics.density * px).toInt()

    fun expand(root: FrameLayout, windowContainer: FrameLayout) {
        val lp = windowContainer.layoutParams as WindowManager.LayoutParams
        val rootLp = root.layoutParams as FrameLayout.LayoutParams

        val screenWidth = c.resources.displayMetrics.widthPixels

        val targetWidth = dp(320)
        val targetHeight = dp(340)

        val containerWidth = targetWidth + dp(40)
        val targetX = (screenWidth - containerWidth) / 2f

        val currentWidth = if (root.width > 0) root.width else dp(64)
        val currentHeight = if (root.height > 0) root.height else dp(200)
        val currentX = lp.x.toFloat()

        SpringAnimation(FloatValueHolder(currentWidth.toFloat())).apply {
            spring = SpringForce(targetWidth.toFloat()).apply { dampingRatio = 1f; stiffness = 400f }
            addUpdateListener { _, value, _ ->
                rootLp.width = value.toInt()
                root.layoutParams = rootLp
                if (windowContainer.isAttachedToWindow) runCatching { w.updateViewLayout(windowContainer, lp) }
            }
        }.start()

        SpringAnimation(FloatValueHolder(currentHeight.toFloat())).apply {
            spring = SpringForce(targetHeight.toFloat()).apply { dampingRatio = 1f; stiffness = 400f }
            addUpdateListener { _, value, _ ->
                rootLp.height = value.toInt()
                root.layoutParams = rootLp
            }
        }.start()

        SpringAnimation(FloatValueHolder(currentX)).apply {
            spring = SpringForce(targetX).apply { dampingRatio = 1f; stiffness = 400f }
            addUpdateListener { _, value, _ ->
                lp.x = value.toInt()
                if (windowContainer.isAttachedToWindow) runCatching { w.updateViewLayout(windowContainer, lp) }
            }
        }.start()

        val mainContainer = root.findViewWithTag<View>("main_container")
        mainContainer?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            mainContainer.visibility = View.GONE
        }?.start()

        val bg = root.background as? GradientDrawable
        bg?.let {
            ValueAnimator.ofFloat(dp(32).toFloat(), dp(20).toFloat()).apply {
                duration = 200
                addUpdateListener { animator -> it.cornerRadius = animator.animatedValue as Float }
                start()
            }
            ValueAnimator.ofArgb(Color.rgb(40, 40, 40), Color.BLACK).apply {
                duration = 200
                addUpdateListener { animator -> it.setColor(animator.animatedValue as Int) }
                start()
            }
        }

        val galleryContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            alpha = 0f
            tag = "gallery_container"
        }

        val imageMap = mutableMapOf<Int, String>()
        runCatching {
            val files = c.assets.list("gallery") ?: emptyArray()
            for (file in files) {
                val number = file.substringBeforeLast(".").toIntOrNull()
                if (number != null) imageMap[number] = file
            }
        }

        val imgSizePx = (dp(300) - dp(20) - dp(3)) / 4

        fun createGrid(startIndex: Int): LinearLayout {
            val gridContainer = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(dp(10), dp(10), dp(10), dp(10))
                }
            }

            for (row in 0 until 2) {
                val rowLayout = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        if (row == 0) bottomMargin = dp(1)
                    }
                }
                for (col in 0 until 4) {
                    val idx = startIndex + row * 4 + col
                    val box = FrameLayout(c).apply {
                        layoutParams = LinearLayout.LayoutParams(imgSizePx, imgSizePx).apply {
                            if (col < 3) rightMargin = dp(1)
                        }
                    }
                    if (imageMap.containsKey(idx)) {
                        runCatching {
                            val inputStream = c.assets.open("gallery/${imageMap[idx]}")
                            val bmp = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()

                            val iv = ImageView(c).apply {
                                layoutParams = FrameLayout.LayoutParams(-1, -1)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageBitmap(bmp)
                            }
                            box.addView(iv)

                            box.setOnClickListener { openFullscreen(box, bmp) }
                        }
                    }
                    rowLayout.addView(box)
                }
                gridContainer.addView(rowLayout)
            }
            return gridContainer
        }

        val topBlock = createGrid(1)

        val divider = View(c).apply {
            setBackgroundColor(Color.argb(80, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(1))
        }

        val bottomBlock = createGrid(9)

        galleryContainer.addView(topBlock)
        galleryContainer.addView(divider)
        galleryContainer.addView(bottomBlock)

        root.addView(galleryContainer)
        galleryContainer.animate().alpha(1f).setDuration(200).setStartDelay(100).start()
    }

    // --- HÀM XỬ LÝ ẢNH FULLSCREEN (Hero Animation) ---
    private fun openFullscreen(v: View, bmp: Bitmap) {
        // Container chính bám Fullscreen nhưng nền trong suốt
        val fsContainer = FrameLayout(c).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
        }

        // Tạo 1 layer nền đen riêng biệt để chỉnh Alpha (mờ) mà không ảnh hưởng ảnh
        val bgView = View(c).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        fsContainer.addView(bgView)

        // Lấy tọa độ tuyệt đối của ảnh Thumbnail trên màn hình
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val startX = loc[0].toFloat()
        val startY = loc[1].toFloat()
        val startW = v.width.toFloat()
        val startH = v.height.toFloat()

        val screenW = c.resources.displayMetrics.widthPixels.toFloat()
        val screenH = c.resources.displayMetrics.heightPixels.toFloat()

        // Tạo ảnh động bay ra (Không thay đổi alpha, luôn = 1f)
        val animImg = ImageView(c).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(startW.toInt(), startH.toInt())
            x = startX
            y = startY
            alpha = 1f
        }
        fsContainer.addView(animImg)

        val fsLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            2038,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        runCatching { w.addView(fsContainer, fsLp) }

        val stiff = 800f
        val damp = 1f

        // 1. Phủ mờ nền đen
        SpringAnimation(bgView, DynamicAnimation.ALPHA).apply {
            spring = SpringForce(1f).apply { stiffness = stiff; dampingRatio = damp }
        }.start()

        // 2. Phóng to & Dịch chuyển Ảnh ra giữa
        SpringAnimation(FloatValueHolder(startW)).apply {
            spring = SpringForce(screenW).apply { stiffness = stiff; dampingRatio = damp }
            addUpdateListener { _, value, _ ->
                val lp = animImg.layoutParams; lp.width = value.toInt(); animImg.layoutParams = lp
            }
        }.start()

        SpringAnimation(FloatValueHolder(startH)).apply {
            spring = SpringForce(screenH).apply { stiffness = stiff; dampingRatio = damp }
            addUpdateListener { _, value, _ ->
                val lp = animImg.layoutParams; lp.height = value.toInt(); animImg.layoutParams = lp
            }
        }.start()

        SpringAnimation(animImg, DynamicAnimation.X).apply {
            spring = SpringForce(0f).apply { stiffness = stiff; dampingRatio = damp }
        }.start()

        SpringAnimation(animImg, DynamicAnimation.Y).apply {
            spring = SpringForce(0f).apply { stiffness = stiff; dampingRatio = damp }
        }.start()

        // --- XỬ LÝ KHI ĐÓNG ẢNH ---
        var closing = false
        fsContainer.setOnClickListener {
            if (closing) return@setOnClickListener
            closing = true

            // Bộ đếm đồng bộ để chặn nháy đen (Chỉ removeView khi CẢ 5 lò xo đã dừng)
            var finishedCount = 0
            val totalAnimations = 5

            fun onAnimFinished() {
                finishedCount++
                if (finishedCount == totalAnimations) {
                    runCatching { w.removeView(fsContainer) }
                }
            }

            // Thu hồi nền đen mờ
            SpringAnimation(bgView, DynamicAnimation.ALPHA).apply {
                spring = SpringForce(0f).apply { stiffness = stiff; dampingRatio = damp }
                addEndListener { _, _, _, _ -> onAnimFinished() }
            }.start()

            // Thu hồi Ảnh về vị trí cũ
            SpringAnimation(FloatValueHolder(animImg.width.toFloat())).apply {
                spring = SpringForce(startW).apply { stiffness = stiff; dampingRatio = damp }
                addUpdateListener { _, value, _ ->
                    val lp = animImg.layoutParams; lp.width = value.toInt(); animImg.layoutParams = lp
                }
                addEndListener { _, _, _, _ -> onAnimFinished() }
            }.start()

            SpringAnimation(FloatValueHolder(animImg.height.toFloat())).apply {
                spring = SpringForce(startH).apply { stiffness = stiff; dampingRatio = damp }
                addUpdateListener { _, value, _ ->
                    val lp = animImg.layoutParams; lp.height = value.toInt(); animImg.layoutParams = lp
                }
                addEndListener { _, _, _, _ -> onAnimFinished() }
            }.start()

            SpringAnimation(animImg, DynamicAnimation.X).apply {
                spring = SpringForce(startX).apply { stiffness = stiff; dampingRatio = damp }
                addEndListener { _, _, _, _ -> onAnimFinished() }
            }.start()

            SpringAnimation(animImg, DynamicAnimation.Y).apply {
                spring = SpringForce(startY).apply { stiffness = stiff; dampingRatio = damp }
                addEndListener { _, _, _, _ -> onAnimFinished() }
            }.start()
        }
    }
}