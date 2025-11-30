package com.tilescan

import android.animation.*
import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*

class SidebarPopupActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(512, 512)

        // 1. Setup Popup
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#FF222222")); cornerRadius = 55f }
            setPadding(40, 80, 40, 90)
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER; setMargins(80, 0, 80, 0)
            }
        }

        // 2. Pin & Temp
        container.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 60)
            addView(ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(70, 40); setImageDrawable(BattDrw()) })
            addView(TextView(context).apply {
                setTextColor(Color.LTGRAY); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setPadding(25, 0, 0, 0)
                val t = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", 0) ?: 0
                text = "${t / 10f}°"
            })
        })

        // 3. Grid Tiles
        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        // Kiểm tra trạng thái Dim hiện tại từ hệ thống
        val isDimOn = Settings.Secure.getInt(contentResolver, "reduce_bright_colors_activated", 0) == 1
        val blueColor = Color.parseColor("#448AFF") // Màu xanh lam

        val tiles = listOf(
            Triple(R.drawable.scan, Color.WHITE) { exec("com.google.android.gms", "com.google.android.gms.mlkit.barcode.v2.ScannerActivity") },
            Triple(R.drawable.lens, Color.WHITE) { exec("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity", true) },
            Triple(R.drawable.quickshare, Color.WHITE) { exec("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity", action = Intent.ACTION_MAIN) },

            // LOGIC MÀU SẮC CHO DIM: Nếu đang Bật -> Xanh, Tắt -> Trắng
            Triple(R.drawable.dim, if (isDimOn) blueColor else Color.WHITE) { toggleDim() },

            Triple(R.drawable.cts, Color.WHITE) { startActivity(Intent(this, CtsActivity::class.java).addFlags(268435456)) }
        )

        tiles.forEachIndexed { i, (icon, color, act) ->
            // Truyền màu vào hàm tạo Tile
            val tile = mkTile(icon, color, act)
            tile.alpha = 0f; tile.translationY = -80f
            grid.addView(tile)
            tile.animate().alpha(1f).translationY(0f).setStartDelay(50 + (i * 40L)).setInterpolator(OvershootInterpolator(1.2f)).setDuration(450).start()
        }
        container.addView(grid)

        // 4. Root Wrapper
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF")); alpha = 0f
            setOnClickListener { close() }; addView(container)
        }
        setContentView(root)

        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                container.translationY = -150f
                container.animate().translationY(0f).setInterpolator(OvershootInterpolator(1f)).setDuration(350).start()
                root.animate().alpha(1f).setDuration(300).start()
                return true
            }
        })
    }

    private fun close() {
        root.animate().alpha(0f).setDuration(200).start()
        container.animate().translationY(-150f).alpha(0f).setDuration(200).withEndAction {
            finish(); overridePendingTransition(0, 0)
        }.start()
    }

    // Sửa hàm mkTile để nhận thêm tham số màu (tint)
    private fun mkTile(icon: Int, tint: Int, act: () -> Unit) = FrameLayout(this).apply {
        val size = 190
        layoutParams = GridLayout.LayoutParams().apply {
            width = size; height = size; setMargins(30, 40, 30, 40)
        }
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#22FFFFFF")) }

        addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(tint) // Áp dụng màu (Xanh hoặc Trắng)
            layoutParams = FrameLayout.LayoutParams(90, 90, Gravity.CENTER)
        })
        isClickable = true
        setOnClickListener { act(); close() }
    }

    private fun exec(pkg: String, cls: String, hist: Boolean = false, action: String? = null) {
        runCatching {
            startActivity(Intent().setClassName(pkg, cls).apply {
                addFlags(268435456); if(hist) addFlags(1048576); if(action!=null) setAction(action)
            })
        }
    }

    // Hàm toggle Dim: Đọc -> Đảo ngược -> Ghi
    private fun toggleDim() {
        runCatching {
            if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0) {
                val current = Settings.Secure.getInt(contentResolver, "reduce_bright_colors_activated", 0)
                Settings.Secure.putInt(contentResolver, "reduce_bright_colors_activated", if (current == 1) 0 else 1)
            } else Toast.makeText(this, "Cần quyền Secure Settings", 0).show()
        }
    }

    class BattDrw : Drawable() {
        val p = Paint(1).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.LTGRAY }
        val f = Paint(1).apply { color = Color.LTGRAY }
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            c.drawRoundRect(0f, 0f, w-10f, h, 8f, 8f, p)
            c.drawRect(w-8f, h/3f, w, h*2/3f, f)
            c.drawRoundRect(8f, 8f, w-18f, h-8f, 3f, 3f, f)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: ColorFilter?) {}; override fun getOpacity() = -3
    }
}