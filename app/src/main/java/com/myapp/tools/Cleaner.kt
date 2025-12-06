package com.myapp.tools

import java.io.File

object Cleaner {
    // DANH SÁCH ĐEN: Điền đường dẫn cụ thể muốn xóa tại đây
    private val targets = listOf(
        "/storage/emulated/0/DCIM/.thumbnails",
        "/storage/emulated/0/Movies/.thumbnails",
        "/storage/emulated/0/Music/.thumbnails",
        "/storage/emulated/0/Pictures/.thumbnails",
        "/storage/emulated/0/Pictures/.camera_cache",
        "/storage/emulated/0/MT2/.recycle"
    )

    // DANH SÁCH TRẮNG: Các thư mục này sẽ KHÔNG bị xóa dù rỗng,
    // nhưng các thư mục con bên trong vẫn bị quét và xóa nếu rỗng.
    private val whitelist = listOf(
        "/storage/emulated/0/Download"
    )

    fun clean() {
        Thread {
            // 1. Xóa không thương tiếc danh sách chỉ định
            targets.forEach { File(it).deleteRecursively() }

            // 2. Quét dọn thư mục rỗng (chỉ quét 2 nơi này)
            scan(File("/storage/emulated/0"), true)
            scan(File("/storage/emulated/0/Android/media"), true)
        }.start()
    }

    private fun scan(d: File, root: Boolean) {
        if (!d.exists() || !d.isDirectory) return

        // Đệ quy quét con trước (Post-order traversal)
        // Việc này đảm bảo các thư mục con bên trong whitelist vẫn được xử lý trước
        d.listFiles()?.forEach { scan(it, false) }

        // Kiểm tra xem thư mục hiện tại có nằm trong whitelist không
        val isWhitelisted = d.absolutePath in whitelist

        // Xóa thư mục nếu rỗng (trừ thư mục gốc scan ban đầu VÀ trừ thư mục trong whitelist)
        if (!root && !isWhitelisted && d.listFiles()?.isEmpty() == true) {
            d.delete()
        }
    }
}