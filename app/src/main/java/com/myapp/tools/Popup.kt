package com.myapp.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.content.edit
import com.myapp.R
import com.myapp.tools.obsidian.EnergyRing
import com.myapp.tools.obsidian.Popup

@Suppress("DEPRECATION")
class Popup(private val c: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var currentOverlay: View? = null
    }
    private val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val p = c.getSharedPreferences("tile_prefs", Context.MODE_PRIVATE)
    private var bar: View? = null; private var pop: View? = null
    private val ip = DecelerateInterpolator(2f)

    @SuppressLint("ClickableViewAccessibility")
    fun setup() {
        destroy()

        // 1. Tạo LayoutParams trước
        val lp = WindowManager.LayoutParams(60, 290, 2038, 776, -3).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 120
            // Tắt animation để update flag tức thì
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
                            if (dx < -30) {
                                triggered = true
                                show()
                            } // Vuốt trái
                            else if (dy > 50 && dy > Math.abs(dx)) { // Vuốt xuống dọc
                                triggered = true
                                (c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(50)
                                c.startActivity(Intent(c, CtsActivity::class.java).addFlags(268435456))
                            }
                        }
                    }
                    // --- PHẦN MỚI: Xử lý Click xuyên thấu ---
                    MotionEvent.ACTION_UP -> {
                        // Nếu nhấc tay mà chưa kích hoạt vuốt -> Là CLICK
                        if (!triggered) {
                            // Bật chế độ xuyên thấu (không nhận cảm ứng)
                            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            try { w.updateViewLayout(this, lp) } catch (e: Exception) {}

                            // Sau 0.5s thì tắt chế độ xuyên thấu (nhận cảm ứng lại)
                            postDelayed({
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                if (isAttachedToWindow) {
                                    try { w.updateViewLayout(this, lp) } catch (e: Exception) {}
                                }
                            }, 500)
                        }
                    }
                    // ----------------------------------------
                }; true
            }
        }

        try { w.addView(v, lp); bar = v } catch (e: Exception) {}
    }

    // [MỚI] Hàm này để Sidebar gọi khi màn hình bật lại
    fun refreshExclusion() {
        bar?.post {
            bar?.systemGestureExclusionRects = listOf(Rect(0, 0, bar?.width ?: 0, bar?.height ?: 0))
        }
    }

    private fun keep(on: Boolean) = bar?.let {
        val lp = it.layoutParams as WindowManager.LayoutParams
        lp.flags = if (on) lp.flags or 128 else lp.flags and 128.inv()
        try {
            w.updateViewLayout(it, lp)
            it.post { it.systemGestureExclusionRects = listOf(Rect(0, 0, it.width, it.height)) }
        } catch (e: Exception) {}
    }

    private fun show() {
        if (pop != null) return

        val root = FrameLayout(c)
        root.apply {
            val save = (c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
            setBackgroundColor(Color.argb(if (save) 255 else 90, 110, 110, 110))
            clipChildren = false
            alpha = 0f
            isClickable = true
            setOnClickListener { close(root) }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            systemUiVisibility = 5894

            addView(createGrid(this))

            addView(TextView(c).apply {
                val intent = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                text = "${(intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f}°C"
                setTextColor(-1)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = FrameLayout.LayoutParams(-2, -2, 81).apply { bottomMargin = 80 }
                translationY = 50f
                animate().translationY(0f).setInterpolator(ip).setDuration(800).start()

                setOnLongClickListener {
                    close(root)
                    showTempOverlay(text.toString())
                    true
                }
            })
        }

        val lp = WindowManager.LayoutParams(-1, -1, 2038, 262404, -3).apply {
            blurBehindRadius = 1
            if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = 1
            dimAmount = 0f
            gravity = Gravity.TOP or Gravity.START
        }
        try { w.addView(root, lp); pop = root; EnergyRing.bringToFront() } catch (e: Exception) {}
        root.post { blur(root, lp, 1, 500); root.animate().alpha(1f).setInterpolator(ip).setDuration(500).start() }
    }

    private fun createGrid(root: FrameLayout): GridLayout {
        val grid = GridLayout(c).apply { columnCount = 4; layoutParams = FrameLayout.LayoutParams(-2, -2, 17); clipChildren = false }
        val (cOn, cOff) = Color.rgb(33, 124, 255) to Color.argb(120, 0, 0, 0)
        fun isDim() = try { Settings.Secure.getInt(c.contentResolver, "reduce_bright_colors_activated") == 1 } catch (e: Exception) { false }
        fun anim(v: View, on: Boolean) = ValueAnimator.ofArgb(if (on) cOff else cOn, if (on) cOn else cOff).apply {
            duration = 200; addUpdateListener { (v.background as GradientDrawable).setColor(it.animatedValue as Int) }
        }.start()

        val items = listOf<Pair<Int, () -> Unit>>(
            R.drawable.scan to { exec("com.google.android.gms", "com.google.android.gms.mlkit.barcode.v2.ScannerActivity"); close(root) },
            R.drawable.lens to { exec("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity", true); close(root) },
            R.drawable.quickshare to { exec("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity", action = Intent.ACTION_MAIN); close(root) },
            R.drawable.screenon to { p.edit { putBoolean("on", !p.getBoolean("on", false)) }; keep(p.getBoolean("on", false)); anim(grid.getChildAt(3), p.getBoolean("on", false)) },
            R.drawable.dim to { runCatching { Settings.Secure.putInt(c.contentResolver, "reduce_bright_colors_activated", if (isDim()) 0 else 1) }; p.edit { putBoolean("dim", isDim()) }; anim(grid.getChildAt(4), isDim()) },
            R.drawable.light to { close(root); p.edit { putBoolean("on", true) }; keep(true); FakeLock(c).lock { p.edit { putBoolean("on", false) }; keep(false) } },
            R.drawable.clean to { Cleaner.clean(); close(root) },
            R.drawable.obsidian1 to { Popup(c, root) { close(root) }.checkAndShowSync(grid) }
        )

        items.forEachIndexed { i, (ic, fn) ->
            grid.addView(FrameLayout(c).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 180; height = 180; setMargins(25, 35, 25, 35) }
                val isOn = if (ic == R.drawable.screenon) p.getBoolean("on", false) else if (ic == R.drawable.dim) isDim() else false
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isOn) cOn else cOff) }
                addView(ImageView(c).apply { setImageResource(ic); setColorFilter(-1); layoutParams = FrameLayout.LayoutParams(75, 75, 17) })
                setOnClickListener { fn() }
                alpha = 0f; translationY = -250f; scaleX = 0.5f; scaleY = 0.1f
                animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(2f)).setDuration(600).setStartDelay((i / 4 * 100).toLong()).start()
            })
        }
        return grid
    }

    private fun close(v: View) {
        if (pop == null) return
        val lp = v.layoutParams as WindowManager.LayoutParams
        blur(v, lp, lp.blurBehindRadius, 1)
        v.animate().alpha(0f).setInterpolator(ip).setDuration(250).withEndAction { v.visibility = View.GONE; runCatching { w.removeView(v) }; pop = null }.start()
    }

    private fun blur(v: View, lp: WindowManager.LayoutParams, f: Int, t: Int) = ValueAnimator.ofInt(f, t).apply {
        duration = 500; interpolator = ip
        addUpdateListener { if (v.isAttachedToWindow) runCatching { lp.blurBehindRadius = it.animatedValue as Int; w.updateViewLayout(v, lp) } }
    }.start()

    private fun exec(pkg: String, cls: String, hist: Boolean = false, action: String? = null) = runCatching {
        c.startActivity(Intent().setClassName(pkg, cls).addFlags(268435456).apply { if (hist) addFlags(1048576); if (action != null) setAction(action) })
    }

    fun destroy() { bar?.let { if (it.isAttachedToWindow) w.removeView(it) }; pop?.let { if (it.isAttachedToWindow) w.removeView(it) } }

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
            setShadowLayer(5f, 0f, 0f, -16777216)
            var x = 0f; var y = 0f; var ix = 0; var iy = 0
            val longClick = Runnable { (c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(50); runCatching { w.removeView(this) }; currentOverlay = null }

            setOnTouchListener { _, e ->
                val lp = layoutParams as WindowManager.LayoutParams
                when (e.action) {
                    0 -> { x = e.rawX; y = e.rawY; ix = lp.x; iy = lp.y; h.postDelayed(longClick, 500) }
                    2 -> if (Math.abs(e.rawX - x) > 10 || Math.abs(e.rawY - y) > 10) {
                        h.removeCallbacks(longClick)
                        lp.x = ix + (e.rawX - x).toInt(); lp.y = iy - (e.rawY - y).toInt()
                        runCatching { w.updateViewLayout(this, lp) }
                    }
                    else -> h.removeCallbacks(longClick)
                }; true
            }
        }
        val lp = WindowManager.LayoutParams(-2, -2, 2038, 520, -3).apply {
            gravity = 81; y = 20; if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = 1
        }
        runCatching { w.addView(v, lp); currentOverlay = v }
    }
}