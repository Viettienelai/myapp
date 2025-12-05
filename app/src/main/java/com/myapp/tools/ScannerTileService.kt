package com.myapp.tools

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.service.quicksettings.TileService
import android.widget.Toast

class ScannerTileService : TileService() {

    override fun onClick() {
        super.onClick()

        // 1. Cấu hình Intent
        val intent = Intent()
        intent.component = ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.mlkit.barcode.v2.ScannerActivity"
        )
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.action = Intent.ACTION_MAIN

        // 2. Logic thực thi (Gói gọn để dùng chung)
        val runner = Runnable {
            try {
                // Bắt buộc dùng PendingIntent cho Android 14+
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi mở Scanner", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Kiểm tra khóa màn hình
        if (isLocked) {
            unlockAndRun(runner)
        } else {
            runner.run()
        }
    }
}