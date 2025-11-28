package com.tilescan

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.service.quicksettings.TileService
import android.widget.Toast

class QuickShareTileService : TileService() {

    override fun onClick() {
        super.onClick()

        // 1. Tạo Intent nhắm thẳng vào Activity đích
        val intent = Intent()
        intent.component = ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.nearby.sharing.ReceiveUsingSamsungQrCodeMainActivity"
        )
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.action = Intent.ACTION_MAIN

        // 2. Logic mở khóa và chạy (Gọn nhất)
        val runner = Runnable {
            try {
                // Trên Android 14+, bắt buộc gói Intent vào PendingIntent
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi mở Quick Share", Toast.LENGTH_SHORT).show()
            }
        }

        if (isLocked) {
            unlockAndRun(runner)
        } else {
            runner.run()
        }
    }
}