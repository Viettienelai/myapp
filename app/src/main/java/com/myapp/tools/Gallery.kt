package com.myapp.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

@Suppress("DEPRECATION")
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

        val imageKeys = imageMap.keys.sorted().toList()
        val thumbViews = mutableMapOf<Int, View>()
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
                            thumbViews[idx] = box

                            box.setOnClickListener {
                                val clickedIndex = imageKeys.indexOf(idx)
                                openFullscreen(clickedIndex, bmp, imageKeys, thumbViews, imageMap)
                            }
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

    // --- HÀM XỬ LÝ ẢNH FULLSCREEN VÀ CỬ CHỈ DRAG ---
    @SuppressLint("ClickableViewAccessibility")
    private fun openFullscreen(startIndex: Int, clickedBmp: Bitmap, imageKeys: List<Int>, thumbViews: Map<Int, View>, imageMap: Map<Int, String>) {

        val bgView = View(c).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val boxfullimg = FrameLayout(c).apply {
            clipChildren = true
        }

        val animImg = object : ImageView(c) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            }
        }.apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_XY
            alpha = 1f
        }
        boxfullimg.addView(animImg)

        val viewPager = ViewPager2(c).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setPageTransformer(MarginPageTransformer(dp(20)))
            visibility = View.INVISIBLE
        }

        class PagerAdapter : RecyclerView.Adapter<PagerAdapter.VH>() {
            inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val iv = ImageView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                return VH(iv)
            }
            override fun onBindViewHolder(holder: VH, position: Int) {
                runCatching {
                    val key = imageKeys[position]
                    val bmp = BitmapFactory.decodeStream(c.assets.open("gallery/${imageMap[key]}"))
                    holder.iv.setImageBitmap(bmp)
                }
            }
            override fun getItemCount() = imageKeys.size
        }
        viewPager.adapter = PagerAdapter()
        viewPager.setCurrentItem(startIndex, false)

        val screenW: Float
        val screenH: Float
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = w.currentWindowMetrics.bounds
            screenW = bounds.width().toFloat()
            screenH = bounds.height().toFloat()
        } else {
            val metrics = DisplayMetrics()
            w.defaultDisplay.getRealMetrics(metrics)
            screenW = metrics.widthPixels.toFloat()
            screenH = metrics.heightPixels.toFloat()
        }

        var isClosing = false
        var isVerticalDrag = false
        var startTouchX = 0f
        var startTouchY = 0f
        var initialBoxX = 0f
        var initialBoxY = 0f

        var destX = 0f
        var destY = 0f
        var destW = 0f
        var destH = 0f
        var targetImgW = 0f
        var targetImgH = 0f

        val stiffIn = 600f
        val stiffOut = 800f
        val damp = 1f

        val fsContainer = object : FrameLayout(c) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (isClosing) return true
                if (viewPager.visibility != View.VISIBLE) return true

                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startTouchX = ev.rawX
                        startTouchY = ev.rawY
                        isVerticalDrag = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(ev.rawX - startTouchX)
                        val dy = abs(ev.rawY - startTouchY)

                        if (!isVerticalDrag && dy > dx * 1.2f && dy > dp(1)) {
                            isVerticalDrag = true
                            prepareForDrag()
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            fun prepareForDrag() {
                val currentIndex = viewPager.currentItem
                val key = imageKeys[currentIndex]

                val rv = viewPager.getChildAt(0) as? RecyclerView
                val vh = rv?.findViewHolderForAdapterPosition(currentIndex) as? PagerAdapter.VH
                val cachedDrawable = vh?.iv?.drawable

                val sW: Float
                val sH: Float

                if (cachedDrawable != null) {
                    animImg.setImageDrawable(cachedDrawable)
                    val iW = cachedDrawable.intrinsicWidth.toFloat()
                    val iH = cachedDrawable.intrinsicHeight.toFloat()
                    sW = if (iW > 0) iW else 1f
                    sH = if (iH > 0) iH else 1f
                } else {
                    val currentBmp = BitmapFactory.decodeStream(c.assets.open("gallery/${imageMap[key]}"))
                    animImg.setImageBitmap(currentBmp)
                    val iW = currentBmp.width.toFloat()
                    val iH = currentBmp.height.toFloat()
                    sW = if (iW > 0) iW else 1f
                    sH = if (iH > 0) iH else 1f
                }

                val thumb = thumbViews[key]
                if (thumb != null && thumb.isAttachedToWindow) {
                    val tLoc = IntArray(2)
                    thumb.getLocationOnScreen(tLoc)
                    destX = tLoc[0].toFloat()
                    destY = tLoc[1].toFloat()
                    destW = thumb.width.toFloat()
                    destH = thumb.height.toFloat()
                } else {
                    destX = screenW / 2; destY = screenH / 2; destW = dp(10).toFloat(); destH = dp(10).toFloat()
                }

                val scaleThumb = maxOf(destW / sW, destH / sH)
                targetImgW = sW * scaleThumb
                targetImgH = sH * scaleThumb

                val scaleFull = minOf(screenW / sW, screenH / sH)
                val fullImgW = sW * scaleFull
                val fullImgH = sH * scaleFull

                boxfullimg.layoutParams = FrameLayout.LayoutParams(screenW.toInt(), screenH.toInt()).apply { gravity = Gravity.TOP or Gravity.START }
                boxfullimg.translationX = 0f
                boxfullimg.translationY = 0f
                boxfullimg.scaleX = 1f
                boxfullimg.scaleY = 1f
                animImg.layoutParams = FrameLayout.LayoutParams(fullImgW.toInt(), fullImgH.toInt(), Gravity.CENTER)

                viewPager.visibility = View.INVISIBLE
                boxfullimg.visibility = View.VISIBLE

                initialBoxX = 0f
                initialBoxY = 0f
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (isClosing) return true
                if (isVerticalDrag) {
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - startTouchX
                            val deltaY = event.rawY - startTouchY
                            val newTransY = initialBoxY + deltaY

                            boxfullimg.translationX = initialBoxX + deltaX
                            boxfullimg.translationY = newTransY

                            val dragThreshold = dp(100).toFloat()
                            val progress = minOf(1f, abs(newTransY) / dragThreshold)

                            val currentScale = 1f - (progress * 0.2f)
                            boxfullimg.scaleX = currentScale
                            boxfullimg.scaleY = currentScale

                            bgView.alpha = 1f - progress
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isVerticalDrag = false
                            val currentTransY = boxfullimg.translationY

                            if (abs(currentTransY) >= dp(100)) {
                                executeClose()
                            } else {
                                cancelClose()
                            }
                        }
                    }
                    return true
                }
                return super.onTouchEvent(event)
            }

            fun executeClose() {
                isClosing = true
                var finishedCount = 0
                val totalAnims = 7

                // Dùng 'this' một cách tường minh thông qua biến overlayView
                // để tránh lỗi Unresolved reference khi trình biên dịch đọc mã
                val overlayView = this

                fun onFinished() {
                    finishedCount++
                    if (finishedCount == totalAnims) runCatching { w.removeView(overlayView) }
                }

                SpringAnimation(bgView, DynamicAnimation.ALPHA).apply {
                    spring = SpringForce(0f).apply { stiffness = stiffOut; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                val currentBoxW = boxfullimg.layoutParams.width.toFloat()
                val currentBoxH = boxfullimg.layoutParams.height.toFloat()

                SpringAnimation(FloatValueHolder(currentBoxW)).apply {
                    spring = SpringForce(destW).apply { stiffness = stiffOut; dampingRatio = damp }
                    addUpdateListener { _, value, _ -> val lp = boxfullimg.layoutParams; lp.width = value.toInt(); boxfullimg.layoutParams = lp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(FloatValueHolder(currentBoxH)).apply {
                    spring = SpringForce(destH).apply { stiffness = stiffOut; dampingRatio = damp }
                    addUpdateListener { _, value, _ -> val lp = boxfullimg.layoutParams; lp.height = value.toInt(); boxfullimg.layoutParams = lp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_X).apply {
                    spring = SpringForce(destX).apply { stiffness = stiffOut; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_Y).apply {
                    spring = SpringForce(destY).apply { stiffness = stiffOut; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.SCALE_X).apply { spring = SpringForce(1f).apply { stiffness = stiffOut; dampingRatio = damp } }.start()
                SpringAnimation(boxfullimg, DynamicAnimation.SCALE_Y).apply { spring = SpringForce(1f).apply { stiffness = stiffOut; dampingRatio = damp } }.start()

                val currentImgW = animImg.layoutParams.width.toFloat()
                val currentImgH = animImg.layoutParams.height.toFloat()

                SpringAnimation(FloatValueHolder(currentImgW)).apply {
                    spring = SpringForce(targetImgW).apply { stiffness = stiffOut; dampingRatio = damp }
                    addUpdateListener { _, value, _ -> val lp = animImg.layoutParams; lp.width = value.toInt(); animImg.layoutParams = lp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(FloatValueHolder(currentImgH)).apply {
                    spring = SpringForce(targetImgH).apply { stiffness = stiffOut; dampingRatio = damp }
                    addUpdateListener { _, value, _ -> val lp = animImg.layoutParams; lp.height = value.toInt(); animImg.layoutParams = lp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()
            }

            fun cancelClose() {
                var finishedCount = 0
                val totalAnims = 5

                fun onFinished() {
                    finishedCount++
                    if (finishedCount == totalAnims) {
                        viewPager.visibility = View.VISIBLE
                        boxfullimg.visibility = View.INVISIBLE
                    }
                }

                bgView.animate().alpha(1f).setDuration(100).start()

                SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_X).apply {
                    spring = SpringForce(0f).apply { stiffness = stiffIn; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_Y).apply {
                    spring = SpringForce(0f).apply { stiffness = stiffIn; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.SCALE_X).apply {
                    spring = SpringForce(1f).apply { stiffness = stiffIn; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                SpringAnimation(boxfullimg, DynamicAnimation.SCALE_Y).apply {
                    spring = SpringForce(1f).apply { stiffness = stiffIn; dampingRatio = damp }
                    addEndListener { _, _, _, _ -> onFinished() }
                }.start()

                onFinished()
            }
        }

        fsContainer.addView(bgView)
        fsContainer.addView(boxfullimg)
        fsContainer.addView(viewPager)

        val fsLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            2038,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        runCatching { w.addView(fsContainer, fsLp) }

        // --- 4. KÍCH HOẠT ZOOM-IN BAN ĐẦU ---
        val clickedThumb = thumbViews[imageKeys[startIndex]] ?: return
        val loc = IntArray(2)
        clickedThumb.getLocationOnScreen(loc)

        val startW = clickedThumb.width.toFloat()
        val startH = clickedThumb.height.toFloat()
        val startX = loc[0].toFloat()
        val startY = loc[1].toFloat()

        val imgW = clickedBmp.width.toFloat()
        val imgH = clickedBmp.height.toFloat()

        val scaleStart = maxOf(startW / imgW, startH / imgH)
        val startImgW = imgW * scaleStart
        val startImgH = imgH * scaleStart

        val scaleEnd = minOf(screenW / imgW, screenH / imgH)
        val endImgW = imgW * scaleEnd
        val endImgH = imgH * scaleEnd

        boxfullimg.layoutParams = FrameLayout.LayoutParams(startW.toInt(), startH.toInt()).apply { gravity = Gravity.TOP or Gravity.START }
        boxfullimg.translationX = startX
        boxfullimg.translationY = startY
        animImg.setImageBitmap(clickedBmp)
        animImg.layoutParams = FrameLayout.LayoutParams(startImgW.toInt(), startImgH.toInt(), Gravity.CENTER)

        var openFinishedCount = 0
        fun onOpenAnimFinished() {
            openFinishedCount++
            if (openFinishedCount == 4) {
                viewPager.visibility = View.VISIBLE
                boxfullimg.visibility = View.INVISIBLE
            }
        }

        SpringAnimation(FloatValueHolder(startW)).apply {
            spring = SpringForce(screenW).apply { stiffness = stiffIn; dampingRatio = damp }
            addUpdateListener { _, value, _ ->
                val lp = boxfullimg.layoutParams; lp.width = value.toInt(); boxfullimg.layoutParams = lp
                val ratio = (value - startW) / (screenW - startW)
                bgView.alpha = ratio.coerceIn(0f, 1f)
            }
            addEndListener { _, _, _, _ -> onOpenAnimFinished() }
        }.start()

        SpringAnimation(FloatValueHolder(startH)).apply {
            spring = SpringForce(screenH).apply { stiffness = stiffIn; dampingRatio = damp }
            addUpdateListener { _, value, _ -> val lp = boxfullimg.layoutParams; lp.height = value.toInt(); boxfullimg.layoutParams = lp }
            addEndListener { _, _, _, _ -> onOpenAnimFinished() }
        }.start()

        SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_X).apply {
            spring = SpringForce(0f).apply { stiffness = stiffIn; dampingRatio = damp }
            addEndListener { _, _, _, _ -> onOpenAnimFinished() }
        }.start()

        SpringAnimation(boxfullimg, DynamicAnimation.TRANSLATION_Y).apply {
            spring = SpringForce(0f).apply { stiffness = stiffIn; dampingRatio = damp }
            addEndListener { _, _, _, _ -> onOpenAnimFinished() }
        }.start()

        SpringAnimation(FloatValueHolder(startImgW)).apply {
            spring = SpringForce(endImgW).apply { stiffness = stiffIn; dampingRatio = damp }
            addUpdateListener { _, value, _ -> val lp = animImg.layoutParams; lp.width = value.toInt(); animImg.layoutParams = lp }
        }.start()

        SpringAnimation(FloatValueHolder(startImgH)).apply {
            spring = SpringForce(endImgH).apply { stiffness = stiffIn; dampingRatio = damp }
            addUpdateListener { _, value, _ -> val lp = animImg.layoutParams; lp.height = value.toInt(); animImg.layoutParams = lp }
        }.start()
    }
}