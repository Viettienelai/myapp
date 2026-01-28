package com.myapp.tools.obsidian

import android.content.Context
import androidx.core.content.edit

object Config {
    private val Context.prefs get() = getSharedPreferences("ObsidianPrefs", Context.MODE_PRIVATE)

    fun saveLocalPath(ctx: Context, path: String) = ctx.prefs.edit { putString("local_path", path) }
    fun getLocalPath(ctx: Context) = ctx.prefs.getString("local_path", "/storage/emulated/0/Documents/Obsidian")!!

    fun saveRemotePath(ctx: Context, path: String) = ctx.prefs.edit { putString("remote_path", path) }

    fun saveRemoteDisplayName(ctx: Context, name: String) = ctx.prefs.edit { putString("remote_name", name) }
    fun getRemoteDisplayName(ctx: Context) = ctx.prefs.getString("remote_name", "Mặc định (Root)")!!

    fun saveUserEmail(ctx: Context, email: String) = ctx.prefs.edit { putString("user_email", email) }
    fun getUserEmail(ctx: Context) = ctx.prefs.getString("user_email", null)

    // --- MỚI THÊM: Lưu ID thư mục gốc ---
    fun saveRootFolderId(ctx: Context, id: String?) = ctx.prefs.edit { putString("root_folder_id", id) }
    fun getRootFolderId(ctx: Context) = ctx.prefs.getString("root_folder_id", null)
}