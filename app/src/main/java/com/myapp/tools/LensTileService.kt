package com.myapp.tools

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.Toast

class LensTileService : TileService() {
    override fun onClick() {
        runCatching {
            val intent = Intent()
                .setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }.onFailure {
            Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}