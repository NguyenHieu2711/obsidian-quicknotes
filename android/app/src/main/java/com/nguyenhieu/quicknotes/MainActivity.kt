package com.nguyenhieu.quicknotes

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCAL_APP_URL = "http://appassets.androidplatform.net/assets/index.html"
    }

    private lateinit var webView: WebView
    private var activeDownloadId: Long = -1L
    private var activeUpdateApkFile: File? = null
    private var pendingInstallApkFile: File? = null
    private var mandatoryUpdateDialog: AlertDialog? = null
    private var updatePromptDismissedThisSession: Boolean = false
    private var downloadPollJob: Job? = null
    private var installTriggeredForId: Long = -1L
    private var lastBackPressedAt: Long = 0L
    private var pendingVoiceLang: String? = null
    private var pendingLaunchTab: String = ""
    private var pendingFocusInput: Boolean = false

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                }
                VaultRepository.saveSafTreeUri(this, uri)
                pushVaultStatusToWeb()
            }
        }
    }

    private val allFilesPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        VaultRepository.tryAutoDetectVaultFolder(this)
        pushVaultStatusToWeb()
    }

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val lang = pendingVoiceLang
        pendingVoiceLang = null
        if (lang != null) {
            if (granted) {
                launchNativeSpeechRecognizer(lang)
            } else {
                webView.evaluateJavascript("window.__onVoiceState && window.__onVoiceState(false);", null)
                Toast.makeText(this, "Cần cấp quyền Micro để nhập bằng giọng nói.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val voiceResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        webView.evaluateJavascript("window.__onVoiceState && window.__onVoiceState(false);", null)
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val best = matches?.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                val escaped = JSONObject.quote(best)
                webView.evaluateJavascript("window.__onVoiceText && window.__onVoiceText($escaped);", null)
            }
        }
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || completedId != activeDownloadId) return
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            handleDownloadStatusCheck(dm, completedId)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureLaunchExtras(intent)

        ContextCompat.registerReceiver(
            this,
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .setHttpAllowed(true)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        cleanupStaleOtaWebCache()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#17191F"))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            addJavascriptInterface(AndroidWebBridge(), "AndroidBridge")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    serveOtaWebAssetIfNewer(request.url)?.let { return it }
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    dispatchLaunchIntentToWeb()
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(LOCAL_APP_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(window.__handleAndroidBack && window.__handleAndroidBack()) || false") { res ->
                    if (res == "true") return@evaluateJavascript
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAt < 1800L) {
                        finish()
                    } else {
                        lastBackPressedAt = now
                        Toast.makeText(this@MainActivity, "Vuốt lần nữa để thoát", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        lifecycleScope.launch {
            checkForUpdatesFromGitHub(manualCheck = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureLaunchExtras(intent)
        dispatchLaunchIntentToWeb()
    }

    override fun onResume() {
        super.onResume()
        val pending = pendingInstallApkFile
        if (pending != null && pending.exists() && packageManager.canRequestPackageInstalls()) {
            pendingInstallApkFile = null
            promptInstallApk(pending)
            return
        }
        VaultRepository.tryAutoDetectVaultFolder(this)
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.__onAppResumed && window.__onAppResumed();", null)
        }
        lifecycleScope.launch {
            checkForUpdatesFromGitHub(manualCheck = false)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (_: Exception) {
        }
        webView.destroy()
        super.onDestroy()
    }

    private fun captureLaunchExtras(intent: Intent?) {
        val tab = intent?.getStringExtra("open_tab")?.trim().orEmpty()
        if (tab.isNotEmpty()) {
            pendingLaunchTab = tab
            pendingFocusInput = intent?.getBooleanExtra("focus_input", false) == true
        }
    }

    private fun dispatchLaunchIntentToWeb() {
        if (pendingLaunchTab.isNotEmpty() && ::webView.isInitialized) {
            val t = JSONObject.quote(pendingLaunchTab)
            val f = pendingFocusInput
            pendingLaunchTab = ""
            pendingFocusInput = false
            webView.evaluateJavascript("window.__openLaunchTab && window.__openLaunchTab($t, $f);", null)
        }
    }

    private fun pushVaultStatusToWeb() {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript("window.__reloadVaultFromNative && window.__reloadVaultFromNative();", null)
    }

    private fun launchNativeSpeechRecognizer(lang: String) {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.ifBlank { "vi-VN" })
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            webView.evaluateJavascript("window.__onVoiceState && window.__onVoiceState(true);", null)
            voiceResultLauncher.launch(intent)
        } catch (_: Exception) {
            webView.evaluateJavascript("window.__onVoiceState && window.__onVoiceState(false);", null)
            Toast.makeText(this, "Thiết bị chưa hỗ trợ nhận diện giọng nói.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerNativeVibration(durationMs: Long) {
        val clamped = durationMs.coerceIn(5L, 45L)
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(clamped, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // GITHUB HOT-BUNDLE OTA & APK SELF-UPDATE
    // =========================================================================
    private fun getOtaWebDir(): File = File(filesDir, "ota_web")

    private fun getSavedOtaWebVersion(): String {
        return getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(VaultRepository.KEY_OTA_WEB_VERSION, "")?.trim().orEmpty()
    }

    private fun getEffectiveWebVersion(): String {
        val otaVer = getSavedOtaWebVersion()
        return if (otaVer.isNotEmpty() && isRemoteVersionNewer(otaVer, BuildConfig.VERSION_NAME)) {
            otaVer
        } else {
            BuildConfig.VERSION_NAME
        }
    }

    private fun cleanupStaleOtaWebCache() {
        val otaVer = getSavedOtaWebVersion()
        if (otaVer.isEmpty() || !isRemoteVersionNewer(otaVer, BuildConfig.VERSION_NAME)) {
            try {
                getOtaWebDir().deleteRecursively()
            } catch (_: Exception) {
            }
            getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(VaultRepository.KEY_OTA_WEB_VERSION)
                .apply()
        }
    }

    private fun serveOtaWebAssetIfNewer(uri: Uri): WebResourceResponse? {
        if (uri.host != "appassets.androidplatform.net") return null
        val otaVer = getSavedOtaWebVersion()
        if (otaVer.isEmpty() || !isRemoteVersionNewer(otaVer, BuildConfig.VERSION_NAME)) return null

        val relName = uri.path?.removePrefix("/assets/")?.trim('/') ?: return null
        val allowed = setOf("index.html", "style.css", "app.js", "manifest.json")
        if (relName !in allowed) return null

        val f = File(getOtaWebDir(), relName)
        if (!f.exists() || f.length() <= 0L) return null
        val mime = when {
            relName.endsWith(".html") -> "text/html"
            relName.endsWith(".css") -> "text/css"
            relName.endsWith(".js") -> "application/javascript"
            else -> "application/json"
        }
        return try {
            WebResourceResponse(mime, "UTF-8", f.inputStream())
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLatestGithubCommitSha(): String? {
        val apiUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/commits/main"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ObsidianQuick-Android/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("sha", "").trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchRawManifestJson(): JSONObject? {
        val ref = resolveLatestGithubCommitSha() ?: "main"
        val rawUrl = "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/$ref/releases/latest.json?t=${System.currentTimeMillis()}"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ObsidianQuick-Android/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadOtaWebAssets(targetWebVersion: String): Boolean {
        val files = listOf("index.html", "style.css", "app.js", "manifest.json")
        val tmpDir = File(filesDir, "ota_web_tmp")
        return try {
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            val ts = System.currentTimeMillis()
            val ref = resolveLatestGithubCommitSha() ?: "main"
            for (name in files) {
                val u = "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/$ref/static/$name?t=$ts"
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(u).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 8000
                        setRequestProperty("Cache-Control", "no-cache")
                    }
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return false
                    File(tmpDir, name).writeBytes(bytes)
                } finally {
                    conn?.disconnect()
                }
            }
            val finalDir = getOtaWebDir()
            finalDir.deleteRecursively()
            if (!tmpDir.renameTo(finalDir)) {
                finalDir.mkdirs()
                for (name in files) {
                    File(tmpDir, name).copyTo(File(finalDir, name), overwrite = true)
                }
                tmpDir.deleteRecursively()
            }
            getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(VaultRepository.KEY_OTA_WEB_VERSION, targetWebVersion)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun checkForUpdatesFromGitHub(manualCheck: Boolean) {
        val manifestJson = withContext(Dispatchers.IO) { fetchRawManifestJson() }
        val remoteWebVer = manifestJson?.optString("web_version", "")?.trim().orEmpty()
        val tagName = manifestJson?.optString("tag_name", "")?.trim().orEmpty()
        val apkUrl = manifestJson?.optString("apk_url", "")?.trim().orEmpty()

        if (tagName.isNotEmpty() && apkUrl.isNotEmpty() && isRemoteVersionNewer(tagName, BuildConfig.VERSION_NAME)) {
            if (manualCheck || !updatePromptDismissedThisSession) {
                showMandatoryUpdateDialog(tagName, apkUrl)
            }
            return
        }

        if (remoteWebVer.isNotEmpty() && isRemoteVersionNewer(remoteWebVer, getEffectiveWebVersion())) {
            val ok = withContext(Dispatchers.IO) { downloadOtaWebAssets(remoteWebVer) }
            if (ok) {
                webView.reload()
                Toast.makeText(this, "Đã tự cập nhật giao diện v$remoteWebVer", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (manualCheck) {
            val msg = if (manifestJson == null) {
                "Không thể kiểm tra cập nhật lúc này."
            } else {
                "Đang ở bản mới nhất (v${getEffectiveWebVersion()})."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (cleanRemote.isEmpty() || cleanRemote == cleanCurrent) return false
        val rParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun showMandatoryUpdateDialog(tagName: String, apkDownloadUrl: String) {
        if (isFinishing || isDestroyed) return
        mandatoryUpdateDialog?.let { if (it.isShowing) return }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Có bản cập nhật mới ($tagName)")
            .setMessage("Phiên bản $tagName đã sẵn sàng (hiện tại: v${BuildConfig.VERSION_NAME}).")
            .setCancelable(true)
            .setPositiveButton("Cập nhật ngay") { _, _ ->
                showDownloadingLockDialog(tagName)
                startApkDownload(tagName, apkDownloadUrl)
            }
            .setNegativeButton("Để sau") { d, _ ->
                updatePromptDismissedThisSession = true
                mandatoryUpdateDialog = null
                d.dismiss()
            }
            .create()
        mandatoryUpdateDialog = dialog
        dialog.show()
    }

    private fun showDownloadingLockDialog(tagName: String) {
        if (isFinishing || isDestroyed) return
        mandatoryUpdateDialog?.dismiss()
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Đang tải $tagName...")
            .setMessage("Bộ cài đặt sẽ tự động mở ngay khi tải xong.")
            .setCancelable(false)
            .create()
        mandatoryUpdateDialog = progressDialog
        progressDialog.show()
    }

    private fun handleDownloadStatusCheck(dm: DownloadManager, downloadId: Long): Boolean {
        if (downloadId == -1L || installTriggeredForId == downloadId) return true
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (idx >= 0) {
                    when (cursor.getInt(idx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            installTriggeredForId = downloadId
                            downloadPollJob?.cancel()
                            mandatoryUpdateDialog?.dismiss()
                            mandatoryUpdateDialog = null
                            activeUpdateApkFile?.let { if (it.exists()) promptInstallApk(it) }
                            return true
                        }
                        DownloadManager.STATUS_FAILED -> {
                            installTriggeredForId = downloadId
                            downloadPollJob?.cancel()
                            mandatoryUpdateDialog?.dismiss()
                            mandatoryUpdateDialog = null
                            Toast.makeText(this, "Tải bản cập nhật thất bại.", Toast.LENGTH_SHORT).show()
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun startApkDownload(tagName: String, apkUrl: String) {
        try {
            val safeTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "obsidian-quick-$safeTag.apk"
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            downloadsDir.listFiles()?.forEach { if (it.name.endsWith(".apk", true)) it.delete() }
            val targetFile = File(downloadsDir, fileName)
            activeUpdateApkFile = targetFile

            val req = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Cập nhật Obsidian Quick ($tagName)")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            installTriggeredForId = -1L
            activeDownloadId = dm.enqueue(req)

            downloadPollJob?.cancel()
            downloadPollJob = lifecycleScope.launch {
                while (isActive && activeDownloadId != -1L) {
                    delay(600L)
                    if (handleDownloadStatusCheck(dm, activeDownloadId)) break
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi tải APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptInstallApk(apkFile: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstallApkFile = apkFile
            Toast.makeText(this, "Hãy bật quyền Cài đặt ứng dụng không xác định cho Obsidian Quick.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi mở cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // JAVASCRIPT BRIDGE CHO WEBVIEW
    // =========================================================================
    inner class AndroidWebBridge {

        @JavascriptInterface
        fun getFullVaultState(): String {
            val (connected, label) = VaultRepository.getVaultDisplayStatus(this@MainActivity)
            val obj = JSONObject().apply {
                put("connected", connected)
                put("vaultLabel", label)
                put("hasAllFilesAccess", VaultRepository.hasAllFilesAccess())
                put("lastModified", VaultRepository.getLatestVaultModifiedTimestamp(this@MainActivity))
                put("appVersion", getEffectiveWebVersion())
                put("quickNotesMd", VaultRepository.readQuickNotesMd(this@MainActivity))
                put("dailyAnchorJson", VaultRepository.readDailyAnchorJson(this@MainActivity))
                put("taskWarehouseMd", VaultRepository.readTaskWarehouseMd(this@MainActivity))
                put("tagsJson", VaultRepository.readVaultTagsJson(this@MainActivity))
            }
            return obj.toString()
        }

        @JavascriptInterface
        fun saveQuickNotesMd(md: String): Boolean {
            return VaultRepository.saveQuickNotesMd(this@MainActivity, md)
        }

        @JavascriptInterface
        fun saveDailyAnchorJson(jsonStr: String): Boolean {
            return VaultRepository.saveDailyAnchorJson(this@MainActivity, jsonStr)
        }

        @JavascriptInterface
        fun saveTaskWarehouseMd(md: String): Boolean {
            return VaultRepository.saveTaskWarehouseMd(this@MainActivity, md)
        }

        @JavascriptInterface
        fun getVaultModifiedTimestamp(): Long {
            return VaultRepository.getLatestVaultModifiedTimestamp(this@MainActivity)
        }

        @JavascriptInterface
        fun requestAllFilesAccess() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        allFilesPermLauncher.launch(intent)
                    } catch (_: Exception) {
                        allFilesPermLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    VaultRepository.tryAutoDetectVaultFolder(this@MainActivity)
                    pushVaultStatusToWeb()
                }
            }
        }

        @JavascriptInterface
        fun pickVaultFolder() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Bật quyền truy cập tệp để đồng bộ Syncthing tức thì 2 chiều",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                folderPickerLauncher.launch(intent)
            }
        }

        @JavascriptInterface
        fun vibrate(ms: Long) {
            triggerNativeVibration(ms)
        }

        @JavascriptInterface
        fun startVoiceInput(lang: String) {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    launchNativeSpeechRecognizer(lang)
                } else {
                    pendingVoiceLang = lang.ifBlank { "vi-VN" }
                    audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        @JavascriptInterface
        fun startNativeUpdate() {
            runOnUiThread {
                lifecycleScope.launch {
                    checkForUpdatesFromGitHub(manualCheck = true)
                }
            }
        }

        @JavascriptInterface
        fun getAppVersion(): String = getEffectiveWebVersion()

        @JavascriptInterface
        fun getClipboardText(): String {
            return try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun setClipboardText(text: String) {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
                cm.setPrimaryClip(ClipData.newPlainText("ObsidianQuick", text))
            } catch (_: Exception) {
            }
        }

        @JavascriptInterface
        fun syncSystemBars(statusHex: String, navHex: String, lightIcons: Boolean) {
            runOnUiThread {
                try {
                    val sColor = Color.parseColor(statusHex)
                    val nColor = Color.parseColor(navHex)
                    window.statusBarColor = sColor
                    window.navigationBarColor = nColor
                    webView.setBackgroundColor(sColor)
                    val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                    ctrl.isAppearanceLightStatusBars = lightIcons
                    ctrl.isAppearanceLightNavigationBars = lightIcons
                } catch (_: Exception) {
                }
            }
        }
    }
}
