package com.linkshare.app.update

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

/** Development updater: installs newer LINKO release builds over the existing app. */
class LinkoUpdateManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    fun checkAndOfferUpdate() {
        scope.launch {
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch
            if (latest.versionCode <= BuildConfig.VERSION_CODE) return@launch
            if ((context as? android.app.Activity)?.isFinishing == true) return@launch
            showUpdateDialog(latest)
        }
    }

    private suspend fun fetchLatestRelease(): ReleaseInfo? = runCatching {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LINKO-Updater")
        }
        if (connection.responseCode !in 200..299) return null
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name")
        val versionCode = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                break
            }
        }
        apkUrl?.let { ReleaseInfo(versionCode, tag, it) }
    }.getOrNull()

    private fun showUpdateDialog(release: ReleaseInfo) {
        val activity = context as? android.app.Activity ?: return
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 8, 56, 8)
        }
        val message = TextView(activity).apply {
            text = "A newer LINKO build is ready.\n\nInstalled: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nLatest: ${release.tag} (${release.versionCode})"
            gravity = Gravity.CENTER
        }
        container.addView(message)
        AlertDialog.Builder(activity)
            .setTitle("LINKO UPDATE AVAILABLE")
            .setView(container)
            .setNegativeButton("LATER", null)
            .setPositiveButton("UPDATE") { _, _ -> downloadAndInstall(release) }
            .show()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        val activity = context as? android.app.Activity ?: return
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("LINKO ${release.tag}")
            .setDescription("Downloading LINKO update…")
            .setMimeType(APK_MIME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "LINKO-${release.versionCode}.apk")

        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }
        val status = TextView(activity).apply { text = "Starting download…" }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        dialogView.addView(status)
        dialogView.addView(progress)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("UPDATING LINKO")
            .setView(dialogView)
            .setCancelable(false)
            .show()

        val downloadId = manager.enqueue(request)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                progressJob?.cancel()
                appContext.unregisterReceiver(this)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                val success = cursor.use { it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL }
                dialog.dismiss()
                if (success) {
                    val uri = manager.getUriForDownloadedFile(downloadId)
                    if (uri != null) installApk(uri) else showFailure("The update file could not be opened.")
                } else {
                    showFailure("LINKO update download failed. Please try again.")
                }
            }
        }
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)

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
                            status.text = "Downloading update… $percent%"
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun installApk(uri: Uri) {
        val activity = context as? android.app.Activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            showFailure("Allow LINKO to install updates, then press UPDATE again.")
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
        (context as? android.app.Activity)?.runOnUiThread {
            AlertDialog.Builder(context).setTitle("LINKO UPDATE").setMessage(message).setPositiveButton("OK", null).show()
        }
    }

    private data class ReleaseInfo(val versionCode: Int, val tag: String, val apkUrl: String)

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val RELEASES_API = "https://api.github.com/repos/MAD-MORE/LINKO___CONNECT-BEYOND-DISTANCE_/releases/latest"
    }
}
