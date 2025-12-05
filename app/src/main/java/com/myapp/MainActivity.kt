package com.myapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.myapp.tools.SidebarService

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Quy trình kiểm tra quyền:
        // 1. Kiểm tra quyền Vẽ trên ứng dụng khác (Overlay)
        // 2. Kiểm tra quyền Truy cập thông báo (Để snooze thông báo hệ thống)
        // 3. Nếu đủ -> Bắt đầu Service Sidebar
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Kiểm tra lại mỗi khi user quay lại từ cài đặt
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // 1. Quyền Overlay (Quan trọng nhất)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Vui lòng cấp quyền 'Vẽ trên ứng dụng khác'", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        // 2. Quyền Notification Listener (Để ẩn thông báo hệ thống)
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Vui lòng cấp quyền 'Truy cập thông báo' để ẩn icon hệ thống", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            return
        }

        // 3. Nếu đủ quyền -> Chạy Service
        startSidebarService()
        finish() // Đóng Activity cho gọn
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun startSidebarService() {
        val intent = Intent(this, SidebarService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Kích hoạt luôn service snooze
        startService(Intent(this, NotificationSnoozerService::class.java))
    }
}