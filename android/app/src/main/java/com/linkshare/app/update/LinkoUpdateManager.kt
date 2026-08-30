package com.linkshare.app.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import java.io.File
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
    private val cache = appContext.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UpdateState(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkAndOfferUpdate() {
        if (checkJob?.isActive == true) return
        if (_state.value.status in setOf(UpdateStatus.Downloading, UpdateStatus.DownloadComplete, UpdateStatus.Verifying, UpdateStatus.Installing)) return
        checkJob = scope.launch {
            updateState(status = UpdateStatus.Checking, statusMessage = "CONNECTING TO LINKO UPDATE NETWORK", errorMessage = null, usingCachedData = false)
            val installed = readInstalledVersion()
            updateState(installedVersionCode = installed.first, installedVersionName = installed.second)
            when (val result = withContext(Dispatchers.IO) { discoverLatestRelease() }) {
                is UpdateDiscoveryResult.Success -> {
                    latestRelease = result.release
                    cacheRelease(result.release)
                    publishReleaseState(result.release, installed, fromCache = false)
                }
                else -> {
                    val cached = readCachedRelease()
                    if (cached != null) {
                        latestRelease = cached
                        publishReleaseState(cached, installed, fromCache = true)
                        updateState(
                            statusMessage = "LATEST RELEASE TEMPORARILY UNREACHABLE",
                            errorMessage = "${result.userMessage()} Last known build: ${cached.versionName} (${cached.versionCode}).",
                            usingCachedData = true
                        )
                    } else {
                        latestRelease = null
                        updateState(
                            status = UpdateStatus.Error,
                            statusMessage = "UPDATE CHECK FAILED",
                            errorMessage = result.userMessage(),
                            usingCachedData = false
                        )
                    }
                }
            }
        }
    }

    fun startUpdate() {
        val release = latestRelease ?: return
        if (release.versionCode <= _state.value.installedVersionCode || _state.value.status == UpdateStatus.Downloading) return
        downloadAndInstall(release)
    }

    fun retry() {
        if (_state.value.status == UpdateStatus.Error || _state.value.errorMessage != null) checkAndOfferUpdate()
    }

    fun cancelUpdate() {
        if (activeDownloadId >= 0L) {
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { manager.remove(activeDownloadId) }
        }
        activeDownloadId = -1L
        progressJob?.cancel()
        unregisterReceiver()
        latestRelease?.let {
            updateState(status = UpdateStatus.UpdateAvailable, latestVersionCode = it.versionCode, latestVersionName = it.versionName, statusMessage = "UPDATE READY", errorMessage = null, downloadId = -1L, usingCachedData = false)
        } ?: updateState(status = UpdateStatus.Idle, statusMessage = "UPDATE CHECK READY", errorMessage = null, downloadId = -1L, usingCachedData = false)
    }

    fun onInstallerReturned() {
        val expected = expectedInstallVersionCode ?: return
        val installed = readInstalledVersion()
        updateState(installedVersionCode = installed.first, installedVersionName = installed.second)
        if (installed.first >= expected) {
            expectedInstallVersionCode = null
            updateState(status = UpdateStatus.Installed, statusMessage = "LINKO UPDATED", errorMessage = null, usingCachedData = false)
            scope.launch {
                delay(1800L)
                if (_state.value.status == UpdateStatus.Installed) {
                    updateState(status = UpdateStatus.UpToDate, statusMessage = "LINKO IS UP TO DATE", errorMessage = null, usingCachedData = false)
                }
            }
        } else if (_state.value.status == UpdateStatus.Installing) {
            updateState(status = UpdateStatus.UpdateAvailable, statusMessage = "UPDATE READY", errorMessage = "Installation was cancelled or did not complete.", usingCachedData = false)
        }
    }

    private suspend fun discoverLatestRelease(): UpdateDiscoveryResult = try {
        val releaseResponse = getText(RELEASES_API, "latest release", GITHUB_JSON_ACCEPT)
        if (releaseResponse.code !in 200..299) {
            return UpdateDiscoveryResult.HttpError("latest release", releaseResponse.code, releaseResponse.errorMessage())
        }
        val release = runCatching { JSONObject(releaseResponse.body) }.getOrElse {
            return UpdateDiscoveryResult.ParseError("latest release", "GitHub returned invalid release JSON.")
        }
        val assets = release.optJSONArray("assets")
            ?: return UpdateDiscoveryResult.ValidationError("latest release", "The latest release does not contain an assets list.")
        val manifestAsset = findAsset(assets, UPDATE_MANIFEST_NAME)
            ?: return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST MISSING")
        val manifestUrl = manifestAsset.apiUrl ?: manifestAsset.browserUrl
            ?: return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST URL INVALID")
        if (!manifestUrl.startsWith("https://")) {
            return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST URL INVALID")
        }
        val manifestAccept = if (manifestAsset.apiUrl != null && manifestUrl == manifestAsset.apiUrl) GITHUB_ASSET_ACCEPT else GITHUB_JSON_ACCEPT
        val manifestResponse = getText(manifestUrl, "update manifest", manifestAccept)
        if (manifestResponse.code !in 200..299) {
            return UpdateDiscoveryResult.HttpError("update manifest", manifestResponse.code, manifestResponse.errorMessage())
        }
        val manifest = runCatching { JSONObject(manifestResponse.body) }.getOrElse {
            return UpdateDiscoveryResult.ParseError("update manifest", "UPDATE MANIFEST INVALID JSON")
        }
        if (manifest.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA) {
            return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST SCHEMA UNSUPPORTED")
        }
        val versionLong = manifest.optLong("versionCode", -1L)
        if (versionLong !in 1L..Int.MAX_VALUE.toLong()) {
            return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST VERSION INVALID")
        }
        val versionName = manifest.optString("versionName").trim()
        if (versionName.isBlank()) {
            return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE MANIFEST VERSION NAME INVALID")
        }
        val apkAsset = manifest.optString("apkAsset").trim()
        if (apkAsset.isBlank()) {
            return UpdateDiscoveryResult.ValidationError("manifest", "UPDATE APK ASSET NAME INVALID")
        }
        val commit = manifest.optString("commit").takeIf { it.isNotBlank() }
        val apk = findAsset(assets, apkAsset)
            ?: return UpdateDiscoveryResult.ValidationError("apk asset", "UPDATE APK ASSET MISSING")
        val apkUrl = apk.browserUrl ?: apk.apiUrl
            ?: return UpdateDiscoveryResult.ValidationError("apk asset", "UPDATE APK URL INVALID")
        if (!apkUrl.startsWith("https://")) {
            return UpdateDiscoveryResult.ValidationError("apk asset", "UPDATE APK URL INVALID")
        }
        UpdateDiscoveryResult.Success(ReleaseInfo(versionLong.toInt(), versionName, apkUrl, commit))
    } catch (t: Throwable) {
        UpdateDiscoveryResult.NetworkError("latest release", t.safeMessage())
    }

    private fun getText(url: String, stage: String, accept: String): HttpResponse {
        if (!url.startsWith("https://")) return HttpResponse(400, "", "HTTPS is required for $stage.")
        var current = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            val connection = runCatching { openConnection(current, accept) }.getOrElse {
                throw UpdateNetworkException(stage, it.safeMessage())
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (hop == MAX_REDIRECTS) return HttpResponse(code, "", "Too many redirects while fetching $stage.")
                    val location = connection.getHeaderField("Location") ?: return HttpResponse(code, "", "Redirect location missing while fetching $stage.")
                    if (!location.startsWith("https://")) return HttpResponse(code, "", "Unsafe redirect rejected while fetching $stage.")
                    current = location
                } else {
                    val body = if (code in 200..299) readBounded(connection.inputStream) else readBounded(connection.errorStream)
                    return HttpResponse(code, body, connection.responseMessage ?: "")
                }
            } finally {
                connection.disconnect()
            }
        }
        return HttpResponse(599, "", "Unable to resolve $stage.")
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        useCaches = false
        instanceFollowRedirects = false
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "LINKO-Updater")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("Pragma", "no-cache")
    }

    private fun readBounded(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        stream.use { input ->
            val buffer = ByteArray(8 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("Response exceeded the update metadata size limit.")
                out.write(buffer, 0, count)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun findAsset(assets: JSONArray, name: String): AssetInfo? {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") == name) {
                val browser = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                val api = asset.optString("url").takeIf { it.startsWith("https://") }
                return AssetInfo(browser, api)
            }
        }
        return null
    }

    private fun publishReleaseState(release: ReleaseInfo, installed: Pair<Int, String>, fromCache: Boolean) {
        val status = if (release.versionCode > installed.first) UpdateStatus.UpdateAvailable else UpdateStatus.UpToDate
        updateState(
            status = status,
            latestVersionCode = release.versionCode,
            latestVersionName = release.versionName,
            statusMessage = if (status == UpdateStatus.UpdateAvailable) "NEW LINKO BUILD FOUND" else "LINKO IS UP TO DATE",
            errorMessage = null,
            usingCachedData = fromCache
        )
    }

    private fun cacheRelease(release: ReleaseInfo) {
        cache.edit()
            .putInt(CACHE_VERSION_CODE, release.versionCode)
            .putString(CACHE_VERSION_NAME, release.versionName)
            .putString(CACHE_APK_URL, release.apkUrl)
            .putString(CACHE_COMMIT, release.commit)
            .putLong(CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun readCachedRelease(): ReleaseInfo? {
        val time = cache.getLong(CACHE_TIME, 0L)
        if (time <= 0L || System.currentTimeMillis() - time > CACHE_TTL_MS) return null
        val code = cache.getInt(CACHE_VERSION_CODE, -1)
        val name = cache.getString(CACHE_VERSION_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val apkUrl = cache.getString(CACHE_APK_URL, null)?.takeIf { it.startsWith("https://") } ?: return null
        if (code <= 0) return null
        return ReleaseInfo(code, name, apkUrl, cache.getString(CACHE_COMMIT, null))
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
            updateState(status = UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "Could not start the update download.")
            return
        }
        updateState(
            status = UpdateStatus.Downloading,
            latestVersionCode = release.versionCode,
            latestVersionName = release.versionName,
            downloadedBytes = 0,
            totalBytes = 0,
            progressPercent = 0,
            statusMessage = "DOWNLOADING LINKO UPDATE",
            errorMessage = null,
            downloadId = activeDownloadId,
            usingCachedData = false
        )
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
                    if (snapshot != null && activeDownloadId == expectedId && _state.value.status == UpdateStatus.Downloading) {
                        updateState(downloadedBytes = snapshot.downloaded, totalBytes = snapshot.total, progressPercent = snapshot.percent)
                        if (snapshot.status == DownloadManager.STATUS_FAILED) handleDownloadComplete(manager, expectedId, release)
                    }
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
            activeDownloadId = -1L
            unregisterReceiver()
            updateState(status = UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "The LINKO update download did not complete successfully.")
            return
        }
        val uri = manager.getUriForDownloadedFile(id)
        activeDownloadId = -1L
        unregisterReceiver()
        if (uri == null) {
            updateState(status = UpdateStatus.Error, statusMessage = "UPDATE DOWNLOAD FAILED", errorMessage = "The downloaded LINKO APK could not be opened.")
            return
        }
        updateState(status = UpdateStatus.DownloadComplete, downloadedBytes = result.downloaded, totalBytes = result.total, progressPercent = 100, statusMessage = "UPDATE RECEIVED")
        scope.launch {
            delay(200L)
            updateState(status = UpdateStatus.Verifying, statusMessage = "VERIFYING LINKO PACKAGE")
            val error = withContext(Dispatchers.IO) { validateApk(uri, release) }
            if (error != null) {
                updateState(status = UpdateStatus.Error, statusMessage = "UPDATE VERIFICATION FAILED", errorMessage = error)
                return@launch
            }
            expectedInstallVersionCode = release.versionCode
            updateState(status = UpdateStatus.Installing, statusMessage = "INSTALLING LINKO", errorMessage = null)
            installApk(uri)
        }
    }

    private fun validateApk(uri: Uri, release: ReleaseInfo): String? {
        var tempFile: File? = null
        return runCatching {
            require(uri.scheme == "content" || uri.scheme == "file") { "Invalid APK URI." }
            val mime = appContext.contentResolver.getType(uri)
            require(mime == null || mime == APK_MIME || mime == "application/octet-stream") { "The downloaded file has an invalid APK MIME type." }
            tempFile = File.createTempFile("linko-update-", ".apk", appContext.cacheDir)
            appContext.contentResolver.openInputStream(uri)?.use { input -> tempFile!!.outputStream().use { output -> input.copyTo(output) } }
                ?: return@runCatching "The downloaded LINKO APK is not readable."
            val info = appContext.packageManager.getPackageArchiveInfo(tempFile!!.absolutePath, PackageManager.GET_META_DATA)
                ?: return@runCatching "The downloaded file is not a readable Android package."
            require(info.packageName == appContext.packageName) { "The downloaded package is not LINKO." }
            require(info.longVersionCode == release.versionCode.toLong()) { "The downloaded APK version does not match the update manifest." }
            null
        }.getOrElse { it.message ?: "The LINKO APK failed validation." }.also {
            runCatching { tempFile?.delete() }
        }
    }

    private fun installApk(uri: Uri) {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            updateState(status = UpdateStatus.Error, statusMessage = "INSTALLATION FAILED", errorMessage = "LINKO is not in a state where Android can open the installer.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            updateState(status = UpdateStatus.Error, statusMessage = "INSTALLATION PERMISSION REQUIRED", errorMessage = "Allow LINKO to install updates, then press UPDATE again.")
            runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }.onFailure {
            expectedInstallVersionCode = null
            updateState(status = UpdateStatus.Error, statusMessage = "INSTALLATION FAILED", errorMessage = "Android could not start the installer.")
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

    private fun unregisterReceiver() {
        receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        receiver = null
    }

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
        downloadId: Long = _state.value.downloadId,
        usingCachedData: Boolean = _state.value.usingCachedData
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
            usingCachedData = usingCachedData,
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
        val usingCachedData: Boolean = false,
        val status: UpdateStatus = UpdateStatus.Idle
    )

    enum class UpdateStatus { Idle, Checking, UpToDate, UpdateAvailable, Downloading, DownloadComplete, Verifying, Installing, Installed, Error }

    private data class ReleaseInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val commit: String?)
    private data class AssetInfo(val browserUrl: String?, val apiUrl: String?)
    private data class DownloadSnapshot(val downloaded: Long, val total: Long, val percent: Int, val status: Int)
    private data class HttpResponse(val code: Int, val body: String, val message: String) {
        fun errorMessage(): String = when (code) {
            403 -> "GitHub refused the request (403). Check for a temporary API rate limit."
            404 -> "GitHub could not find the requested release data (404)."
            429 -> "GitHub rate limit reached (429). Try again shortly."
            in 500..599 -> "GitHub is temporarily unavailable ($code)."
            else -> if (message.isNotBlank()) message else "HTTP $code while fetching the update."
        }
    }

    private sealed interface UpdateDiscoveryResult {
        data class Success(val release: ReleaseInfo) : UpdateDiscoveryResult
        data class HttpError(val stage: String, val code: Int, val message: String) : UpdateDiscoveryResult
        data class NetworkError(val stage: String, val message: String) : UpdateDiscoveryResult
        data class ParseError(val stage: String, val message: String) : UpdateDiscoveryResult
        data class ValidationError(val stage: String, val message: String) : UpdateDiscoveryResult

        fun userMessage(): String = when (this) {
            is HttpError -> when (code) {
                401 -> "GitHub authentication/API access failed (401)."
                403 -> "GitHub rate limit or access restriction reached (403)."
                404 -> "The latest LINKO release could not be found (404)."
                429 -> "GitHub rate limit reached (429). Try again shortly."
                in 500..599 -> "GitHub is temporarily unavailable ($code)."
                else -> "GitHub returned HTTP $code while fetching $stage."
            }
            is NetworkError -> "Could not reach GitHub while fetching $stage: $message"
            is ParseError -> message
            is ValidationError -> message
            is Success -> ""
        }
    }

    private class UpdateNetworkException(val stage: String, message: String) : Exception(message)

    private fun Throwable.safeMessage(): String = message?.take(180)?.ifBlank { "unknown network error" } ?: "unknown network error"

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val UPDATE_MANIFEST_NAME = "linko-update.json"
        private const val RELEASES_API = "https://api.github.com/repos/MAD-MORE/LINKO___CONNECT-BEYOND-DISTANCE_/releases/latest"
        private const val GITHUB_JSON_ACCEPT = "application/vnd.github+json"
        private const val GITHUB_ASSET_ACCEPT = "application/octet-stream"
        private const val SUPPORTED_SCHEMA = 1
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val CACHE_NAME = "linko_update_cache"
        private const val CACHE_VERSION_CODE = "versionCode"
        private const val CACHE_VERSION_NAME = "versionName"
        private const val CACHE_APK_URL = "apkUrl"
        private const val CACHE_COMMIT = "commit"
        private const val CACHE_TIME = "cachedAt"
    }
}
