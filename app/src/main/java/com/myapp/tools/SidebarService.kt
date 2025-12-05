package com.myapp.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myapp.R

class SidebarService : Service() {

    // Vẫn dùng các Manager cũ của bạn, nhưng truyền Context là Service này
    // Lưu ý: Trong PopupBarManager/VolumeBarManager, bạn cần đảm bảo
    // LayoutParams của WindowManager dùng TYPE_APPLICATION_OVERLAY (cho Android 8+)
    private val volumeManager by lazy { VolumeBarManager(this) }
    private val popupManager by lazy { PopupBarManager(this) }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Không cần bind
    }

    override fun onCreate() {
        super.onCreate()
        // Bắt buộc chạy Foreground để không bị kill
        startForegroundServiceNotification()

        // Setup giao diện
        popupManager.setup()
        volumeManager.setup()
    }

    override fun onDestroy() {
        popupManager.destroy()
        volumeManager.destroy()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: Nếu bị kill do thiếu RAM, hệ thống sẽ tự khởi động lại nó
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "SidebarBackgroundChannel"
        val channel = NotificationChannel(
            channelId,
            "Sidebar Service",
            NotificationManager.IMPORTANCE_MIN // Mức thấp nhất để không làm phiền
        )
        channel.description = "Keeping sidebar active"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sidebar is active")
            .setContentText("Swipe to access tools")
            .setSmallIcon(R.drawable.scan) // Thay bằng icon của bạn
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        // ID 1999 là ví dụ, số nào cũng được miễn khác 0
        startForeground(1999, notification)
    }
}