package com.myapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import com.myapp.tools.Sidebar
import androidx.core.net.toUri

class  MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // 1. Quyền Overlay (Hiển thị trên ứng dụng khác)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            return
        }

        // 2. Quyền Notification (Đọc/Snooze thông báo)
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }

        // 3. Quyền Storage (Quản lý tất cả tệp - Để dọn rác)
        // Yêu cầu Android 11+ (API 30+). Nếu target thấp hơn thì cần code khác.
        if (!Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()))
            return
        }

        // 4. Nếu đủ quyền -> Chạy Service và đóng UI
        startSidebarService()
        finish()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun startSidebarService() {
        startForegroundService(Intent(this, Sidebar::class.java))
        startService(Intent(this, NotiSnoozer::class.java))
    }
}