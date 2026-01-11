package com.myapp.tools.obsidian

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.*
import java.util.*

data class DriveItem(val id: String, val name: String)

class SetupActivity : ComponentActivity() {
    private val _driveService = mutableStateOf<Drive?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = darkColors(background = Color(0xFF121212))) {
                SetupScreen()
            }
        }
    }

    @Composable
    fun SetupScreen() {
        val ctx = LocalContext.current
        var userEmail by remember { mutableStateOf(Config.getUserEmail(ctx)) }
        var localPath by remember { mutableStateOf(Config.getLocalPath(ctx)) }
        var remoteName by remember { mutableStateOf(Config.getRemoteDisplayName(ctx)) }
        var showDrivePicker by remember { mutableStateOf(false) }

        val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(res.data)
            runCatching {
                val acc = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                initDrive(acc) { email -> userEmail = email }
            }.onFailure { Toast.makeText(ctx, "Login Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
        }

        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val path = it.path?.split("primary:")?.getOrNull(1)?.let { p -> "/storage/emulated/0/$p" } ?: "/storage/emulated/0/"
                Config.saveLocalPath(ctx, path)
                localPath = path
            }
        }

        Column(
            Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Sync, null, tint = Color(0xFFBB86FC), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Cấu hình Obsidian Sync", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))

            ConfigItem(
                icon = Icons.Default.AccountCircle,
                title = if (userEmail.isNullOrEmpty()) "Đăng nhập Google Drive" else userEmail!!,
                isDone = !userEmail.isNullOrEmpty()
            ) {
                val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope(DriveScopes.DRIVE))
                    .build()
                authLauncher.launch(GoogleSignIn.getClient(ctx, opts).signInIntent)
            }

            ConfigItem(
                icon = Icons.Default.Smartphone,
                title = if (localPath.length > 25) "...${localPath.takeLast(25)}" else localPath,
                subTitle = "Thư mục trên máy",
                isDone = localPath != "/storage/emulated/0/Documents/Obsidian"
            ) { folderLauncher.launch(null) }

            val currentService = _driveService.value
            ConfigItem(
                icon = Icons.Default.Cloud,
                title = remoteName,
                subTitle = "Thư mục trên Drive",
                isDone = remoteName != "Mặc định (Root)"
            ) {
                if (currentService != null) showDrivePicker = true
                else Toast.makeText(ctx, "Vui lòng đăng nhập trước!", Toast.LENGTH_SHORT).show()
            }

            Spacer(Modifier.height(40.dp))

            val isReady = !userEmail.isNullOrEmpty()
            Button(
                onClick = { finish() },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF03DAC6))
            ) {
                Text("HOÀN TẤT & ĐÓNG", color = Color.Black)
            }
        }

        if (showDrivePicker && _driveService.value != null) {
            DrivePicker(_driveService.value!!) { item ->
                showDrivePicker = false
                if (item != null) {
                    // --- THAY ĐỔI QUAN TRỌNG: Lưu vào AppConfig ---
                    Config.saveRemotePath(ctx, "gdrive:")
                    Config.saveRemoteDisplayName(ctx, item.name)
                    Config.saveRootFolderId(ctx, item.id) // Lưu ID vào Prefs
                    remoteName = item.name
                }
            }
        }

        LaunchedEffect(Unit) {
            GoogleSignIn.getLastSignedInAccount(ctx)?.let { initDrive(it) { } }
        }
    }

    @Composable
    fun ConfigItem(icon: ImageVector, title: String, subTitle: String? = null, isDone: Boolean, onClick: () -> Unit) {
        Card(
            backgroundColor = Color(0xFF2D2D2D),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (isDone) Color(0xFF03DAC6) else Color.Gray)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (subTitle != null) Text(subTitle, color = Color.Gray, fontSize = 12.sp)
                }
                if (isDone) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF03DAC6))
            }
        }
    }

    private fun initDrive(acc: GoogleSignInAccount, onUser: (String) -> Unit) {
        val cred = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE)).apply { selectedAccount = acc.account }
        val service = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), cred).setApplicationName("ObsidianSync").build()
        _driveService.value = service
        Config.saveUserEmail(this, acc.email ?: "")
        onUser(acc.email ?: "")

        // Silent Token Refresh không cần thiết ở đây nữa vì SyncWorker sẽ tự làm
    }
}

@Composable
fun DrivePicker(service: Drive, onResult: (DriveItem?) -> Unit) {
    var stack by remember { mutableStateOf(listOf(DriveItem("root", "Root"))) }
    var list by remember { mutableStateOf(listOf<DriveItem>()) }

    LaunchedEffect(stack.last()) {
        withContext(Dispatchers.IO) {
            try {
                val res = service.files().list()
                    .setQ("'${stack.last().id}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed=false")
                    .setFields("files(id, name)").setOrderBy("name").execute()
                list = res.files.map { DriveItem(it.id, it.name) }
            } catch (_: Exception) {}
        }
    }

    Dialog({ onResult(null) }) {
        Card(Modifier.fillMaxWidth().height(500.dp), backgroundColor = Color(0xFF1E1E1E)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stack.size > 1) IconButton({ stack = stack.dropLast(1) }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text(stack.last().name, style = MaterialTheme.typography.h6, color = Color.White)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(list) { i ->
                        Column {
                            Row(Modifier.clickable { stack = stack + i }.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = Color.Yellow)
                                Spacer(Modifier.width(10.dp))
                                Text(i.name, color = Color.White)
                            }
                            Divider(color = Color.DarkGray)
                        }
                    }
                }
                Button({ onResult(stack.last()) }, Modifier.align(Alignment.End), colors = ButtonDefaults.buttonColors(Color(0xFF03DAC6))) { Text("CHỌN THƯ MỤC NÀY") }
            }
        }
    }
}