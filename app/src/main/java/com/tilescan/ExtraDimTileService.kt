package com.tilescan

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ExtraDimTileService : TileService() {

    override fun onStartListening() {
        updateUi()
    }

    override fun onClick() {
        if (checkSelfPermission(WRITE_SECURE_SETTINGS) != PERMISSION_GRANTED) return

        // 1. Đọc trạng thái thật từ hệ thống (1 = On, 0 = Off)
        val current = Settings.Secure.getInt(contentResolver, "reduce_bright_colors_activated", 0)
        val newState = if (current == 1) 0 else 1

        // 2. Gửi lệnh toggle
        runCatching {
            Settings.Secure.putInt(contentResolver, "reduce_bright_colors_activated", newState)
        }

        // 3. Cập nhật icon Tile ngay lập tức
        updateUi()
    }

    private fun updateUi() {
        qsTile?.run {
            // Đọc lại từ hệ thống để hiển thị đúng trạng thái
            val isActive = Settings.Secure.getInt(contentResolver, "reduce_bright_colors_activated", 0) == 1
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}