package com.myapp.tools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.*
import android.os.Build
import android.provider.Settings
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.myapp.R

@Suppress("DEPRECATION")
class PopupBarManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var barView: View? = null
    private var popupView: View? = null

    // Xác định loại Layout phù hợp cho Android mới (8.0+) và cũ
    private val layoutType =
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    fun setup() {
        addBar(0, 300, Color.TRANSPARENT) { showPopup() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBar(yPos: Int, h: Int, color: Int, act: () -> Unit) {
        val v = FrameLayout(ctx).apply {
            setBackgroundColor(color)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null) // Tối ưu GPU cho view tĩnh
            systemGestureExclusionRects = listOf(Rect(0, 0, 20, h))

            var x0 = 0f; var d = false
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { x0 = e.rawX; d = false }
                    MotionEvent.ACTION_MOVE -> if (!d && e.rawX - x0 < -20) { d = true; act() }
                }
                true
            }
        }

        val p = WindowManager.LayoutParams(20, h, layoutType, 296, -3).apply {
            gravity = Gravity.TOP or Gravity.END
            y = yPos
        }
        wm.addView(v, p); barView = v
    }

    @SuppressLint("SetTextI18n")
    private fun showPopup() {
        if (popupView != null) return

        val root = FrameLayout(ctx).apply {
            // Logic tiết kiệm pin: Max alpha nếu bật, ngược lại 90
            val isSave = (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
            setBackgroundColor(Color.argb(if (isSave) 255 else 90, 110, 110, 110))

            alpha = 0f; isClickable = true
            setOnClickListener { close(this) }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Giúp view tràn xuống dưới Navigation Bar
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        val con = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 90)
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(80, 0, 80, 0) }
            translationY = -150f
        }

        // --- ĐÃ XÓA PHẦN HIỂN THỊ PIN & NHIỆT ĐỘ TẠI ĐÂY ---

        // Grid Icons
        val grid = GridLayout(ctx).apply { columnCount = 3; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = 1 } }
        val tiles = listOf(
            R.drawable.scan to { exec("com.google.android.gms", "com.google.android.gms.mlkit.barcode.v2.ScannerActivity") },
            R.drawable.lens to { exec("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity", true) },
            R.drawable.quickshare to { exec("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity", action = Intent.ACTION_MAIN) },
            R.drawable.dim to { toggleDim() },
            R.drawable.cts to { ctx.startActivity(Intent(ctx, CtsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        )

        tiles.forEachIndexed { i, (ic, fn) ->
            val t = FrameLayout(ctx).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 190; height = 190; setMargins(30, 40, 30, 40) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(120, 0, 0, 0)) }
                addView(ImageView(context).apply { setImageResource(ic); setColorFilter(-1); layoutParams = FrameLayout.LayoutParams(75, 75, 17) })
                setOnClickListener { fn(); close(root) }
                alpha = 0f; translationY = -80f
            }
            grid.addView(t)
            t.animate().alpha(1f).translationY(0f).setStartDelay(50 + (i * 40L)).setInterpolator(OvershootInterpolator(1.2f)).setDuration(450).start()
        }
        con.addView(grid); root.addView(con)

        root.addView(TextView(ctx).apply {
            text = "ViệtTiến┇ᴱᴸᴬᴵ"; setTextColor(-1); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 80 }
            translationY = 100f
            animate().translationY(0f).setDuration(350).start()
        })

        val p = WindowManager.LayoutParams(-1, -1, layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_BLUR_BEHIND, -3).apply {
            blurBehindRadius = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        wm.addView(root, p); popupView = root

        root.post {
            animateBlur(root, p, 0, 500, 350)
            root.animate().alpha(1f).setDuration(350).start()
            con.animate().translationY(0f).setInterpolator(OvershootInterpolator(1f)).setDuration(350).start()
        }
    }

    private fun close(root: View) {
        if (popupView == null) return
        val vg = root as ViewGroup
        val p = root.layoutParams as WindowManager.LayoutParams

        animateBlur(root, p, p.blurBehindRadius, 0, 200)

        // Container (Icons): Bay lên trên và mờ dần
        vg.getChildAt(0).animate().translationY(-200f).alpha(0f)
            .setDuration(250).setInterpolator(AccelerateInterpolator()).start()

        // Text (ViệtTiến): Bay xuống dưới
        vg.getChildAt(1).animate().translationY(100f)
            .setDuration(250).start()

        // Root Background: Mờ dần và đóng
        root.animate().alpha(0f).setDuration(250).withEndAction {
            root.visibility = View.GONE
            root.post {
                if (root.isAttachedToWindow) try { wm.removeView(root) } catch(_: Exception){}
                popupView = null
            }
        }.start()
    }

    private fun animateBlur(v: View, p: WindowManager.LayoutParams, f: Int, t: Int, d: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ValueAnimator.ofInt(f, t).apply { duration = d; addUpdateListener {
                if (v.isAttachedToWindow && v.isVisible) try { p.blurBehindRadius = it.animatedValue as Int; wm.updateViewLayout(v, p) } catch (_: Exception){}
            }}.start()
        }
    }

    private fun exec(pkg: String, cls: String, hist: Boolean = false, action: String? = null) {
        runCatching {
            ctx.startActivity(Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                if (hist) addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
                if (action != null) setAction(action)
            })
        }
    }

    private fun toggleDim() {
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
            val prefs = ctx.getSharedPreferences("tile_prefs", Context.MODE_PRIVATE)
            val isDim = prefs.getBoolean("dim", false)
            Settings.Secure.putInt(ctx.contentResolver, "reduce_bright_colors_activated", if (!isDim) 1 else 0)
            prefs.edit { putBoolean("dim", !isDim) }
        }
        // ĐÃ XÓA TOAST TẠI ĐÂY
    }

    fun destroy() {
        barView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        popupView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
    }
}