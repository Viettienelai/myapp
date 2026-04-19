package com.myapp.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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

        val lp = WindowManager.LayoutParams(dp(20), dp(100), 2038, 776, -3).apply {
            gravity = Gravity.TOP or Gravity.END
            y = dp(236)
            windowAnimations = 0
        }

        val v = FrameLayout(c).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
            }

            var sx = 0f; var sy = 0f; var triggered = false

            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; triggered = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (!triggered) {
                            if (dx < -dp(10)) {
                                triggered = true
                                show()
                            }
                            else if (dy > dp(16) && dy > abs(dx)) {
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
            bar?.systemGestureExclusionRects = listOf(Rect(0, 0, bar?.width ?: 0, bar?.height ?: 0))
        }
    }

    private fun keep(on: Boolean) = bar?.let {
        val lp = it.layoutParams as WindowManager.LayoutParams
        lp.flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        try {
            w.updateViewLayout(it, lp)
            it.post {
                it.systemGestureExclusionRects = listOf(Rect(0, 0, it.width, it.height))
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun show() {
        if (pop != null) return

        val windowContainer = FrameLayout(c).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val root = FrameLayout(c)
        root.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(32).toFloat()
                setColor(Color.rgb(40, 40, 40))
                setStroke(dp(1), Color.argb(50,200, 200, 200))
            }

            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            clipChildren = false
            isClickable = true

            scaleX = 0.4f
            scaleY = 0.2f

            systemUiVisibility = 5894

            val container = LinearLayout(c).apply {
                tag = "main_container"
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
                    setMargins(dp(8), dp(18), dp(8), dp(8))
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

        root.layoutParams = FrameLayout.LayoutParams(dp(64), FrameLayout.LayoutParams.WRAP_CONTENT)
        windowContainer.addView(root)

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

        val flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val onScreenX = -dp(5).toFloat()
        val offScreenX = -dp(70).toFloat()

        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 2038, flags, PixelFormat.TRANSLUCENT).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = offScreenX.toInt()
            windowAnimations = 0
        }

        try {
            w.addView(windowContainer, lp)
            pop = windowContainer
            EnergyRing.bringToFront()

            val springAnimX = SpringAnimation(FloatValueHolder(offScreenX))
            springAnimX.spring = SpringForce(onScreenX).apply {
                dampingRatio = 0.7f
                stiffness = 200f
            }
            springAnimX.addUpdateListener { _, value, _ ->
                if (windowContainer.isAttachedToWindow) {
                    lp.x = value.toInt()
                    runCatching { w.updateViewLayout(windowContainer, lp) }
                }
            }
            springAnimX.start()

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
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        val cOn = Color.rgb(33, 124, 255)
        val cOff = Color.rgb(0, 0, 0)

        fun isDim() = try { Settings.Secure.getInt(c.contentResolver, "reduce_bright_colors_activated") == 1 } catch (_: Exception) { false }

        fun anim(v: View, on: Boolean) = ValueAnimator.ofArgb(if (on) cOff else cOn, if (on) cOn else cOff).apply {
            duration = 200; addUpdateListener { (v.background as GradientDrawable).setColor(it.animatedValue as Int) }
        }.start()

        val items: List<Pair<Int, () -> Unit>> = listOf(
            R.drawable.scan to { exec("com.google.android.gms", "com.google.android.gms.mlkit.barcode.v2.ScannerActivity"); close(); Unit },
            R.drawable.lens to { exec("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity", true); close(); Unit },
            R.drawable.quickshare to { exec("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity", action = Intent.ACTION_MAIN); close(); Unit },
            R.drawable.screenon to { p.edit { putBoolean("on", !p.getBoolean("on", false)) }; keep(p.getBoolean("on", false)); anim(list.getChildAt(3), p.getBoolean("on", false)); Unit },
            R.drawable.dim to { runCatching { Settings.Secure.putInt(c.contentResolver, "reduce_bright_colors_activated", if (isDim()) 0 else 1) }; p.edit { putBoolean("dim", isDim()) }; anim(list.getChildAt(4), isDim()); Unit },
            R.drawable.light to { close(); p.edit { putBoolean("on", true) }; keep(true); FakeLock(c).lock { p.edit { putBoolean("on", false) }; keep(false) }; Unit },
            R.drawable.clean to { Cleaner.clean(); close(); Unit },
            R.drawable.obsidian1 to {
                val windowContainer = pop as? FrameLayout
                val rootLp = root.layoutParams as FrameLayout.LayoutParams

                val currentHeight = root.height
                val targetHeight = dp(322)

                if (currentHeight != targetHeight && currentHeight > 0) {
                    tempText.animate().alpha(0f).setDuration(300).start()
                    divider.animate().alpha(0f).setDuration(300).start()

                    val heightSpring = SpringAnimation(FloatValueHolder(currentHeight.toFloat()))
                    heightSpring.spring = SpringForce(targetHeight.toFloat()).apply {
                        dampingRatio = 0.6f
                        stiffness = SpringForce.STIFFNESS_LOW
                    }

                    heightSpring.addUpdateListener { _, value, _ ->
                        rootLp.height = value.toInt()
                        root.layoutParams = rootLp

                        if (windowContainer?.isAttachedToWindow == true) {
                            runCatching { w.updateViewLayout(windowContainer, windowContainer.layoutParams) }
                        }
                    }
                    heightSpring.start()
                }

                Popup(c, root) { close() }.checkAndShowSync(list)
                Unit
            },
            R.drawable.card to {
                val windowContainer = pop as? FrameLayout
                if (windowContainer != null) {
                    Gallery(c, w).expand(root, windowContainer)
                }
                Unit
            }
        )

        items.forEachIndexed { _, (ic, fn) ->
            list.addView(FrameLayout(c).apply {
                val bMargin = dp(8)
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
        val root = windowContainer.getChildAt(0) as FrameLayout
        val lp = windowContainer.layoutParams as WindowManager.LayoutParams
        val offScreenX = -dp(200).toFloat()

        val rootLp = root.layoutParams as FrameLayout.LayoutParams
        val currentWidth = if (root.width > 0) root.width else dp(64)
        val currentHeight = if (root.height > 0) root.height else dp(200)

        if (currentWidth > dp(64)) {
            val widthSpring = SpringAnimation(FloatValueHolder(currentWidth.toFloat()))
            widthSpring.spring = SpringForce(dp(64).toFloat()).apply { dampingRatio = 1f; stiffness = 400f }
            widthSpring.addUpdateListener { _, value, _ ->
                rootLp.width = value.toInt()
                root.layoutParams = rootLp
                if (windowContainer.isAttachedToWindow) {
                    runCatching { w.updateViewLayout(windowContainer, lp) }
                }
            }
            widthSpring.start()

            val heightSpring = SpringAnimation(FloatValueHolder(currentHeight.toFloat()))
            heightSpring.spring = SpringForce(dp(64).toFloat()).apply { dampingRatio = 1f; stiffness = 400f }
            heightSpring.addUpdateListener { _, value, _ ->
                rootLp.height = value.toInt()
                root.layoutParams = rootLp
            }
            heightSpring.start()

            val galleryContainer = root.findViewWithTag<View>("gallery_container")
            val mainContainer = root.findViewWithTag<View>("main_container")

            galleryContainer?.let { gc ->
                gc.animate().alpha(0f).setDuration(200).withEndAction {
                    root.removeView(gc)
                }.start()
            }

            mainContainer?.apply {
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(200).start()
            }

            val bg = root.background as? GradientDrawable
            bg?.let {
                // Thay đổi để bo góc từ 20dp về lại 32dp khi đóng Gallery
                ValueAnimator.ofFloat(dp(20).toFloat(), dp(32).toFloat()).apply {
                    duration = 200
                    addUpdateListener { animator -> it.cornerRadius = animator.animatedValue as Float }
                    start()
                }
                ValueAnimator.ofArgb(Color.BLACK, Color.rgb(40, 40, 40)).apply {
                    duration = 200
                    addUpdateListener { animator -> it.setColor(animator.animatedValue as Int) }
                    start()
                }
            }
        }

        SpringAnimation(root, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(0.4f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
            start()
        }
        SpringAnimation(root, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(0.2f).apply { dampingRatio = 0.7f; stiffness = SpringForce.STIFFNESS_LOW }
            start()
        }

        windowContainer.postDelayed({
            val springAnimX = SpringAnimation(FloatValueHolder(lp.x.toFloat()))
            springAnimX.spring = SpringForce(offScreenX).apply {
                dampingRatio = 0.7f
                stiffness = SpringForce.STIFFNESS_LOW
            }

            springAnimX.addUpdateListener { _, value, _ ->
                if (windowContainer.isAttachedToWindow) {
                    lp.x = value.toInt()
                    runCatching { w.updateViewLayout(windowContainer, lp) }
                }
            }

            springAnimX.addEndListener { _, _, _, _ ->
                windowContainer.visibility = View.GONE
                runCatching { w.removeView(windowContainer) }
                pop = null
            }

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
            y = dp(7)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { w.addView(v, lp); currentOverlay = v }
    }
}