package com.myapp.tools.obsidian

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min

@SuppressLint("StaticFieldLeak")
object EnergyRing {
    private var view: RingView? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var layoutParams: WindowManager.LayoutParams? = null

    // Hàm quy đổi nội bộ
    private fun dp(px: Int, ctx: Context): Int = (ctx.resources.displayMetrics.density * px).toInt()

    private const val COLOR_SYNC = 0xFFA079FF.toInt()
    private const val COLOR_SUCCESS = 0xFF24B232.toInt()

    private const val SCANNER_COLOR_1 = 0xFFA079FF.toInt()
    private const val SCANNER_COLOR_2 = 0xFF24B232.toInt()

    fun show(ctx: Context) {
        handler.post {
            if (view != null) return@post
            val context = ctx.applicationContext
            wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            view = RingView(context).apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }

            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                height = dp(100, context) // 300px -> ~100dp
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            try {
                wm?.addView(view, layoutParams)
                view?.startScanning()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun bringToFront() {
        handler.post {
            if (view != null && view?.isAttachedToWindow == true && wm != null) {
                try {
                    wm?.removeView(view)
                    wm?.addView(view, layoutParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setProgress(percent: Int) {
        handler.post { view?.updateProgress(percent) }
    }

    fun success() {
        handler.post { view?.animateSuccess { removeView() } }
    }

    fun hide() {
        handler.post { removeView() }
    }

    private fun removeView() {
        if (view != null && view?.isAttachedToWindow == true) {
            try { wm?.removeView(view) } catch (_: Exception) {}
            view = null
        }
    }

    private class RingView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f
        }

        private var progress = 0f
        private var targetRadiusOffset = 8f

        private var radiusScanner = 8f
        private var radiusRing = -20f

        private var currentColor = COLOR_SYNC

        private var state = State.SCANNING

        enum class State { SCANNING, TRANSITION, SYNCING, SUCCESS }

        private var scanAngle1 = 0f
        private var scanAngle2 = 0f
        private var scannerRotationAnim: ValueAnimator? = null
        private var progressAnim: ValueAnimator? = null

        fun startScanning() {
            state = State.SCANNING
            radiusScanner = targetRadiusOffset
            radiusRing = -20f

            scannerRotationAnim = ValueAnimator.ofFloat(0f, 720f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val v = it.animatedValue as Float
                    scanAngle1 = v
                    scanAngle2 = -v * 2f
                    invalidate()
                }
                start()
            }
        }

        fun updateProgress(p: Int) {
            if (state == State.SUCCESS) return

            if (state == State.SCANNING) {
                state = State.TRANSITION
                radiusRing = targetRadiusOffset

                val shrinkScannerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val fraction = it.animatedValue as Float
                        radiusScanner = targetRadiusOffset * (1 - fraction)
                        invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (state != State.SUCCESS) {
                                state = State.SYNCING
                                scannerRotationAnim?.cancel()
                            }
                        }
                    })
                }
                shrinkScannerAnim.start()
            }

            val target = p.toFloat()
            if (progressAnim?.isRunning == true) progressAnim?.cancel()

            progressAnim = ValueAnimator.ofFloat(progress, target).apply {
                duration = 1000
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun animateSuccess(onEnd: () -> Unit) {
            scannerRotationAnim?.cancel()
            progressAnim?.cancel()

            state = State.SUCCESS
            paint.style = Paint.Style.FILL
            progress = 100f
            radiusRing = targetRadiusOffset

            val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), COLOR_SYNC, COLOR_SUCCESS).apply {
                duration = 700
                addUpdateListener { currentColor = it.animatedValue as Int; invalidate() }
            }

            val expandAnim = ValueAnimator.ofFloat(radiusRing, 25f).apply {
                duration = 700
                interpolator = OvershootInterpolator(2.5f)
                addUpdateListener { radiusRing = it.animatedValue as Float; invalidate() }
            }

            val shrinkAnim = ValueAnimator.ofFloat(25f, -100f).apply {
                duration = 400
                startDelay = 700
                interpolator = AccelerateInterpolator()
                addUpdateListener {
                    radiusRing = it.animatedValue as Float
                    if (it.animatedFraction > 0.2f) alpha = 1f - it.animatedFraction
                    invalidate()
                }
            }

            AnimatorSet().apply {
                play(expandAnim).with(colorAnim)
                play(shrinkAnim).after(expandAnim)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { onEnd() }
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            var cx = width / 2f
            var cy = 60f
            var baseRadius = 30f

            rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let {
                cx = it.exactCenterX()
                cy = it.exactCenterY()
                baseRadius = min(it.width(), it.height()) / 2f
            }

            if (state == State.SCANNING || state == State.TRANSITION) {
                val rScan = baseRadius + radiusScanner
                if (radiusScanner > 0.5f) {
                    val rectScan = RectF(cx - rScan, cy - rScan, cx + rScan, cy + rScan)
                    paint.style = Paint.Style.STROKE

                    paint.color = SCANNER_COLOR_1
                    canvas.drawArc(rectScan, scanAngle1, 100f, false, paint)

                    paint.color = SCANNER_COLOR_2
                    canvas.drawArc(rectScan, scanAngle2 + 180f, 80f, false, paint)
                }
            }

            if (radiusRing > -10 || state == State.SUCCESS) {
                val rRing = baseRadius + radiusRing
                if (rRing > 0) {
                    paint.color = currentColor
                    paint.alpha = 255

                    if (state == State.SUCCESS) {
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(cx, cy, rRing, paint)
                    } else {
                        paint.style = Paint.Style.STROKE
                        val rectRing = RectF(cx - rRing, cy - rRing, cx + rRing, cy + rRing)
                        canvas.drawArc(rectRing, 270f, (360 * progress / 100), false, paint)
                    }
                }
            }
        }
    }
}