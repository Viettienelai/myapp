package com.myapp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationSnoozerService : NotificationListenerService() {

    // Danh sách các từ khóa bạn muốn chặn.
    // Chỉ cần thêm chuỗi mới vào đây là xong.
    private val snoozeKeywords = listOf(
        "displaying over other apps",  // Chặn toàn bộ thông báo vẽ đè (bất kể app nào)
        "chat heads active",
        "smart rapid charging is on",
        "power saving mode turned on"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Lấy extras an toàn, nếu null thì dừng luôn
        val extras = sbn?.notification?.extras ?: return

        // Gộp cả Tiêu đề và Nội dung thành 1 chuỗi để kiểm tra cho lẹ
        val content = "${extras.getString("android.title", "")} ${extras.getString("android.text", "")}"

        // Kiểm tra: Nếu content chứa bất kỳ từ khóa nào trong list (không phân biệt hoa thường)
        if (snoozeKeywords.any { content.contains(it, ignoreCase = true) }) {
            try {
                // Snooze 12 tiếng (43200000 ms)
                snoozeNotification(sbn.key, 43200000L)
            } catch (_: Exception) { }
        }
    }
}