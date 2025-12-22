package com.myapp.tools

import java.io.File

object Cleaner {
    // 1. DANH SÁCH ĐEN: Xóa thẳng tay toàn bộ nội dung bên trong
    private val targets = listOf(
        "/storage/emulated/0/DCIM/.thumbnails",
        "/storage/emulated/0/Movies/.thumbnails",
        "/storage/emulated/0/Music/.thumbnails",
        "/storage/emulated/0/Pictures/.thumbnails",
        "/storage/emulated/0/Pictures/.camera_cache",
        "/storage/emulated/0/MT2/.recycle",
        "/storage/emulated/0/Documents/ringtone"
    )

    // 2. DANH SÁCH LOẠI TRỪ (Ignore): Không thèm động vào, không thèm quét bên trong
    // Nên dùng cho các thư mục chứa dữ liệu quan trọng hoặc app tránh bị lag (như Android/data)
    private val excludeList = listOf(
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Documents/Obsidian",
    )

    // 3. DANH SÁCH TRẮNG: Không xóa thư mục này dù nó rỗng,
    // NHƯNG vẫn quét và xóa các thư mục con bên trong nếu chúng rỗng.
    private val whitelist = listOf(
        "/storage/emulated/0/Download"
    )

    fun clean() {
        Thread {
            // Bước 1: Xóa các mục trong danh sách đen
            targets.forEach { File(it).deleteRecursively() }

            // Bước 2: Quét dọn thư mục rỗng
            scan(File("/storage/emulated/0"), true)
            scan(File("/storage/emulated/0/Android/media"), true)
        }.start()
    }

    private fun scan(d: File, root: Boolean) {
        // Kiểm tra cơ bản
        if (!d.exists() || !d.isDirectory) return

        // MỚI: Nếu thư mục nằm trong danh sách loại trừ thì thoát ngay (không quét con)
        if (d.absolutePath in excludeList) return

        // Đệ quy quét con trước (Post-order traversal)
        d.listFiles()?.forEach { scan(it, false) }

        // Kiểm tra xem thư mục hiện tại có nằm trong whitelist không
        val isWhitelisted = d.absolutePath in whitelist

        // Xóa thư mục nếu rỗng (trừ root và trừ whitelist)
        if (!root && !isWhitelisted && d.listFiles()?.isEmpty() == true) {
            d.delete()
        }
    }
}