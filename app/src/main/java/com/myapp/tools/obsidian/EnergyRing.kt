package com.myapp.tools.obsidian

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
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

    // Bảng màu
    private const val COLOR_SYNC = 0xFFA079FF.toInt()     // Tím (Syncing)
    private const val COLOR_SUCCESS = 0xFF24B232.toInt()  // Xanh lá (Done)

    // Scanner Colors
    private const val SCANNER_COLOR_1 = 0xFFA079FF.toInt() // Tím
    private const val SCANNER_COLOR_2 = 0xFF24B232.toInt() // Xanh lá

    fun show(ctx: Context) {
        handler.post {
            if (view != null) return@post
            val context = ctx.applicationContext
            wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            view = RingView(context).apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }

            val type = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

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
                height = 300
                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            try {
                wm?.addView(view, layoutParams)
                view?.startScanning()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- NEW METHOD: Đưa Ring lên trên cùng ---
    fun bringToFront() {
        handler.post {
            if (view != null && view?.isAttachedToWindow == true && wm != null) {
                try {
                    // Gỡ ra và thêm lại để reset Z-Order lên cao nhất
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

        // --- CẤU HÌNH ---
        private var progress = 0f
        private var targetRadiusOffset = 8f  // Kích thước chuẩn

        // Hai bán kính riêng biệt
        private var radiusScanner = 8f  // Scanner sẽ thu nhỏ dần
        private var radiusRing = -20f   // Ring chính (bắt đầu ẩn)

        private var currentColor = COLOR_SYNC

        // --- TRẠNG THÁI ---
        private var state = State.SCANNING

        enum class State {
            SCANNING,   // Xoay 2 vòng tròn
            TRANSITION, // Scanner thu nhỏ
            SYNCING,    // Chạy % Ring
            SUCCESS     // Biến hình xanh lá
        }

        // --- ANIMATORS ---
        private var scanAngle1 = 0f
        private var scanAngle2 = 0f
        private var scannerRotationAnim: ValueAnimator? = null
        private var progressAnim: ValueAnimator? = null

        fun startScanning() {
            state = State.SCANNING
            radiusScanner = targetRadiusOffset
            radiusRing = -20f

            // Animation xoay vô tận (Nhanh: 720 độ/2s)
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

            // --- KÍCH HOẠT CHUYỂN CẢNH ---
            if (state == State.SCANNING) {
                state = State.TRANSITION

                // Ring chính: Hiện ra NGAY LẬP TỨC tại vị trí chuẩn (không scale từ 0 nữa)
                radiusRing = targetRadiusOffset

                // Scanner: Thu nhỏ dần từ 8 -> 0 rồi biến mất
                val shrinkScannerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val fraction = it.animatedValue as Float

                        // Thu nhỏ bán kính Scanner
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

            // --- UPDATE PROGRESS ---
            val target = p.toFloat()
            if (progressAnim?.isRunning == true) progressAnim?.cancel()

            progressAnim = ValueAnimator.ofFloat(progress, target).apply {
                duration = 300
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
            paint.style = Paint.Style.FILL // Đặc ruột
            progress = 100f

            // Đảm bảo Ring ở kích thước chuẩn trước khi phình to
            radiusRing = targetRadiusOffset

            // 1. Đổi màu Tím -> Xanh
            val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), COLOR_SYNC, COLOR_SUCCESS).apply {
                duration = 700
                addUpdateListener { currentColor = it.animatedValue as Int; invalidate() }
            }

            // 2. Phình to (Expand) -> SỬA THÀNH 25f
            val expandAnim = ValueAnimator.ofFloat(radiusRing, 25f).apply {
                duration = 700
                interpolator = OvershootInterpolator(2.5f)
                addUpdateListener { radiusRing = it.animatedValue as Float; invalidate() }
            }

            // 3. Thu nhỏ biến mất (Shrink)
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

            if (Build.VERSION.SDK_INT >= 28) {
                rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let {
                    cx = it.exactCenterX()
                    cy = it.exactCenterY()
                    baseRadius = min(it.width(), it.height()) / 2f
                }
            }

            // --- 1. VẼ SCANNER (Chỉ vẽ khi đang Scan hoặc đang Thu nhỏ) ---
            if (state == State.SCANNING || state == State.TRANSITION) {
                val rScan = baseRadius + radiusScanner
                // Chỉ vẽ nếu bán kính Scanner còn dương
                if (radiusScanner > 0.5f) {
                    val rectScan = RectF(cx - rScan, cy - rScan, cx + rScan, cy + rScan)
                    paint.style = Paint.Style.STROKE

                    // Cung 1
                    paint.color = SCANNER_COLOR_1
                    canvas.drawArc(rectScan, scanAngle1, 100f, false, paint)

                    // Cung 2
                    paint.color = SCANNER_COLOR_2
                    // Opacity gốc thấp hơn kết hợp với Fade out
                    canvas.drawArc(rectScan, scanAngle2 + 180f, 80f, false, paint)
                }
            }

            // --- 2. VẼ MAIN RING ---
            // Vẽ khi đã qua giai đoạn Scan (radiusRing > -10) hoặc đang Success
            if (radiusRing > -10 || state == State.SUCCESS) {
                val rRing = baseRadius + radiusRing
                if (rRing > 0) {
                    paint.color = currentColor
                    paint.alpha = 255 // Ring chính luôn rõ nét

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