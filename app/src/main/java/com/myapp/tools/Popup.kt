package com.myapp.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.content.edit
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.myapp.R
import com.myapp.tools.obsidian.EnergyRing
import com.myapp.tools.obsidian.Popup
import kotlin.math.abs

@Suppress("DEPRECATION")
class Popup(private val c: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var currentOverlay: View? = null
    }
    private val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val p = c.getSharedPreferences("tile_prefs", Context.MODE_PRIVATE)
    private var bar: View? = null; private var pop: View? = null

    private fun dp(px: Int): Int = (c.resources.displayMetrics.density * px).toInt()

    @SuppressLint("ClickableViewAccessibility")
    fun setup() {
        destroy()

        val lp = WindowManager.LayoutParams(60, 300, 2038, 776, -3).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 710
            windowAnimations = 0
        }

        val v = FrameLayout(c).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
                }
            }

            var sx = 0f; var sy = 0f; var triggered = false

            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; triggered = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (!triggered) {
                            if (dx < -30) {
                                triggered = true
                                show()
                            }
                            else if (dy > 50 && dy > abs(dx)) {
                                triggered = true
                                (c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(50)
                                c.startActivity(Intent(c, CtsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!triggered) {
                            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            try { w.updateViewLayout(this, lp) } catch (_: Exception) {}

                            postDelayed({
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                if (isAttachedToWindow) {
                                    try { w.updateViewLayout(this, lp) } catch (_: Exception) {}
                                }
                            }, 500)
                        }
                    }
                }; true
            }
        }

        try { w.addView(v, lp); bar = v } catch (_: Exception) {}
    }

    fun refreshExclusion() {
        bar?.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bar?.systemGestureExclusionRects = listOf(Rect(0, 0, bar?.width ?: 0, bar?.height ?: 0))
            }
        }
    }

    private fun keep(on: Boolean) = bar?.let {
        val lp = it.layoutParams as WindowManager.LayoutParams
        lp.flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        try {
            w.updateViewLayout(it, lp)
            it.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.systemGestureExclusionRects = listOf(Rect(0, 0, it.width, it.height))
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun show() {
        if (pop != null) return

        // Wrapper bọc ngoài để dự phòng không gian cho hiệu ứng Scale nhún nảy (chống Clipping)
        val windowContainer = FrameLayout(c).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val root = FrameLayout(c)
        root.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(60).toFloat()
                setColor(Color.rgb(40, 40, 40))
            }

            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            clipChildren = false
            isClickable = true

            // Đặt kích thước siêu nhỏ lúc khởi tạo chờ Scale
            scaleX = 0.4f
            scaleY = 0.2f

            setPadding(dp(8), dp(8), dp(8), dp(8))
            systemUiVisibility = 5894

            val container = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(-1, -2)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val tempText = TextView(c).apply {
                val intent = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                text = "${(intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f}°"
                setTextColor(-1)
                textSize = 12f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(10)
                    bottomMargin = dp(8)
                }

                setOnClickListener {
                    close()
                    if (currentOverlay != null) {
                        runCatching { w.removeView(currentOverlay) }
                        currentOverlay = null
                    } else {
                        showTempOverlay(text.toString())
                    }
                }
            }

            val divider = View(c).apply {
                setBackgroundColor(Color.argb(80, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(1)).apply {
                    topMargin = 0
                    bottomMargin = dp(8)
                }
            }

            container.addView(tempText)
            container.addView(divider)
            container.addView(createList(root, tempText, divider))
            addView(container)
        }

        // Bắt sự kiện bấm ra ngoài (Out bounds) hoặc bấm vùng viền vô hình
        windowContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                close()
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                root.getHitRect(rect)
                if (!rect.contains(event.x.toInt(), event.y.toInt())) {
                    close()
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Đặt Toolbar vào trong hộp dự phòng kích thước
        root.layoutParams = FrameLayout.LayoutParams(dp(64), FrameLayout.LayoutParams.WRAP_CONTENT)
        windowContainer.addView(root)

        val flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        // Gravity.END: x = khoảng cách từ viền phải cửa sổ tới viền phải màn hình
        // Do Window đã cộng thêm 20dp padding, để toolbar cách mép phải màn hình 12dp: x = 12 - 20 = -8dp
        val onScreenX = -dp(5).toFloat()
        val offScreenX = -dp(70).toFloat() // Xa tít mép bên ngoài

        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 2038, flags, PixelFormat.TRANSLUCENT).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = offScreenX.toInt()
            windowAnimations = 0
        }

        try {
            w.addView(windowContainer, lp)
            pop = windowContainer
            EnergyRing.bringToFront()

            // 1. Trượt (Translate) vút vào NGAY LẬP TỨC
            val springAnimX = SpringAnimation(FloatValueHolder(offScreenX))
            springAnimX.spring = SpringForce(onScreenX).apply {
                dampingRatio = 0.7f
                stiffness = 200f
            }
            springAnimX.addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                if (windowContainer.isAttachedToWindow) {
                    lp.x = value.toInt()
                    runCatching { w.updateViewLayout(windowContainer, lp) }
                }
            })
            springAnimX.start()

            // 2. Scale bung lụa (DELAY 100MS so với di chuyển)
            windowContainer.postDelayed({
                SpringAnimation(root, DynamicAnimation.SCALE_X).apply {
                    spring = SpringForce(1f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
                    start()
                }
                SpringAnimation(root, DynamicAnimation.SCALE_Y).apply {
                    spring = SpringForce(1f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
                    start()
                }
            }, 50)

        } catch (_: Exception) {}
    }

    private fun createList(root: FrameLayout, tempText: TextView, divider: View): LinearLayout {
        val list = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            clipChildren = false
        }
        val cOn = Color.rgb(33, 124, 255)
        val cOff = Color.rgb(0, 0, 0)

        fun isDim() = try { Settings.Secure.getInt(c.contentResolver, "reduce_bright_colors_activated") == 1 } catch (_: Exception) { false }

        fun anim(v: View, on: Boolean) = ValueAnimator.ofArgb(if (on) cOff else cOn, if (on) cOn else cOff).apply {
            duration = 200; addUpdateListener { (v.background as GradientDrawable).setColor(it.animatedValue as Int) }
        }.start()

        val items = listOf(
            R.drawable.scan to { exec("com.google.android.gms", "com.google.android.gms.mlkit.barcode.v2.ScannerActivity"); close() },
            R.drawable.lens to { exec("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity", true); close() },
            R.drawable.quickshare to { exec("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity", action = Intent.ACTION_MAIN); close() },
            R.drawable.screenon to { p.edit { putBoolean("on", !p.getBoolean("on", false)) }; keep(p.getBoolean("on", false)); anim(list.getChildAt(3), p.getBoolean("on", false)) },
            R.drawable.dim to { runCatching { Settings.Secure.putInt(c.contentResolver, "reduce_bright_colors_activated", if (isDim()) 0 else 1) }; p.edit { putBoolean("dim", isDim()) }; anim(list.getChildAt(4), isDim()) },
            R.drawable.light to { close(); p.edit { putBoolean("on", true) }; keep(true); FakeLock(c).lock { p.edit { putBoolean("on", false) }; keep(false) } },
            R.drawable.clean to { Cleaner.clean(); close() },
            R.drawable.obsidian1 to {
                val windowContainer = pop as? FrameLayout
                val rootLp = root.layoutParams as FrameLayout.LayoutParams

                val currentHeight = root.height
                val targetHeight = dp(322)

                // Kiểm tra nếu đang khác kích thước đích thì mới chạy hiệu ứng thu đổi chiều cao
                if (currentHeight != targetHeight && currentHeight > 0) {
                    tempText.animate().alpha(0f).setDuration(300).start()
                    divider.animate().alpha(0f).setDuration(300).start()

                    // Diễn hoạt thẳng chiều cao qua Frame-by-frame giúp WindowManager cập nhật đều đặn, không bị clipping
                    ValueAnimator.ofInt(currentHeight, targetHeight).apply {
                        duration = 200
                        addUpdateListener { anim ->
                            rootLp.height = anim.animatedValue as Int
                            root.layoutParams = rootLp

                            if (windowContainer?.isAttachedToWindow == true) {
                                runCatching { w.updateViewLayout(windowContainer, windowContainer.layoutParams) }
                            }
                        }
                    }.start()
                }

                // Gọi checkAndShowSync
                Popup(c, root) { close() }.checkAndShowSync(list)
            }
        )

        items.forEachIndexed { index, (ic, fn) ->
            list.addView(FrameLayout(c).apply {
                val bMargin = if (index == items.size - 1) 0 else dp(8)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, 0, bMargin) }

                val isOn = if (ic == R.drawable.screenon) p.getBoolean("on", false) else if (ic == R.drawable.dim) isDim() else false
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isOn) cOn else cOff) }

                addView(ImageView(c).apply { setImageResource(ic); setColorFilter(-1); layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER) })
                setOnClickListener { fn() }
            })
        }
        return list
    }

    private fun close() {
        if (pop == null) return

        val windowContainer = pop as FrameLayout
        val root = windowContainer.getChildAt(0)
        val lp = windowContainer.layoutParams as WindowManager.LayoutParams
        val offScreenX = -dp(200).toFloat()

        // 1. Scale thu nhỏ về hạt bụi NGAY LẬP TỨC
        SpringAnimation(root, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(0.4f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
            start()
        }
        SpringAnimation(root, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(0.2f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
            start()
        }

        // 2. Trượt vút ra ngoài (DELAY 100MS so với Scale down)
        windowContainer.postDelayed({
            val springAnimX = SpringAnimation(FloatValueHolder(lp.x.toFloat()))
            springAnimX.spring = SpringForce(offScreenX).apply {
                dampingRatio = 0.7f
                stiffness = SpringForce.STIFFNESS_LOW
            }

            springAnimX.addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                if (windowContainer.isAttachedToWindow) {
                    lp.x = value.toInt()
                    runCatching { w.updateViewLayout(windowContainer, lp) }
                }
            })

            // Khi trượt khuất bóng rồi thì dọn rác
            springAnimX.addEndListener(DynamicAnimation.OnAnimationEndListener { _, _, _, _ ->
                windowContainer.visibility = View.GONE
                runCatching { w.removeView(windowContainer) }
                pop = null
            })

            springAnimX.start()
        }, 100)
    }

    private fun exec(pkg: String, cls: String, hist: Boolean = false, action: String? = null) = runCatching {
        c.startActivity(Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { if (hist) addFlags(
            Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY); if (action != null) setAction(action) })
    }

    fun destroy() { bar?.let { if (it.isAttachedToWindow) w.removeView(it) }; pop?.let { if (it.isAttachedToWindow) w.removeView(it) } }

    @SuppressLint("SetTextI18n")
    private fun showTempOverlay(t: String) {
        currentOverlay?.let { runCatching { w.removeView(it) } }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val v = object : TextView(c) {
            val run = object : Runnable {
                override fun run() {
                    val temp = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", 0) ?: 0
                    text = "${temp / 10f}°"; h.postDelayed(this, 1000)
                }
            }
            override fun onAttachedToWindow() { super.onAttachedToWindow(); h.post(run) }
            override fun onDetachedFromWindow() { super.onDetachedFromWindow(); h.removeCallbacks(run) }
        }.apply {
            text = t; setTextColor(-1); textSize = 9f; typeface = Typeface.DEFAULT_BOLD
            setPadding(5,0,5,0)
            setShadowLayer(5f, 0f, 0f, -16777216)

            setOnClickListener {
                runCatching { w.removeView(this) }
                currentOverlay = null
            }
        }
        val lp = WindowManager.LayoutParams(-2, -2, 2038, 520, -3).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 20
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { w.addView(v, lp); currentOverlay = v }
    }
}