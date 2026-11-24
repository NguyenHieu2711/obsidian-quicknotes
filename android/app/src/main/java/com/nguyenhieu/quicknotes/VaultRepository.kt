package com.nguyenhieu.quicknotes

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object VaultRepository {

    private val memCache = ConcurrentHashMap<String, String>()
    private val safWriteExecutor = Executors.newSingleThreadExecutor()

    const val PREFS_NAME = "obsidian_quick_prefs"
    const val KEY_VAULT_PATH = "vault_fs_path"
    const val KEY_VAULT_SAF_URI = "vault_saf_uri"
    const val KEY_OTA_WEB_VERSION = "ota_web_version"

    const val REL_QUICK_NOTES = "0. Inbox/⚡ Quick Notes.md"
    const val REL_DAILY_ANCHOR = "9. System/99. Nhà kho chứa Scripts Dataview/Backup/daily-focus-anchor.json"
    const val REL_TASK_WAREHOUSE = "1. Actions/📁 0. Kế hoạch & Lịch trình/✅ Nhà kho chứa Task.md"
    const val REL_TAGS_INDEX = ".obsidian/plugins/.quicknotes-sync/tags-index.json"

    private val DEFAULT_QUICK_NOTES_MD = """
        # ⚡ Ghi nhanh
        # 🎯 Đang giải quyết
        ## Bảng cảm giác phấn đấu
        ## Những điều cốt lõi bản thân
        # 📥 Kho lưu trữ chung
        ## Kho Notes chung
    """.trimIndent() + "\n"

    private val DEFAULT_DAILY_ANCHOR_JSON = """
        {
          "mode": "col",
          "visible": true,
          "customTasks": [],
          "pinnedRefs": [],
          "hiddenAutoKeys": [],
          "attachments": []
        }
    """.trimIndent()

    private val DEFAULT_TASK_WAREHOUSE_MD = """
        # ✅ Nhà kho chứa Task
        ## 📥 1. Hộp tiếp nhận (Chờ phân loại)
    """.trimIndent() + "\n"

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun mirrorDir(context: Context): File {
        val d = File(context.filesDir, "vault_mirror")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun getSavedVaultFsDir(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VAULT_PATH, "")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val f = File(raw)
        return if (f.exists() && f.isDirectory) f else null
    }

    fun getSavedSafTree(context: Context): DocumentFile? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VAULT_SAF_URI, "")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return try {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(raw))
            if (doc != null && doc.exists() && doc.canRead()) doc else null
        } catch (_: Exception) {
            null
        }
    }

    fun tryAutoDetectVaultFolder(context: Context): File? {
        if (!hasAllFilesAccess()) return null
        val existing = getSavedVaultFsDir(context)
        if (existing != null) return existing

        val root = Environment.getExternalStorageDirectory() ?: return null
        val candidates = mutableListOf<File>()

        fun checkDir(d: File): Boolean {
            if (!d.isDirectory || d.name.startsWith(".")) return false
            val hasObsidian = File(d, ".obsidian").isDirectory
            val hasInbox = File(d, "0. Inbox").isDirectory
            return hasObsidian || (hasInbox && d.name.contains("Obsidian", ignoreCase = true))
        }

        val level1 = root.listFiles().orEmpty()
        for (d1 in level1) {
            if (!d1.isDirectory || d1.name.equals("Android", ignoreCase = true)) continue
            if (checkDir(d1)) candidates.add(d1)
            val level2 = d1.listFiles().orEmpty()
            for (d2 in level2) {
                if (checkDir(d2)) candidates.add(d2)
                val level3 = d2.listFiles().orEmpty()
                for (d3 in level3) {
                    if (checkDir(d3)) candidates.add(d3)
                }
            }
        }

        val best = candidates.sortedByDescending { d ->
            var score = 0
            if (File(d, ".obsidian").isDirectory) score += 10
            if (File(d, "0. Inbox").isDirectory) score += 10
            if (d.name.contains("Bản chính", ignoreCase = true)) score += 20
            score
        }.firstOrNull()

        if (best != null) {
            saveVaultFsPath(context, best.absolutePath)
        }
        return best
    }

    fun saveVaultFsPath(context: Context, absPath: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VAULT_PATH, absPath)
            .apply()
    }

    fun saveSafTreeUri(context: Context, treeUri: Uri) {
        val resolvedFs = resolveTreeUriToFilePath(treeUri)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("saf_doc_uri_")) edit.remove(k)
        }
        edit.putString(KEY_VAULT_SAF_URI, treeUri.toString())
        if (resolvedFs != null && File(resolvedFs).isDirectory && hasAllFilesAccess()) {
            edit.putString(KEY_VAULT_PATH, resolvedFs)
        }
        edit.apply()
    }

    private fun resolveTreeUriToFilePath(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":")
            if (parts.isNotEmpty() && parts[0].equals("primary", ignoreCase = true)) {
                val rel = if (parts.size > 1) parts[1].trim('/') else ""
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (rel.isEmpty()) base else "$base/$rel"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getVaultDisplayStatus(context: Context): Pair<Boolean, String> {
        val fsDir = getSavedVaultFsDir(context) ?: tryAutoDetectVaultFolder(context)
        if (fsDir != null) {
            return Pair(true, fsDir.name)
        }
        val saf = getSavedSafTree(context)
        if (saf != null) {
            return Pair(true, saf.name ?: "Vault (SAF)")
        }
        return Pair(false, "Chưa chọn thư mục Vault Syncthing")
    }

    fun getLatestVaultModifiedTimestamp(context: Context): Long {
        val mDir = mirrorDir(context)
        val m1 = File(mDir, REL_QUICK_NOTES).takeIf { it.exists() }?.lastModified() ?: 0L
        val m2 = File(mDir, REL_DAILY_ANCHOR).takeIf { it.exists() }?.lastModified() ?: 0L
        val m3 = File(mDir, REL_TASK_WAREHOUSE).takeIf { it.exists() }?.lastModified() ?: 0L
        val mirrorMax = maxOf(m1, m2, m3)

        val fsDir = getSavedVaultFsDir(context) ?: return mirrorMax
        val f1 = File(fsDir, REL_QUICK_NOTES).takeIf { it.exists() }?.lastModified() ?: 0L
        val f2 = File(fsDir, REL_DAILY_ANCHOR).takeIf { it.exists() }?.lastModified() ?: 0L
        val f3 = File(fsDir, REL_TASK_WAREHOUSE).takeIf { it.exists() }?.lastModified() ?: 0L
        return maxOf(mirrorMax, f1, f2, f3)
    }

    private fun mergeSyncthingConflictsIfAny(vaultRoot: File) {
        try {
            val inboxDir = File(vaultRoot, "0. Inbox")
            if (!inboxDir.isDirectory) return
            val mainFile = File(inboxDir, "⚡ Quick Notes.md")
            val conflicts = inboxDir.listFiles { f ->
                f.isFile && f.name.startsWith("⚡ Quick Notes.sync-conflict-") && f.name.endsWith(".md")
            }.orEmpty()
            if (conflicts.isEmpty()) return

            val baseText = if (mainFile.exists()) mainFile.readText(Charsets.UTF_8) else DEFAULT_QUICK_NOTES_MD
            val existingLines = baseText.lines().map { it.trim() }.filter { it.startsWith("- ") }.toMutableSet()
            val extraBullets = mutableListOf<String>()

            for (cf in conflicts) {
                val cfLines = cf.readText(Charsets.UTF_8).lines()
                for (ln in cfLines) {
                    val t = ln.trim()
                    if (t.startsWith("- ") && t !in existingLines) {
                        existingLines.add(t)
                        extraBullets.add("- ${t.removePrefix("- ").trim()}")
                    }
                }
                cf.delete()
            }

            if (extraBullets.isNotEmpty()) {
                val merged = buildString {
                    append(baseText.trimEnd())
                    append("\n")
                    if (!baseText.contains("# ⚡ Ghi nhanh")) {
                        append("# ⚡ Ghi nhanh\n")
                    }
                    extraBullets.forEach { append(it).append("\n") }
                }
                atomicWriteFs(mainFile, merged)
            }
        } catch (_: Exception) {
        }
    }

    private fun atomicWriteFs(target: File, content: String) {
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun getRawSavedSafTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VAULT_SAF_URI, "")?.trim().orEmpty()
        return if (raw.isNotEmpty()) try { Uri.parse(raw) } catch (_: Exception) { null } else null
    }

    private fun resolveDirectSafDocUri(context: Context, treeUri: Uri, relPath: String): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cacheKey = "saf_doc_uri_$relPath"
        val cached = prefs.getString(cacheKey, "")?.trim().orEmpty()
        if (cached.isNotEmpty()) {
            return try { Uri.parse(cached) } catch (_: Exception) { null }
        }
        return try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val cleanRel = relPath.trim('/')
            val childDocId = if (rootDocId.endsWith(":") || rootDocId.endsWith("/")) {
                "$rootDocId$cleanRel"
            } else {
                "$rootDocId/$cleanRel"
            }
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheSafDocUri(context: Context, relPath: String, docUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("saf_doc_uri_$relPath", docUri.toString())
            .apply()
    }

    private fun syncMirrorQuietly(context: Context, relPath: String, content: String) {
        memCache[relPath] = content
        try {
            val mf = File(mirrorDir(context), relPath)
            if (!mf.exists() || mf.length() != content.toByteArray(Charsets.UTF_8).size.toLong()) {
                atomicWriteFs(mf, content)
            }
        } catch (_: Exception) {
        }
    }

    fun readRelFileFastMirror(context: Context, relPath: String, fallbackContent: String): String {
        memCache[relPath]?.let { return it }
        val mf = File(mirrorDir(context), relPath)
        if (mf.exists() && mf.isFile) {
            return try {
                val txt = mf.readText(Charsets.UTF_8)
                memCache[relPath] = txt
                txt
            } catch (_: Exception) {
                fallbackContent
            }
        }
        return readRelFile(context, relPath, fallbackContent)
    }

    /**
     * Optimistic 0.5ms write-behind: updates RAM + local SSD mirror immediately,
     * refreshes ONLY the affected Widget ListView without layout tear-down,
     * and flushes to Syncthing FS / SAF asynchronously on a single FIFO background thread.
     */
    fun writeRelFileOptimistic(context: Context, relPath: String, content: String): Boolean {
        val appCtx = context.applicationContext
        memCache[relPath] = content
        try {
            atomicWriteFs(File(mirrorDir(appCtx), relPath), content)
        } catch (_: Exception) {
        }

        // Immediate <20ms list-only refresh for the exact widget kind that changed!
        when (relPath) {
            REL_QUICK_NOTES -> DualWidgetContract.refreshListOnly(appCtx, DualWidgetContract.KIND_NOTES)
            REL_DAILY_ANCHOR, REL_TASK_WAREHOUSE -> DualWidgetContract.refreshListOnly(appCtx, DualWidgetContract.KIND_TASKS)
        }

        // Flush to real Syncthing Vault file asynchronously (never blocks Widget UI!)
        safWriteExecutor.execute {
            flushToExternalVault(appCtx, relPath, content)
        }
        return true
    }

    private fun flushToExternalVault(context: Context, relPath: String, content: String): Boolean {
        val fsDir = getSavedVaultFsDir(context) ?: tryAutoDetectVaultFolder(context)
        if (fsDir != null) {
            return try {
                atomicWriteFs(File(fsDir, relPath), content)
                true
            } catch (_: Exception) {
                false
            }
        }

        val treeUri = getRawSavedSafTreeUri(context)
        if (treeUri != null) {
            val directUri = resolveDirectSafDocUri(context, treeUri, relPath)
            if (directUri != null) {
                try {
                    val out = context.contentResolver.openOutputStream(directUri, "wt")
                    if (out != null) {
                        out.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                        cacheSafDocUri(context, relPath, directUri)
                        return true
                    }
                } catch (_: Exception) {
                }
            }
            val safRoot = getSavedSafTree(context)
            if (safRoot != null) {
                return try {
                    val doc = findSafFile(safRoot, relPath, createIfMissing = true) ?: return false
                    cacheSafDocUri(context, relPath, doc.uri)
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(content)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return true
    }

    fun syncFromExternalVaultAsync(context: Context, relPaths: List<String>) {
        val appCtx = context.applicationContext
        safWriteExecutor.execute {
            var notesChanged = false
            var tasksChanged = false
            for (rel in relPaths) {
                val prev = memCache[rel]
                val fresh = when (rel) {
                    REL_QUICK_NOTES -> readRelFile(appCtx, rel, DEFAULT_QUICK_NOTES_MD)
                    REL_DAILY_ANCHOR -> readRelFile(appCtx, rel, DEFAULT_DAILY_ANCHOR_JSON)
                    REL_TASK_WAREHOUSE -> readRelFile(appCtx, rel, DEFAULT_TASK_WAREHOUSE_MD)
                    else -> continue
                }
                if (prev != null && prev != fresh) {
                    if (rel == REL_QUICK_NOTES) notesChanged = true else tasksChanged = true
                }
            }
            if (notesChanged) DualWidgetContract.refreshListOnly(appCtx, DualWidgetContract.KIND_NOTES)
            if (tasksChanged) DualWidgetContract.refreshListOnly(appCtx, DualWidgetContract.KIND_TASKS)
        }
    }

    private fun readRelFile(context: Context, relPath: String, fallbackContent: String): String {
        val fsDir = getSavedVaultFsDir(context) ?: tryAutoDetectVaultFolder(context)
        if (fsDir != null) {
            if (relPath == REL_QUICK_NOTES) {
                mergeSyncthingConflictsIfAny(fsDir)
            }
            val f = File(fsDir, relPath)
            if (f.exists() && f.isFile) {
                return try {
                    val txt = f.readText(Charsets.UTF_8)
                    syncMirrorQuietly(context, relPath, txt)
                    txt
                } catch (_: Exception) {
                    fallbackContent
                }
            }
            val mirror = File(mirrorDir(context), relPath)
            if (mirror.exists() && relPath == REL_QUICK_NOTES) {
                val mText = mirror.readText(Charsets.UTF_8)
                atomicWriteFs(f, mText)
                return mText
            }
            return fallbackContent
        }

        val treeUri = getRawSavedSafTreeUri(context)
        if (treeUri != null) {
            // Fast O(1) direct URI read (skips slow DocumentFile directory scan!)
            val directUri = resolveDirectSafDocUri(context, treeUri, relPath)
            if (directUri != null) {
                try {
                    val text = context.contentResolver.openInputStream(directUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (text != null) {
                        syncMirrorQuietly(context, relPath, text)
                        return text
                    }
                } catch (_: Exception) {
                }
            }
            // Fallback once if file path wasn't direct-mapped yet
            val safRoot = getSavedSafTree(context)
            if (safRoot != null) {
                val doc = findSafFile(safRoot, relPath, createIfMissing = false)
                if (doc != null) {
                    cacheSafDocUri(context, relPath, doc.uri)
                    return try {
                        val txt = context.contentResolver.openInputStream(doc.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: fallbackContent
                        syncMirrorQuietly(context, relPath, txt)
                        txt
                    } catch (_: Exception) {
                        fallbackContent
                    }
                }
            }
            return fallbackContent
        }

        val mirror = File(mirrorDir(context), relPath)
        return if (mirror.exists()) {
            try {
                mirror.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                fallbackContent
            }
        } else {
            fallbackContent
        }
    }

    private fun writeRelFile(context: Context, relPath: String, content: String): Boolean {
        return writeRelFileOptimistic(context, relPath, content)
    }

    private fun findSafFile(root: DocumentFile, relPath: String, createIfMissing: Boolean): DocumentFile? {
        val parts = relPath.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var cur: DocumentFile = root
        for (i in 0 until parts.size - 1) {
            val seg = parts[i]
            val next = cur.findFile(seg) ?: if (createIfMissing) cur.createDirectory(seg) else null
            cur = next ?: return null
        }
        val fileName = parts.last()
        val existing = cur.findFile(fileName)
        if (existing != null) return existing
        if (!createIfMissing) return null
        val mime = if (fileName.endsWith(".json")) "application/json" else "text/markdown"
        return cur.createFile(mime, fileName)
    }

    fun readQuickNotesMd(context: Context): String = readRelFile(context, REL_QUICK_NOTES, DEFAULT_QUICK_NOTES_MD)
    fun readQuickNotesMdFast(context: Context): String = readRelFileFastMirror(context, REL_QUICK_NOTES, DEFAULT_QUICK_NOTES_MD)
    fun saveQuickNotesMd(context: Context, md: String): Boolean = writeRelFile(context, REL_QUICK_NOTES, md)

    fun readDailyAnchorJson(context: Context): String = readRelFile(context, REL_DAILY_ANCHOR, DEFAULT_DAILY_ANCHOR_JSON)
    fun readDailyAnchorJsonFast(context: Context): String = readRelFileFastMirror(context, REL_DAILY_ANCHOR, DEFAULT_DAILY_ANCHOR_JSON)
    fun saveDailyAnchorJson(context: Context, jsonStr: String): Boolean {
        // Non-breaking merge on fast mirror/RAM so saving takes <1ms
        return try {
            val currentRaw = readDailyAnchorJsonFast(context)
            val baseObj = try { JSONObject(currentRaw) } catch (_: Exception) { JSONObject() }
            val incoming = JSONObject(jsonStr)
            if (incoming.has("customTasks")) {
                baseObj.put("customTasks", incoming.getJSONArray("customTasks"))
            } else {
                val keys = incoming.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    baseObj.put(k, incoming.get(k))
                }
            }
            writeRelFile(context, REL_DAILY_ANCHOR, baseObj.toString(2))
        } catch (_: Exception) {
            writeRelFile(context, REL_DAILY_ANCHOR, jsonStr)
        }
    }

    fun readTaskWarehouseMd(context: Context): String = readRelFile(context, REL_TASK_WAREHOUSE, DEFAULT_TASK_WAREHOUSE_MD)
    fun readTaskWarehouseMdFast(context: Context): String = readRelFileFastMirror(context, REL_TASK_WAREHOUSE, DEFAULT_TASK_WAREHOUSE_MD)
    fun saveTaskWarehouseMd(context: Context, md: String): Boolean = writeRelFile(context, REL_TASK_WAREHOUSE, md)

    fun readVaultTagsJson(context: Context): String {
        val raw = readRelFile(context, REL_TAGS_INDEX, "[]")
        return try {
            if (raw.trim().startsWith("{")) {
                val obj = JSONObject(raw)
                obj.optJSONArray("tags")?.toString() ?: "[]"
            } else if (raw.trim().startsWith("[")) {
                raw
            } else {
                "[]"
            }
        } catch (_: Exception) {
            "[]"
        }
    }

    fun listActiveNoteStreams(context: Context): List<Pair<String, String>> {
        val md = readQuickNotesMd(context)
        val result = mutableListOf<Pair<String, String>>()
        var curGroup = ""
        for (rawLine in md.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("# ") && !line.startsWith("## ")) {
                val h1 = line.removePrefix("# ").trim()
                curGroup = when {
                    h1.contains("Đang giải quyết", ignoreCase = true) -> "active"
                    h1.contains("Kho", ignoreCase = true) -> "archive"
                    else -> ""
                }
            } else if (line.startsWith("## ") && curGroup.isNotEmpty()) {
                val title = line.removePrefix("## ").trim()
                if (title.isNotEmpty()) {
                    result.add(Pair(curGroup, title))
                }
            }
        }
        if (result.none { it.first == "active" }) {
            result.add(0, Pair("active", "Bảng cảm giác phấn đấu"))
        }
        if (result.none { it.first == "archive" }) {
            result.add(Pair("archive", "Kho Notes chung"))
        }
        return result
    }

    fun appendCaptureItem(
        context: Context,
        rawText: String,
        targetType: String,
        streamTitle: String = ""
    ): Boolean {
        val clean = rawText.trim().replace("\r\n", " ").replace("\n", " ")
        if (clean.isEmpty()) return false
        val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        when (targetType) {
            "quick" -> {
                val md = readQuickNotesMd(context)
                val lines = md.lines().toMutableList()
                var quickIdx = lines.indexOfFirst { it.trim().startsWith("# ") && it.contains("Ghi nhanh", ignoreCase = true) }
                if (quickIdx == -1) {
                    lines.add(0, "# ⚡ Ghi nhanh")
                    quickIdx = 0
                }
                lines.add(quickIdx + 1, "- $hhmm · $clean")
                return saveQuickNotesMd(context, lines.joinToString("\n").trimEnd() + "\n")
            }
            "stream" -> {
                val md = readQuickNotesMd(context)
                val lines = md.lines().toMutableList()
                val targetIdx = lines.indexOfFirst {
                    it.trim().equals("## $streamTitle", ignoreCase = true)
                }
                if (targetIdx >= 0) {
                    var insertAt = targetIdx + 1
                    while (insertAt < lines.size && !lines[insertAt].trim().startsWith("#")) {
                        insertAt++
                    }
                    lines.add(insertAt, "- $hhmm · $clean")
                } else {
                    lines.add("## $streamTitle")
                    lines.add("- $hhmm · $clean")
                }
                return saveQuickNotesMd(context, lines.joinToString("\n").trimEnd() + "\n")
            }
            "focus_task" -> {
                val rawJson = readDailyAnchorJson(context)
                val root = try { JSONObject(rawJson) } catch (_: Exception) { JSONObject() }
                val arr = root.optJSONArray("customTasks") ?: JSONArray()
                val newTask = JSONObject().apply {
                    put("id", "cf_${System.currentTimeMillis()}")
                    put("text", clean)
                    put("why", "")
                    put("status", "doing")
                    put("isCore", false)
                    put("notePath", JSONObject.NULL)
                    put("subLabel", "")
                    put("subTasks", JSONArray())
                }
                arr.put(newTask)
                root.put("customTasks", arr)
                return saveDailyAnchorJson(context, root.toString(2))
            }
            "warehouse_task" -> {
                val md = readTaskWarehouseMd(context)
                val lines = md.lines().toMutableList()
                val inboxIdx = lines.indexOfFirst { it.contains("Hộp tiếp nhận", ignoreCase = true) }
                if (inboxIdx >= 0) {
                    var insertAt = inboxIdx + 1
                    while (insertAt < lines.size && lines[insertAt].trim().startsWith("*(")) {
                        insertAt++
                    }
                    lines.add(insertAt, "- [ ] $clean")
                } else {
                    lines.add("- [ ] $clean")
                }
                return saveTaskWarehouseMd(context, lines.joinToString("\n").trimEnd() + "\n")
            }
        }
        return false
    }

    fun notifyWidgets(context: Context) {
        DualWidgetContract.refreshAllWidgets(context)
    }
}
