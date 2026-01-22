package com.myapp.tools

import android.annotation.SuppressLint
import android.app.*
import android.os.* // Đã bao gồm Vibrator và VibrationEffect
import org.lsposed.hiddenapibypass.HiddenApiBypass

@Suppress("DEPRECATION")
class CtsActivity : Activity() {
    @SuppressLint("PrivateApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(512, 512) // 512 = FLAG_LAYOUT_NO_LIMITS

        Handler(mainLooper).postDelayed({
            runCatching {
                val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "voiceinteraction") as IBinder
                val service = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                val bundle = Bundle().apply {
                    putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                    putInt("omni.entry_point", 1)
                }
                HiddenApiBypass.invoke(Class.forName("com.android.internal.app.IVoiceInteractionManagerService"), service, "showSessionFromSession", null, bundle, 7, "hyperOS_home")
            }
            finish(); overridePendingTransition(0, 0)
        }, 0) // Thời gian delay 350ms
    }
}