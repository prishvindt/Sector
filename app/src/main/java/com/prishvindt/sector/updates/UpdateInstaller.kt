package com.prishvindt.sector.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdateInstaller(
    private val context: Context
) {
    fun canInstallFromThisSource(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@runCatching
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun downloadApk(
        updateInfo: UpdateInfo,
        onProgress: (Int?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, UPDATE_CACHE_DIR).apply {
                if (!exists() && !mkdirs()) {
                    throw IOException("Cannot create update cache directory")
                }
            }
            updatesDir.listFiles { file -> file.extension == "apk" || file.extension == "part" }
                ?.forEach { it.delete() }

            val safeVersion = updateInfo.latestVersion.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val apkFile = File(updatesDir, "Sector-$safeVersion.apk")
            val partFile = File(updatesDir, "${apkFile.name}.part")

            onProgress(null)
            val connection = (URL(updateInfo.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                useCaches = false
            }

            try {
                if (connection.responseCode !in 200..299) {
                    throw IOException("Update download failed with HTTP ${connection.responseCode}")
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                var downloadedBytes = 0L
                var lastProgress: Int? = null
                connection.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            val progress = totalBytes?.let { ((downloadedBytes * 100) / it).toInt().coerceIn(0, 100) }
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }

                if (apkFile.exists() && !apkFile.delete()) {
                    throw IOException("Cannot replace cached update APK")
                }
                if (!partFile.renameTo(apkFile)) {
                    throw IOException("Cannot finalize cached update APK")
                }
                apkFile
            } catch (error: Throwable) {
                partFile.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
    }

    fun launchInstaller(apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
            context.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(intent)
    }

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UPDATE_CACHE_DIR = "updates"
    }
}
