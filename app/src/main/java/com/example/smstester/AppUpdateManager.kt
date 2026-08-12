package com.antteam.smstester

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val required: Boolean
)

sealed interface UpdateInstallResult {
    data object InstallerOpened : UpdateInstallResult
    data object PermissionRequested : UpdateInstallResult
}

object AppUpdateManager {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun check(context: Context): AppUpdateInfo? {
        val token = LicenseManager.getSavedToken(context) ?: return null
        val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }

        val result = functions.getHttpsCallable("checkAppUpdate").call(
            mapOf(
                "token" to token,
                "deviceId" to LicenseManager.getDeviceId(context),
                "currentVersionCode" to currentVersion
            )
        ).await().data as? Map<*, *> ?: return null

        if (result["available"] != true) return null
        val update = result["update"] as? Map<*, *> ?: return null

        return AppUpdateInfo(
            versionCode = (update["versionCode"] as? Number)?.toLong() ?: return null,
            versionName = update["versionName"]?.toString().orEmpty(),
            releaseNotes = update["releaseNotes"]?.toString().orEmpty(),
            apkUrl = update["apkUrl"]?.toString().orEmpty(),
            required = result["required"] == true
        ).takeIf { it.apkUrl.isNotBlank() }
    }

    suspend fun downloadAndInstall(context: Context, update: AppUpdateInfo): UpdateInstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return UpdateInstallResult.PermissionRequested
        }

        val manager = context.getSystemService(DownloadManager::class.java)
        val fileName = "smstester-${update.versionCode}.apk"
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("SMS Tester ${update.versionName}")
            .setDescription("Загрузка обновления")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = manager.enqueue(request)
        val uri = waitForDownload(manager, downloadId)

        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        return UpdateInstallResult.InstallerOpened
    }

    private suspend fun waitForDownload(manager: DownloadManager, id: Long): Uri =
        withContext(Dispatchers.IO) {
            while (true) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            return@withContext manager.getUriForDownloadedFile(id)
                                ?: error("APK_URI_NOT_FOUND")
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            error("DOWNLOAD_FAILED_$reason")
                        }
                    }
                }
                delay(500)
            }
            @Suppress("UNREACHABLE_CODE")
            error("DOWNLOAD_ABORTED")
        }
}
