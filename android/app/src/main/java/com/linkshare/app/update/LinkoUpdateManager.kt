package com.linkshare.app.update

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.linkshare.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LinkoUpdateManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var checkJob: Job? = null
    private var progressJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var dialogShowing = false

    fun checkAndOfferUpdate() {
        if (checkJob?.isActive == true || dialogShowing) return
        checkJob = scope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (latest == null || latest.versionCode <= BuildConfig.VERSION_CODE) return@launch
            val activity = context as? Activity
            if (activity == null || activity.isFinishing || activity.isDestroyed) return@launch
            showUpdateDialog(activity, latest)
        }
    }

    private suspend fun fetchLatestRelease(): ReleaseInfo? = runCatching {
        val releaseConnection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LINKO-Updater")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            if (releaseConnection.responseCode !in 200..299) return@runCatching null
            val release = JSONObject(releaseConnection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.optJSONArray("assets") ?: return@runCatching null
            var manifestUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") == UPDATE_MANIFEST_NAME) {
                    manifestUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
            val url = manifestUrl ?: return@runCatching null
            val manifestConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LINKO-Updater")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            try {
                if (manifestConnection.responseCode !in 200..299) return@runCatching null
                val manifest = JSONObject(manifestConnection.inputStream.bufferedReader().use { it.readText() })
                val versionCode = manifest.optLong("versionCode", -1L)
                    .takeIf { it in 1..Int.MAX_VALUE }
                    ?.toInt()
                    ?: return@runCatching null
                val versionName = manifest.optString("versionName").takeIf { it.isNotBlank() }
                    ?: release.optString("name").ifBlank { release.optString("tag_name") }
                val apkAsset = manifest.optString("apkAsset", APK_ASSET_NAME)
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name") == apkAsset) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
                apkUrl ?: return@runCatching null
                ReleaseInfo(versionCode, versionName, apkUrl)
            } finally {
                manifestConnection.disconnect()
            }
        } finally {
            releaseConnection.disconnect()
        }
    }.getOrNull()

    private fun showUpdateDialog(activity: Activity, release: ReleaseInfo) {
        if (dialogShowing || activity.isFinishing || activity.isDestroyed) return
        dialogShowing = true
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 8, 56, 8)
        }
        box.addView(TextView(activity).apply {
            text = "Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nLatest: ${release.versionName} (${release.versionCode})\n\nA newer signed LINKO build is available."
            gravity = Gravity.CENTER
        })
        AlertDialog.Builder(activity)
            .setTitle("LINKO UPDATE AVAILABLE")
            .setView(box)
            .setNegativeButton("LATER") { _, _ -> dialogShowing = false }
            .setPositiveButton("UPDATE") { _, _ ->
                dialogShowing = false
                downloadAndInstall(release)
            }
            .setOnDismissListener { dialogShowing = false }
            .show()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        val activity = context as? Activity ?: return
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("LINKO ${release.versionName}")
            .setDescription("Downloading LINKO update…")
            .setMimeType(APK_MIME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "LINKO-${release.versionCode}.apk")

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }
        val status = TextView(activity).apply { text = "Preparing build ${release.versionCode}…" }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val details = TextView(activity).apply { text = "Build ${release.versionCode}\nStarting download…" }
        box.addView(status)
        box.addView(progress)
        box.addView(details)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("UPDATING LINKO")
            .setView(box)
            .setNegativeButton("CANCEL") { _, _ -> progressJob?.cancel() }
            .create()
        dialog.show()

        val downloadId = runCatching { manager.enqueue(request) }.getOrElse {
            dialog.dismiss()
            showFailure("Could not start the LINKO update download.")
            return
        }

        receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                progressJob?.cancel()
                runCatching { appContext.unregisterReceiver(this) }
                receiver = null
                val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                val success = cursor.use {
                    it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (!success) {
                    dialog.dismiss()
                    showFailure("LINKO update download failed. Please try again.")
                    return
                }
                progress.progress = 100
                status.text = "Download complete ✓"
                details.text = "Build ${release.versionCode}\nReady to install."
                dialog.dismiss()
                manager.getUriForDownloadedFile(downloadId)?.let(::installApk)
                    ?: showFailure("The downloaded LINKO APK could not be opened.")
            }
        }
        ContextCompat.registerReceiver(appContext, receiver!!, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)

        progressJob = scope.launch {
            while (isActive) {
                val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                cursor.use {
                    if (it.moveToFirst()) {
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            progress.progress = percent
                            status.text = "Downloading build ${release.versionCode}… $percent%"
                            details.text = "${formatBytes(downloaded)} / ${formatBytes(total)}"
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun installApk(uri: Uri) {
        val activity = context as? Activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            showFailure("Allow LINKO to install updates, then return and press UPDATE again.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { showFailure("Android could not start the installer: ${it.message ?: "unknown error"}") }
    }

    private fun showFailure(message: String) {
        (context as? Activity)?.runOnUiThread {
            AlertDialog.Builder(context).setTitle("LINKO UPDATE").setMessage(message).setPositiveButton("OK", null).show()
        }
    }

    private fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format("%.1f MB", bytes / 1048576.0)

    private data class ReleaseInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val APK_ASSET_NAME = "app-release.apk"
        private const val UPDATE_MANIFEST_NAME = "linko-update.json"
        private const val RELEASES_API = "https://api.github.com/repos/MAD-MORE/LINKO___CONNECT-BEYOND-DISTANCE_/releases/latest"
    }
}
