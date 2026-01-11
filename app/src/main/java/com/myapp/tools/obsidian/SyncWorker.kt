package com.myapp.tools.obsidian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.android.gms.auth.GoogleAuthUtil
import android.accounts.Account
import com.myapp.R
import java.io.File
import java.net.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        setForegroundSafe()

        // 1. Hiện Scanner (2 vòng trắng/cyan xoay)
        EnergyRing.show(applicationContext)

        try {
            // SỬA: Dùng Config thay vì AppConfig
            val email = Config.getUserEmail(applicationContext) ?: return Result.failure()
            val token = GoogleAuthUtil.getToken(applicationContext, Account(email, "com.google"), "oauth2:https://www.googleapis.com/auth/drive")
            val rootId = Config.getRootFolderId(applicationContext)

            val conf = StringBuilder()
                .append("[gdrive]\ntype = drive\nscope = drive\n")
                .append("token = {\"access_token\":\"$token\",\"token_type\":\"Bearer\",\"expiry\":\"2030-01-01T00:00:00+07:00\"}\n")
            if (!rootId.isNullOrEmpty()) conf.append("root_folder_id = $rootId\n")
            File(applicationContext.filesDir, "rclone.conf").writeText(conf.toString())
        } catch (e: Exception) {
            EnergyRing.hide()
            return Result.failure()
        }

        val proxy = TinyProxy(10800)
        proxy.start()

        return try {
            val isUpload = inputData.getBoolean("is_upload", true)
            // SỬA: Dùng Config
            val local = Config.getLocalPath(applicationContext)
            val rootId = Config.getRootFolderId(applicationContext)
            val remote = if (!rootId.isNullOrEmpty()) "gdrive:" else "gdrive:Obsidian"
            val src = if (isUpload) local else remote
            val dst = if (isUpload) remote else local

            val pb = ProcessBuilder(
                File(applicationContext.applicationInfo.nativeLibraryDir, "librclone.so").absolutePath,
                "--config", File(applicationContext.filesDir, "rclone.conf").absolutePath,
                "sync", src, dst,
                "--transfers", "4", "--checkers", "4",
                "--delete-during", "--create-empty-src-dirs",
                "--http-proxy", "http://127.0.0.1:10800",
                "--no-check-certificate", "--drive-chunk-size", "32M", "--drive-use-trash=false",
                "--stats=0.5s",
                "--progress"
            )

            pb.environment().apply {
                put("LD_LIBRARY_PATH", applicationContext.applicationInfo.nativeLibraryDir)
                put("HOME", applicationContext.filesDir.absolutePath)
                put("TMPDIR", applicationContext.cacheDir.absolutePath)
                put("http_proxy", "http://127.0.0.1:10800"); put("https_proxy", "http://127.0.0.1:10800")
            }
            pb.redirectErrorStream(true)
            pb.directory(applicationContext.filesDir)

            val process = pb.start()

            // Regex chuẩn
            val strictPattern = Pattern.compile("Transferred:.*?(?:B|iB).*?,\\s*(\\d+)%,.*?ETA")
            var lastProgress = 0

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!
                    if (raw.contains("Transferred:") && (raw.contains(" B") || raw.contains("iB"))) {
                        val m = strictPattern.matcher(raw)
                        if (m.find()) {
                            val p = m.group(1)?.toIntOrNull() ?: 0

                            if (p >= lastProgress) {
                                lastProgress = p
                                // 2. Kích hoạt hiệu ứng chuyển đổi Scanner -> Sync Ring
                                EnergyRing.setProgress(p)
                            }
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                EnergyRing.success()
                Result.success()
            } else {
                EnergyRing.hide()
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e("SyncWorker", "Error: ${e.message}")
            EnergyRing.hide()
            Result.failure()
        } finally {
            proxy.stop()
        }
    }

    private suspend fun setForegroundSafe() {
        val channelId = "obsidian_sync_silent"
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(channelId, "Obsidian Background", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.obsidian1)
            .setContentTitle("Obsidian Sync")
            .setOngoing(true).build()

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            if (Build.VERSION.SDK_INT >= 29) setForeground(ForegroundInfo(101, notif, type))
            else setForeground(ForegroundInfo(101, notif))
        } catch (_: Exception) {}
    }

    class TinyProxy(private val port: Int) {
        @Volatile var isRunning = true
        private val executor = Executors.newCachedThreadPool()
        private var serverSocket: ServerSocket? = null
        fun start() {
            executor.execute {
                try {
                    serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                    serverSocket?.soTimeout = 2000
                    while (isRunning) {
                        try {
                            val client = serverSocket?.accept()
                            if (client != null) executor.execute { handle(client) }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }
        fun stop() {
            isRunning = false
            try { serverSocket?.close() } catch (_: Exception) {}
            executor.shutdownNow()
        }
        private fun handle(cl: Socket) = try { cl.use {
            val rd = it.getInputStream().bufferedReader()
            val req = rd.readLine()
            if (req?.startsWith("CONNECT") == true) {
                val parts = req.split(" ")
                val hostPort = parts[1].split(":")
                val host = hostPort[0]
                val port = hostPort.getOrNull(1)?.toInt() ?: 443
                while (rd.readLine().isNotEmpty()) {}
                it.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                val rm = Socket(host, port)
                rm.use { remote ->
                    val t = Executors.newSingleThreadExecutor()
                    t.execute { try { remote.inputStream.copyTo(it.getOutputStream()) } catch (_: Exception) {} }
                    try { it.inputStream.copyTo(remote.getOutputStream()) } catch (_: Exception) {}
                    t.shutdownNow()
                }
            }
        }} catch (_: Exception) {}
    }
}