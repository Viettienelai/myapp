package com.myapp.tools

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myapp.R

class Sidebar : Service() {

    private val volumeManager by lazy { VolumeBar(this) }
    private val popupManager by lazy { Popup(this) }

    // Receiver lắng nghe sự kiện màn hình
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON ||
                intent?.action == Intent.ACTION_USER_PRESENT) {
                // Màn hình sáng: Ép cập nhật lại vùng chống Back
                popupManager.refreshExclusion()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        popupManager.setup()
        volumeManager.setup()

        // Đăng ký lắng nghe
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        popupManager.destroy()
        volumeManager.destroy()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "SidebarBackgroundChannel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Sidebar Service", NotificationManager.IMPORTANCE_MIN)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sidebar Active")
            .setSmallIcon(R.drawable.scan)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1999, notification)
    }
}