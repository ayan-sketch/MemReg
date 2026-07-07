package com.memreg.net.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val updateMessage: String,
    val latestDbVersion: Int,
    val dbUrl: String
)

class UpdateManager(private val context: Context) {

    companion object {
        private const val VERSION_URL = "https://raw.githubusercontent.com/ayan-sketch/MemReg/refs/heads/main/version.json"
    }

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val info = parseUpdateInfo(json)
            val currentVersion = getCurrentVersionCode()
            if (info.latestVersionCode > currentVersion || info.latestDbVersion > getDbVersion()) info else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            downloadFile(info.apkUrl, context.cacheDir, "update.apk", onProgress)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadDb(info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            downloadFile(info.dbUrl, context.cacheDir, "memreg_new.db", onProgress)
        } catch (e: Exception) {
            null
        }
    }

    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun downloadFile(url: String, destDir: File, fileName: String, onProgress: (Int) -> Unit): File {
        val file = File(destDir, fileName)
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        val totalSize = conn.contentLength
        val input = conn.inputStream
        val output = FileOutputStream(file)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalSize > 0) {
                onProgress((totalRead * 100) / totalSize)
            }
        }
        output.close()
        input.close()
        conn.disconnect()
        return file
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            0
        }
    }

    private fun getDbVersion(): Int {
        val prefs = context.getSharedPreferences("memreg_updater", Context.MODE_PRIVATE)
        return prefs.getInt("db_version", 1)
    }

    fun setDbVersion(version: Int) {
        val prefs = context.getSharedPreferences("memreg_updater", Context.MODE_PRIVATE)
        prefs.edit().putInt("db_version", version).apply()
    }

    private fun parseUpdateInfo(json: String): UpdateInfo {
        val vc = extractInt(json, "latest_version_code")
        val vn = extractString(json, "latest_version_name")
        val au = extractString(json, "apk_url")
        val msg = extractString(json, "update_message")
        val dv = extractInt(json, "latest_db_version")
        val du = extractString(json, "db_url")
        return UpdateInfo(vc, vn, au, msg, dv, du)
    }

    private fun extractString(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.getOrElse(1) { "" } ?: ""
    }

    private fun extractInt(json: String, key: String): Int {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        return pattern.find(json)?.groupValues?.getOrElse(1) { "0" }?.toIntOrNull() ?: 0
    }
}
