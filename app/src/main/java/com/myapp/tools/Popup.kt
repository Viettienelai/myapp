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
    private val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val p = c.getSharedPreferences("tile_prefs", Context.MODE_PRIVATE)
    private var bar: View? = null; private var pop: View? = null
    private val ip = DecelerateInterpolator(2f)

    @SuppressLint("ClickableViewAccessibility")
    fun setup() {
        destroy()
        val v = FrameLayout(c).apply {
            setBackgroundColor(Color.argb(50, 0, 0, 255)) // Yêu cầu: Màu BLUE
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.post { v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height)) }
            }
            var x = 0f; var d = false
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { x = e.rawX; d = false }
                    MotionEvent.ACTION_MOVE -> if (!d && e.rawX - x < -20) { d = true; show() }
                }; true
            }
        }
        // Flags: NotFocusable(8) | LayoutInScreen(256) | LayoutNoLimits(512) = 776
        val lp = WindowManager.LayoutParams(40, 400, 2038, 776, -3).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 0 // Yêu cầu: Sát mép trên
        }
        w.addView(v, lp); bar = v
    }

    private fun keep(on: Boolean) = bar?.let {
        val lp = it.layoutParams as WindowManager.LayoutParams
        lp.flags = if (on) lp.flags or 128 else lp.flags and 128.inv()
        w.updateViewLayout(it, lp)
    }

    private fun show() {
        if (pop != null) return
        val root = FrameLayout(c).apply {
            val save = (c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
            setBackgroundColor(Color.argb(if (save) 255 else 90, 110, 110, 110))
            clipChildren = false
            alpha = 0f; isClickable = true; setOnClickListener { close(this) }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // --- MỚI: Yêu cầu View nội dung tràn xuống dưới System Bars ---
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            addView(createGrid(this))
            addView(TextView(c).apply {
                text = "ViệtTiến┇ᴱᴸᴬᴵ"; setTextColor(-1); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                layoutParams = FrameLayout.LayoutParams(-2, -2, 81).apply { bottomMargin = 80 }
                translationY = 50f; animate().translationY(0f).setInterpolator(ip).setDuration(800).start()
            })
        }

        // Flags: WatchOutsideTouch | LayoutInScreen | HardwareAccelerated
        // Giữ nguyên 262404, KHÔNG thêm 512 để giữ Blur
        val lp = WindowManager.LayoutParams(-1, -1, 2038, 262404, -3).apply {
            blurBehindRadius = 1
            layoutInDisplayCutoutMode = 1 // SHORT_EDGES: Tràn lên tai thỏ
            dimAmount = 0f
            gravity = Gravity.TOP or Gravity.START // Neo chặt vào góc trên cùng
        }

        w.addView(root, lp); pop = root; EnergyRing.bringToFront()
        root.post { blur(root, lp, 1, 500); root.animate().alpha(1f).setInterpolator(ip).setDuration(800).start() }
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
            R.drawable.dim to {
                runCatching { Settings.Secure.putInt(c.contentResolver, "reduce_bright_colors_activated", if (isDim()) 0 else 1) }
                p.edit { putBoolean("dim", isDim()) }; anim(grid.getChildAt(4), isDim())
            },
            R.drawable.cts to { c.startActivity(Intent(c, CtsActivity::class.java).addFlags(268435456)); close(root) },
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
        duration = 800; interpolator = ip
        addUpdateListener { if (v.isAttachedToWindow) runCatching { lp.blurBehindRadius = it.animatedValue as Int; w.updateViewLayout(v, lp) } }
    }.start()

    private fun exec(pkg: String, cls: String, hist: Boolean = false, action: String? = null) = runCatching {
        c.startActivity(Intent().setClassName(pkg, cls).addFlags(268435456).apply { if (hist) addFlags(1048576); if (action != null) setAction(action) })
    }

    fun destroy() { bar?.let { if (it.isAttachedToWindow) w.removeView(it) }; pop?.let { if (it.isAttachedToWindow) w.removeView(it) } }
}