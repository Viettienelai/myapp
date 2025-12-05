package com.myapp.tools

import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.*
import android.content.Intent
import android.os.* // Đã bao gồm Vibrator và VibrationEffect
import android.service.quicksettings.TileService
import org.lsposed.hiddenapibypass.HiddenApiBypass

class CtsTileService : TileService() {
    override fun onClick() = startActivityAndCollapse(
        getActivity(this, 0, Intent(this, CtsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
    )
}

@Suppress("DEPRECATION")
class CtsActivity : Activity() {
    @SuppressLint("PrivateApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(512, 512) // 512 = FLAG_LAYOUT_NO_LIMITS

        Handler(mainLooper).postDelayed({
            // --- THÊM MỚI: Xử lý rung (50ms) ---
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            // -----------------------------------

            runCatching {
                val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "voiceinteraction") as IBinder
                val service = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                val bundle = Bundle().apply {
                    putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                    putInt("omni.entry_point", 1)
                    putBoolean("micts_trigger", true)
                }
                HiddenApiBypass.invoke(Class.forName("com.android.internal.app.IVoiceInteractionManagerService"), service, "showSessionFromSession", null, bundle, 7, "hyperOS_home")
            }
            finish(); overridePendingTransition(0, 0)
        }, 350) // Thời gian delay 350ms
    }
}