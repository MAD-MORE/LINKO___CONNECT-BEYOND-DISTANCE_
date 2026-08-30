package com.linkshare.app.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.linkshare.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LinkoUpdateManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var progressJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var activeDownloadId = -1L
    private var expectedInstallVersionCode: Int? = null
    private var latestRelease: ReleaseInfo? = null

    private val _state = MutableStateFlow(UpdateState(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkAndOfferUpdate() {
        if (checkJob?.isActive == true) return
        if (_state.value.status in setOf(UpdateStatus.Downloading, UpdateStatus.DownloadComplete, UpdateStatus.Verifying, UpdateStatus.Installing)) return
        checkJob = scope.launch {
            updateState(UpdateStatus.Checking, statusMessage = "CONNECTING TO LINKO UPDATE NETWORK", errorMessage = null)
            val installed = readInstalledVersion()
            updateState(installedVersionCode = installed.first, installedVersionName = installed.second)
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (latest == null) {
                updateState(UpdateStatus.Error, statusMessage = "UPDATE CHECK FAILED", errorMessage = "Unable to reach the latest LINKO release.")
                return@launch
            }
            latestRelease = latest
            if (latest.versionCode > installed.first) {
                updateState(UpdateStatus.UpdateAvailable, latestVersionCode = latest.versionCode, latestVersionName = latest.versionName, statusMessage = "NEW LINKO BUILD FOUND", errorMessage = null)
            } else {
                updateState(UpdateStatus.UpToDate, latestVersionCode = latest.versionCode, latestVersionName = latest.versionName, statusMessage = "LINKO IS UP TO DATE", errorMessage = null)
            }
        }
    }

    fun startUpdate() {
        val release = latestRelease ?: return
        if (release.versionCode <= _state.value.installedVersionCode || _state.value.status == UpdateStatus.Downloading) return
        downloadAndInstall(release)
    }

    fun retry() {
        if (_state.value.status == UpdateStatus.Error) checkAndOfferUpdate()
    }

    fun cancelUpdate() {
        if (activeDownloadId >= 0L) {
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { manager.remove(activeDownloadId) }
        }
        activeDownloadId = -1L
        progressJob?.cancel()
        unregisterReceiver()
        latestRelease?.let { updateState(UpdateStatus.UpdateAvailable, latestVersionCode = it.versionCode, latestVersionName = it.versionName, statusMessage = "UPDATE READY") }
            ?: updateState(UpdateStatus.Idle, statusMessage = "UPDATE CHECK READY")
    }

    fun onInstallerReturned() {
        val expected = expectedInstallVersionCode ?: return
        val installed = readInstalledVersion()
        updateState(installedVersionCode = installed.first, installedVersionName = installed.second)
        if (installed.first >= expected) {
            expectedInstallVersionCode = null
            updateState(UpdateStatus.Installed, statusMessage = "LINKO UPDATED", errorMessage = null)
            scope.launch {
                delay(1800L)
                if (_state.value.status == UpdateStatus.Installed) updateState(UpdateStatus.UpToDate, statusMessage = "LINKO IS UP TO DATE")
            }
        } else if (_state.value.status == UpdateStatus.Installing) {
            updateState(UpdateStatus.UpdateAvailable, statusMessage = "UPDATE READY", errorMessage = "Installation was cancelled or did not complete.")
        }
    }

    private suspend fun fetchLatestRelease(): ReleaseInfo? = runCatching {
        val releaseConnection = openConnection(RELEASES_API)
        try {
            if (releaseConnection.responseCode !in 200..299) return@runCatching null
            val release = JSONObject(releaseConnection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.optJSONArray("assets") ?: return@runCatching null
            val manifestUrl = findAssetUrl(assets, UPDATE_MANIFEST_NAME) ?: return@runCatching null
            val manifestConnection = openConnection(manifestUrl)
            try {
                if (manifestConnection.responseCode !in 200..299) return@runCatching null
                val manifest = JSONObject(manifestConnection.inputStream.bufferedReader().use { it.readText() })
                if (manifest.optInt("schemaVersion", -1) != 1) return@runCatching null
                val versionCode = manifest.optLong("versionCode", -1L).takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: return@runCatching null
                val versionName = manifest.optString("versionName").takeIf { it.isNotBlank() } ?: return@runCatching null
                val apkAsset = manifest.optString("apkAsset").takeIf { it.isNotBlank() } ?: return@runCatching null
                val commit = manifest.optString("commit").takeIf { it.isNotBlank() }
                val apkUrl = findAssetUrl(assets, apkAsset) ?: return@runCatching null
                require(apkUrl.startsWith("https://"))
                ReleaseInfo(versionCode, versionName, apkUrl, commit)
            } finally { manifestConnection.disconnect() }
        } finally { releaseConnection.disconnect() }
    }.getOrNull()

    private fun openConnection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        useCaches = false
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "LINKO-Updater")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("Pragma", "no-cache")
    }

    private fun findAssetUrl(assets: JSONArray, name: String): String? {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") == name) return asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
        }
        return null
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        if (context !is Activity || context.isFinishing || context.isDestroyed) return
        unregisterReceiver()
        progressJob?.cancel()
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("LINKO ${release.versionName}")
            .setDescription("Downloading LINKO update…")
            .setMimeType(APK_MIME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "LINKO-${release.versionCode}.apk")
        activeDownloadId = runCatching { manager.enqueue(request) }.getOrElse {
            updateState(UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "Could not start the update download.")
            return
        }
        updateState(UpdateStatus.Downloading, latestVersionCode = release.versionCode, latestVersionName = release.versionName, downloadedBytes = 0, totalBytes = 0, progressPercent = 0, statusMessage = "DOWNLOADING LINKO UPDATE", errorMessage = null, downloadId = activeDownloadId)
        val expectedId = activeDownloadId
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == expectedId) handleDownloadComplete(manager, expectedId, release)
            }
        }
        ContextCompat.registerReceiver(appContext, receiver!!, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)
        progressJob = scope.launch(Dispatchers.IO) {
            while (isActive && activeDownloadId == expectedId) {
                val snapshot = queryDownload(manager, expectedId)
                withContext(Dispatchers.Main) {
                    if (snapshot != null && activeDownloadId == expectedId && _state.value.status == UpdateStatus.Downloading) updateState(downloadedBytes = snapshot.downloaded, totalBytes = snapshot.total, progressPercent = snapshot.percent)
                }
                delay(250L)
            }
        }
    }

    private fun handleDownloadComplete(manager: DownloadManager, id: Long, release: ReleaseInfo) {
        if (id != activeDownloadId) return
        progressJob?.cancel()
        val result = queryDownload(manager, id)
        if (result == null || result.status != DownloadManager.STATUS_SUCCESSFUL) {
            activeDownloadId = -1L; unregisterReceiver()
            updateState(UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "The LINKO update download did not complete successfully.")
            return
        }
        val uri = manager.getUriForDownloadedFile(id)
        activeDownloadId = -1L; unregisterReceiver()
        if (uri == null) {
            updateState(UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "The downloaded LINKO APK could not be opened.")
            return
        }
        updateState(UpdateStatus.DownloadComplete, downloadedBytes = result.downloaded, totalBytes = result.total, progressPercent = 100, statusMessage = "UPDATE RECEIVED")
        scope.launch {
            delay(200L)
            updateState(UpdateStatus.Verifying, statusMessage = "VERIFYING LINKO PACKAGE")
            val error = withContext(Dispatchers.IO) { validateApk(uri, release) }
            if (error != null) { updateState(UpdateStatus.Error, statusMessage = "UPDATE VERIFICATION FAILED", errorMessage = error); return@launch }
            expectedInstallVersionCode = release.versionCode
            updateState(UpdateStatus.Installing, statusMessage = "INSTALLING LINKO")
            installApk(uri)
        }
    }

    private fun validateApk(uri: Uri, release: ReleaseInfo): String? = runCatching {
        require(uri.scheme == "content" || uri.scheme == "file") { "Invalid APK URI." }
        val info = appContext.packageManager.getPackageArchiveInfo(uri.toString(), PackageManager.GET_META_DATA)
            ?: return@runCatching "The downloaded file is not a readable Android package."
        require(info.packageName == appContext.packageName) { "The downloaded package is not LINKO." }
        require(info.longVersionCode >= release.versionCode.toLong()) { "The downloaded APK is older than the advertised update." }
        null
    }.getOrElse { it.message ?: "The LINKO APK failed validation." }

    private fun installApk(uri: Uri) {
        val activity = context as? Activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            updateState(UpdateStatus.Error, statusMessage = "INSTALLATION PERMISSION REQUIRED", errorMessage = "Allow LINKO to install updates, then press UPDATE again.")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, APK_MIME); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { activity.startActivity(intent) }.onFailure {
            expectedInstallVersionCode = null
            updateState(UpdateStatus.Error, statusMessage = "INSTALLATION FAILED", errorMessage = "Android could not start the installer: ${it.message ?: "unknown error"}")
        }
    }

    private fun queryDownload(manager: DownloadManager, id: Long): DownloadSnapshot? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val percent = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
            DownloadSnapshot(downloaded, total, percent, status)
        }
    }

    private fun readInstalledVersion(): Pair<Int, String> = runCatching {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.longVersionCode.toInt() to info.versionName.orEmpty()
    }.getOrDefault(BuildConfig.VERSION_CODE to BuildConfig.VERSION_NAME)

    private fun unregisterReceiver() { receiver?.let { runCatching { appContext.unregisterReceiver(it) } }; receiver = null }

    private fun updateState(
        status: UpdateStatus = _state.value.status,
        installedVersionCode: Int = _state.value.installedVersionCode,
        installedVersionName: String = _state.value.installedVersionName,
        latestVersionCode: Int? = _state.value.latestVersionCode,
        latestVersionName: String? = _state.value.latestVersionName,
        downloadedBytes: Long = _state.value.downloadedBytes,
        totalBytes: Long = _state.value.totalBytes,
        progressPercent: Int = _state.value.progressPercent,
        statusMessage: String = _state.value.statusMessage,
        errorMessage: String? = _state.value.errorMessage,
        downloadId: Long = _state.value.downloadId
    ) {
        _state.value = UpdateState(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progressPercent = progressPercent,
            statusMessage = statusMessage,
            errorMessage = errorMessage,
            downloadId = downloadId,
            status = status
        )
    }

    data class UpdateState(
        val installedVersionCode: Int,
        val installedVersionName: String,
        val latestVersionCode: Int? = null,
        val latestVersionName: String? = null,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val progressPercent: Int = 0,
        val statusMessage: String = "",
        val errorMessage: String? = null,
        val downloadId: Long = -1L,
        val status: UpdateStatus = UpdateStatus.Idle
    )

    enum class UpdateStatus { Idle, Checking, UpToDate, UpdateAvailable, Downloading, DownloadComplete, Verifying, Installing, Installed, Error }
    private data class ReleaseInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val commit: String?)
    private data class DownloadSnapshot(val downloaded: Long, val total: Long, val percent: Int, val status: Int)

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val UPDATE_MANIFEST_NAME = "linko-update.json"
        private const val RELEASES_API = "https://api.github.com/repos/MAD-MORE/LINKO___CONNECT-BEYOND-DISTANCE_/releases/latest"
    }
}
