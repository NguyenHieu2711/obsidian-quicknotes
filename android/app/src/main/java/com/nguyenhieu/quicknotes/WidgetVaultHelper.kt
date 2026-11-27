package com.nguyenhieu.quicknotes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupOption(
    val key: String,
    val label: String,
    val sectionHint: String = "",
    val count: Int = 0
)

data class NoteWidgetItem(
    val uid: String,
    val groupKey: String,
    val groupLabel: String,
    val isUnassigned: Boolean,
    val time: String,
    val cleanText: String,
    val starred: Boolean,
    val recencyRank: Int
)

data class TaskWidgetItem(
    val uid: String,
    val groupKey: String,
    val groupLabel: String,
    val cleanText: String,
    val done: Boolean,
    val starred: Boolean,
    val recencyRank: Int,
    val remindAt: String = ""
)

object WidgetVaultHelper {

    const val GROUP_ALL = "__ALL__"
    const val GROUP_QUICK = "__QUICK__"
    const val GROUP_FOCUS = "__FOCUS__"
    const val GROUP_WAIT = "WH:Việc chờ"

    const val SORT_NEWEST = "newest"
    const val SORT_NAME = "name"
    const val SORT_STAR = "star"

    private const val STAR_PREFIX = "★ "

    private val viCollator: Collator by lazy {
        Collator.getInstance(Locale("vi", "VN")).apply { strength = Collator.PRIMARY }
    }

    // =========================================================================
    // PER-WIDGET PREFERENCES (GROUP FILTER & SORT MODE)
    // =========================================================================
    fun getWidgetGroupFilter(context: Context, appWidgetId: Int, defaultGroup: String = GROUP_ALL): String {
        val prefs = context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("w_grp_$appWidgetId", defaultGroup)?.takeIf { it.isNotBlank() } ?: defaultGroup
    }

    fun setWidgetGroupFilter(context: Context, appWidgetId: Int, groupKey: String) {
        context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("w_grp_$appWidgetId", groupKey)
            .apply()
    }

    fun getWidgetSortMode(context: Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("w_sort_$appWidgetId", SORT_NEWEST).orEmpty()
        return if (raw == SORT_NAME || raw == SORT_STAR) raw else SORT_NEWEST
    }

    fun setWidgetSortMode(context: Context, appWidgetId: Int, sortMode: String) {
        context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("w_sort_$appWidgetId", sortMode)
            .apply()
    }

    fun cycleWidgetSortMode(context: Context, appWidgetId: Int): String {
        val next = when (getWidgetSortMode(context, appWidgetId)) {
            SORT_NEWEST -> SORT_NAME
            SORT_NAME -> SORT_STAR
            else -> SORT_NEWEST
        }
        setWidgetSortMode(context, appWidgetId, next)
        return next
    }

    fun clearWidgetPrefs(context: Context, appWidgetIds: IntArray) {
        val edit = context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            edit.remove("w_grp_$id")
            edit.remove("w_sort_$id")
        }
        edit.apply()
    }

    fun sortBadgeText(sortMode: String): String {
        return when (sortMode) {
            SORT_NAME -> "⇅ Tên"
            SORT_STAR -> "⇅ Sao ★"
            else -> "⇅ Mới"
        }
    }

    // =========================================================================
    // INTERNAL STRUCTURED QUICK-NOTES PARSER / SERIALIZER
    // =========================================================================
    private data class RawNoteEntry(
        var time: String,
        var rawText: String,
        val children: MutableList<String> = mutableListOf()
    )

    private data class RawNoteStream(
        val section: String, // "active" or "archive"
        val title: String,
        val items: MutableList<RawNoteEntry> = mutableListOf()
    )

    private data class ParsedNotesDoc(
        val quickItems: MutableList<RawNoteEntry>,
        val streams: MutableList<RawNoteStream>
    )

    private val timeBulletRegex = Regex("""^(\d{1,2}:\d{2})\s*[·•\-]\s*(.+)$""")
    private val childIndentRegex = Regex("""^(\t+| {2,})-\s+(.+)$""")

    private fun parseNotesDoc(md: String): ParsedNotesDoc {
        val quickItems = mutableListOf<RawNoteEntry>()
        val streams = mutableListOf<RawNoteStream>()
        var section = ""
        var currentStream: RawNoteStream? = null
        var lastParent: RawNoteEntry? = null

        val lines = md.replace("\r\n", "\n").split("\n")
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed == "---") continue

            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                val h1 = trimmed.removePrefix("# ").trim()
                currentStream = null
                lastParent = null
                section = when {
                    h1.contains("Ghi nhanh", ignoreCase = true) || h1.contains("Nháp", ignoreCase = true) -> "quick"
                    h1.contains("Kho", ignoreCase = true) || h1.contains("Lưu trữ", ignoreCase = true) -> "archive"
                    else -> "active"
                }
                continue
            }

            if (trimmed.startsWith("## ")) {
                val title = trimmed.removePrefix("## ").trim()
                if (title.isEmpty()) continue
                val grp = if (section == "archive") "archive" else "active"
                currentStream = RawNoteStream(grp, title)
                streams.add(currentStream)
                lastParent = null
                continue
            }

            val childMatch = childIndentRegex.find(rawLine)
            if (childMatch != null) {
                val childTxt = childMatch.groupValues[2].trim()
                if (childTxt.isNotEmpty() && lastParent != null) {
                    lastParent.children.add(childTxt)
                }
                continue
            }

            if (trimmed.startsWith("- ")) {
                val afterDash = trimmed.removePrefix("- ").trim()
                val m = timeBulletRegex.find(afterDash)
                val time = m?.groupValues?.get(1).orEmpty()
                val text = (m?.groupValues?.get(2) ?: afterDash).trim()
                if (text.isEmpty()) continue
                val entry = RawNoteEntry(time, text)
                if (section == "quick" || (currentStream == null && section != "active" && section != "archive")) {
                    quickItems.add(entry)
                    lastParent = entry
                } else {
                    if (currentStream == null) {
                        val defTitle = if (section == "archive") "Kho Notes chung" else "Bảng cảm giác phấn đấu"
                        currentStream = RawNoteStream(if (section == "archive") "archive" else "active", defTitle)
                        streams.add(currentStream)
                    }
                    currentStream.items.add(entry)
                    lastParent = entry
                }
            }
        }

        if (streams.none { it.section == "active" }) {
            streams.add(0, RawNoteStream("active", "Bảng cảm giác phấn đấu"))
            streams.add(1, RawNoteStream("active", "Những điều cốt lõi bản thân"))
        }
        if (streams.none { it.section == "archive" }) {
            streams.add(RawNoteStream("archive", "Kho Notes chung"))
        }

        return ParsedNotesDoc(quickItems, streams)
    }

    private fun serializeNotesDoc(doc: ParsedNotesDoc): String {
        val out = mutableListOf<String>()
        out.add("# ⚡ Ghi nhanh")
        for (it in doc.quickItems) {
            val pfx = if (it.time.isNotEmpty()) "${it.time} · " else ""
            out.add("- $pfx${it.rawText}")
            for (ch in it.children) out.add("\t- $ch")
        }

        out.add("# 🎯 Đang giải quyết")
        for (st in doc.streams.filter { it.section == "active" }) {
            out.add("## ${st.title}")
            for (it in st.items) {
                val pfx = if (it.time.isNotEmpty()) "${it.time} · " else ""
                out.add("- $pfx${it.rawText}")
                for (ch in it.children) out.add("\t- $ch")
            }
        }

        out.add("# 📥 Kho lưu trữ chung")
        for (st in doc.streams.filter { it.section == "archive" }) {
            out.add("## ${st.title}")
            for (it in st.items) {
                val pfx = if (it.time.isNotEmpty()) "${it.time} · " else ""
                out.add("- $pfx${it.rawText}")
                for (ch in it.children) out.add("\t- $ch")
            }
        }

        return out.joinToString("\n") + "\n"
    }

    private fun stripStar(raw: String): Pair<Boolean, String> {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("★")) {
            Pair(true, trimmed.removePrefix("★").trim())
        } else {
            Pair(false, trimmed)
        }
    }

    private fun applyStar(clean: String, starred: Boolean): String {
        val base = clean.trim().removePrefix("★").trim()
        return if (starred) "$STAR_PREFIX$base" else base
    }

    private fun timeMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        return h * 60 + m
    }

    // =========================================================================
    // NOTES PUBLIC API FOR WIDGET
    // =========================================================================
    fun listNoteGroups(context: Context, fastMirror: Boolean = false): List<GroupOption> {
        val md = if (fastMirror) VaultRepository.readQuickNotesMdFast(context) else VaultRepository.readQuickNotesMd(context)
        val doc = parseNotesDoc(md)
        val total = doc.quickItems.size + doc.streams.sumOf { it.items.size }
        val out = mutableListOf<GroupOption>()
        out.add(GroupOption(GROUP_ALL, "Tất cả nhóm", "", total))
        out.add(GroupOption(GROUP_QUICK, "Ghi nhanh (Chưa gắn nhóm)", "Nháp", doc.quickItems.size))
        for (st in doc.streams) {
            val secLabel = if (st.section == "archive") "Kho lưu trữ" else "Đang giải quyết"
            out.add(GroupOption(st.title, st.title, secLabel, st.items.size))
        }
        return out
    }

    fun resolveNoteGroupHeaderTitle(context: Context, groupFilter: String): String {
        return when (groupFilter) {
            GROUP_ALL -> "Notes · Tất cả ▾"
            GROUP_QUICK -> "Ghi nhanh ▾"
            else -> "$groupFilter ▾"
        }
    }

    fun loadNotesForWidget(context: Context, groupFilter: String, sortMode: String, fastMirror: Boolean = false): List<NoteWidgetItem> {
        val md = if (fastMirror) VaultRepository.readQuickNotesMdFast(context) else VaultRepository.readQuickNotesMd(context)
        val doc = parseNotesDoc(md)
        val all = mutableListOf<NoteWidgetItem>()

        // Stream items: chronological order top -> bottom (higher recencyRank = newer at bottom)
        for ((stIdx, st) in doc.streams.withIndex()) {
            for ((idx, entry) in st.items.withIndex()) {
                val (starred, clean) = stripStar(entry.rawText)
                val uid = "NS|${st.title}|$idx|${entry.rawText.hashCode()}"
                all.add(
                    NoteWidgetItem(
                        uid = uid,
                        groupKey = st.title,
                        groupLabel = st.title,
                        isUnassigned = false,
                        time = entry.time,
                        cleanText = clean,
                        starred = starred,
                        recencyRank = (stIdx + 1) * 10000 + idx
                    )
                )
            }
        }

        // Quick items: appended at bottom, placed after streams so newly captured notes stay at the bottom
        for ((idx, entry) in doc.quickItems.withIndex()) {
            val (starred, clean) = stripStar(entry.rawText)
            val uid = "NQ|$idx|${entry.rawText.hashCode()}"
            all.add(
                NoteWidgetItem(
                    uid = uid,
                    groupKey = GROUP_QUICK,
                    groupLabel = "+ Gắn nhóm",
                    isUnassigned = true,
                    time = entry.time,
                    cleanText = clean,
                    starred = starred,
                    recencyRank = 1_000_000 + idx
                )
            )
        }

        val filtered = when (groupFilter) {
            GROUP_ALL -> all
            GROUP_QUICK -> all.filter { it.groupKey == GROUP_QUICK }
            else -> all.filter { it.groupKey.equals(groupFilter, ignoreCase = true) }
        }

        // ListView uses stackFromBottom=true (chat style): bottom item (last index) is newest & immediately visible
        return when (sortMode) {
            SORT_NAME -> filtered.sortedWith { a, b ->
                viCollator.compare(a.cleanText, b.cleanText)
            }
            SORT_STAR -> filtered.sortedWith(
                compareBy<NoteWidgetItem> { it.starred }
                    .thenBy { it.recencyRank }
            )
            else -> filtered.sortedBy { it.recencyRank }
        }
    }

    private fun locateNote(doc: ParsedNotesDoc, uid: String): Pair<MutableList<RawNoteEntry>, Int>? {
        val parts = uid.split("|")
        if (parts.isEmpty()) return null
        if (parts[0] == "NQ" && parts.size >= 3) {
            val idx = parts[1].toIntOrNull() ?: -1
            val hash = parts[2].toIntOrNull()
            if (idx in doc.quickItems.indices && (hash == null || doc.quickItems[idx].rawText.hashCode() == hash)) {
                return Pair(doc.quickItems, idx)
            }
            if (hash != null) {
                val found = doc.quickItems.indexOfFirst { it.rawText.hashCode() == hash }
                if (found >= 0) return Pair(doc.quickItems, found)
            }
        } else if (parts[0] == "NS" && parts.size >= 4) {
            val streamTitle = parts[1]
            val idx = parts[2].toIntOrNull() ?: -1
            val hash = parts[3].toIntOrNull()
            val st = doc.streams.find { it.title.equals(streamTitle, ignoreCase = true) } ?: return null
            if (idx in st.items.indices && (hash == null || st.items[idx].rawText.hashCode() == hash)) {
                return Pair(st.items, idx)
            }
            if (hash != null) {
                val found = st.items.indexOfFirst { it.rawText.hashCode() == hash }
                if (found >= 0) return Pair(st.items, found)
            }
        }
        return null
    }

    fun addNoteFromWidget(context: Context, text: String, targetGroupKey: String, starred: Boolean): Boolean {
        val clean = text.trim().replace("\r\n", " ").replace("\n", " ")
        if (clean.isEmpty()) return false
        val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val finalRaw = applyStar(clean, starred)
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))

        if (targetGroupKey.isBlank() || targetGroupKey == GROUP_ALL || targetGroupKey == GROUP_QUICK) {
            doc.quickItems.add(RawNoteEntry(hhmm, finalRaw))
        } else {
            var st = doc.streams.find { it.title.equals(targetGroupKey, ignoreCase = true) }
            if (st == null) {
                st = RawNoteStream("active", targetGroupKey.trim())
                doc.streams.add(0, st)
            }
            st.items.add(RawNoteEntry(hhmm, finalRaw))
        }
        return VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
    }

    fun toggleNoteStar(context: Context, uid: String): Boolean {
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))
        val (list, idx) = locateNote(doc, uid) ?: return false
        val entry = list[idx]
        val (isStar, clean) = stripStar(entry.rawText)
        entry.rawText = applyStar(clean, !isStar)
        return VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
    }

    fun assignNoteToGroup(context: Context, uid: String, targetGroupKey: String, newSectionIfCreated: String = "active"): Boolean {
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))
        val (srcList, idx) = locateNote(doc, uid) ?: return false
        val moved = srcList.removeAt(idx)
        if (moved.time.isEmpty()) {
            moved.time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
        if (targetGroupKey == GROUP_QUICK) {
            doc.quickItems.add(moved)
        } else {
            var dest = doc.streams.find { it.title.equals(targetGroupKey, ignoreCase = true) }
            if (dest == null) {
                dest = RawNoteStream(newSectionIfCreated, targetGroupKey.trim())
                doc.streams.add(dest)
            }
            dest.items.add(moved)
        }
        return VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
    }

    fun convertNoteToFocusTask(context: Context, uid: String): Boolean {
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))
        val (srcList, idx) = locateNote(doc, uid) ?: return false
        val removed = srcList.removeAt(idx)
        val (starred, clean) = stripStar(removed.rawText)
        VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
        return addTaskFromWidget(context, clean, GROUP_FOCUS, starred)
    }

    fun updateNoteFromWidget(context: Context, uid: String, newCleanText: String, starred: Boolean): Boolean {
        val clean = newCleanText.trim().replace("\r\n", " ").replace("\n", " ")
        if (clean.isEmpty()) return false
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))
        val (list, idx) = locateNote(doc, uid) ?: return false
        list[idx].rawText = applyStar(clean, starred)
        return VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
    }

    fun deleteNoteFromWidget(context: Context, uid: String): Boolean {
        val doc = parseNotesDoc(VaultRepository.readQuickNotesMdFast(context))
        val (list, idx) = locateNote(doc, uid) ?: return false
        list.removeAt(idx)
        return VaultRepository.saveQuickNotesMd(context, serializeNotesDoc(doc))
    }

    fun findNoteByUid(context: Context, uid: String, fastMirror: Boolean = false): NoteWidgetItem? {
        return loadNotesForWidget(context, GROUP_ALL, SORT_NEWEST, fastMirror).find { it.uid == uid }
    }

    // =========================================================================
    // TASKS PARSER & PUBLIC API FOR WIDGET (FOCUS JSON + WAREHOUSE MD)
    // =========================================================================
    private val taskLineRegex = Regex("""^-\s*\[([ xX])\]\s+(.+)$""")
    private val headingCleanupRegex = Regex("""^(?:[^\p{L}\p{N}]+)?(?:\d+\.\s*|[A-Z]\.\s*)?(.+?)(?:\s*\([^)]*\))?$""")

    private fun cleanWarehouseHeadingLabel(rawHeadingLine: String): String {
        val noHashes = rawHeadingLine.trim().replace(Regex("""^#+\s*"""), "").trim()
        val strippedEmoji = noHashes.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()
        val m = headingCleanupRegex.find(strippedEmoji)
        val base = (m?.groupValues?.get(1) ?: strippedEmoji).trim()
        return base.ifEmpty { strippedEmoji.ifEmpty { "Hộp tiếp nhận" } }
    }

    private fun isIgnoredWarehouseHeading(rawHeadingLine: String): Boolean {
        val t = rawHeadingLine.lowercase(Locale.getDefault())
        return t.contains("cơ chế vận hành") || t.contains("danh mục task theo mảng")
    }

    data class WarehouseSection(
        val key: String, // "WH:<cleanLabel>"
        val cleanLabel: String,
        val headingLineIndex: Int
    )

    private fun scanWarehouseSections(lines: List<String>): List<WarehouseSection> {
        val sections = mutableListOf<WarehouseSection>()
        for ((i, raw) in lines.withIndex()) {
            val tr = raw.trim()
            if (tr.startsWith("## ") || tr.startsWith("### ")) {
                if (isIgnoredWarehouseHeading(tr)) continue
                val clean = cleanWarehouseHeadingLabel(tr)
                sections.add(WarehouseSection("WH:$clean", clean, i))
            }
        }
        return sections
    }

    // =========================================================================
    // SINGLE-FILE QUICK TASKS PARSER / SERIALIZER (0. Inbox/✅ Quick Tasks.md)
    // =========================================================================
    private data class RawSubTask(
        var done: Boolean,
        var text: String
    )

    private data class RawQuickTask(
        var done: Boolean,
        var rawBody: String,
        val subTasks: MutableList<RawSubTask> = mutableListOf()
    )

    private data class RawTaskGroup(
        val key: String, // GROUP_FOCUS or "WH:<label>"
        val label: String,
        val isFocus: Boolean,
        val items: MutableList<RawQuickTask> = mutableListOf()
    )

    private data class ParsedTasksDoc(
        val focusItems: MutableList<RawQuickTask>,
        val waitGroups: MutableList<RawTaskGroup>
    )

    private val subTaskLineRegex = Regex("""^(\t+| {2,})-\s*\[([ xX])\]\s+(.+)$""")
    private val subBulletLineRegex = Regex("""^(\t+| {2,})-\s+(.+)$""")

    private fun parseQuickTasksDoc(md: String): ParsedTasksDoc {
        val focusItems = mutableListOf<RawQuickTask>()
        val waitGroups = mutableListOf<RawTaskGroup>()
        var inFocus = true
        var curWaitGroup: RawTaskGroup? = null
        var lastParent: RawQuickTask? = null

        val lines = md.replace("\r\n", "\n").split("\n")
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed == "---") continue

            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                val h1 = trimmed.removePrefix("# ").trim()
                lastParent = null
                if (h1.contains("Hôm nay", ignoreCase = true) || h1.contains("Trọng tâm", ignoreCase = true)) {
                    inFocus = true
                    curWaitGroup = null
                } else {
                    inFocus = false
                    curWaitGroup = null
                }
                continue
            }

            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                val clean = trimmed.replace(Regex("""^#+\s*"""), "")
                    .replace(Regex("""^[^\p{L}\p{N}]+"""), "")
                    .trim()
                lastParent = null
                if (clean.isNotEmpty()) {
                    inFocus = false
                    val key = "WH:$clean"
                    val existing = waitGroups.find { it.key.equals(key, ignoreCase = true) }
                    curWaitGroup = existing ?: RawTaskGroup(key, clean, isFocus = false).also { waitGroups.add(it) }
                }
                continue
            }

            val subTaskMatch = subTaskLineRegex.find(rawLine)
            if (subTaskMatch != null && lastParent != null) {
                val subDone = subTaskMatch.groupValues[2].equals("x", ignoreCase = true)
                val subTxt = subTaskMatch.groupValues[3].trim()
                if (subTxt.isNotEmpty()) lastParent.subTasks.add(RawSubTask(subDone, subTxt))
                continue
            }
            val subBulletMatch = subBulletLineRegex.find(rawLine)
            if (subBulletMatch != null && lastParent != null) {
                val subTxt = subBulletMatch.groupValues[2].trim()
                if (subTxt.isNotEmpty()) lastParent.subTasks.add(RawSubTask(false, subTxt))
                continue
            }

            val m = taskLineRegex.find(trimmed)
            if (m != null) {
                val done = m.groupValues[1].equals("x", ignoreCase = true)
                val body = m.groupValues[2].trim()
                if (body.isEmpty()) continue
                val entry = RawQuickTask(done, body)
                if (inFocus) {
                    focusItems.add(entry)
                } else {
                    val grp = curWaitGroup ?: (waitGroups.find { it.key == GROUP_WAIT }
                        ?: RawTaskGroup(GROUP_WAIT, "Việc chờ", isFocus = false).also { waitGroups.add(0, it) })
                    curWaitGroup = grp
                    grp.items.add(entry)
                }
                lastParent = entry
            }
        }

        if (waitGroups.isEmpty()) {
            waitGroups.add(RawTaskGroup(GROUP_WAIT, "Việc chờ", isFocus = false))
        }
        return ParsedTasksDoc(focusItems, waitGroups)
    }

    private fun serializeQuickTasksDoc(doc: ParsedTasksDoc): String {
        val out = mutableListOf<String>()
        out.add("# 🎯 Hôm nay")
        for (t in doc.focusItems) {
            out.add("- [${if (t.done) "x" else " "}] ${t.rawBody}")
            for (st in t.subTasks) {
                out.add("\t- [${if (st.done) "x" else " "}] ${st.text}")
            }
        }

        out.add("# 📥 Việc chờ")
        val defaultWait = doc.waitGroups.find { it.key == GROUP_WAIT }
        if (defaultWait != null) {
            for (t in defaultWait.items) {
                out.add("- [${if (t.done) "x" else " "}] ${t.rawBody}")
                for (st in t.subTasks) {
                    out.add("\t- [${if (st.done) "x" else " "}] ${st.text}")
                }
            }
        }
        for (grp in doc.waitGroups) {
            if (grp.key == GROUP_WAIT) continue
            out.add("## ${grp.label}")
            for (t in grp.items) {
                out.add("- [${if (t.done) "x" else " "}] ${t.rawBody}")
                for (st in t.subTasks) {
                    out.add("\t- [${if (st.done) "x" else " "}] ${st.text}")
                }
            }
        }
        return out.joinToString("\n").trimEnd() + "\n"
    }

    private fun locateQuickTask(doc: ParsedTasksDoc, uid: String): Pair<MutableList<RawQuickTask>, Int>? {
        val parts = uid.split("|")
        if (parts.size < 4 || parts[0] != "QT") return null
        val grpKey = parts[1]
        val idx = parts[2].toIntOrNull() ?: -1
        val bodyHash = parts[3].toIntOrNull()

        val targetList = if (grpKey == GROUP_FOCUS) {
            doc.focusItems
        } else {
            doc.waitGroups.find { it.key.equals(grpKey, ignoreCase = true) }?.items
        } ?: return null

        if (idx in targetList.indices && (bodyHash == null || targetList[idx].rawBody.hashCode() == bodyHash)) {
            return Pair(targetList, idx)
        }
        if (bodyHash != null) {
            val found = targetList.indexOfFirst { it.rawBody.hashCode() == bodyHash }
            if (found >= 0) return Pair(targetList, found)
        }
        return null
    }

    fun listTaskGroups(context: Context, fastMirror: Boolean = false): List<GroupOption> {
        val md = if (fastMirror) VaultRepository.readQuickTasksMdFast(context) else VaultRepository.readQuickTasksMd(context)
        val doc = parseQuickTasksDoc(md)
        val focusCount = doc.focusItems.size
        val waitTotal = doc.waitGroups.sumOf { it.items.size }
        val out = mutableListOf<GroupOption>()
        out.add(GroupOption(GROUP_ALL, "Tất cả Task", "", focusCount + waitTotal))
        out.add(GroupOption(GROUP_FOCUS, "Hôm nay", "Trọng tâm", focusCount))
        for (grp in doc.waitGroups) {
            out.add(GroupOption(grp.key, grp.label, "Việc chờ", grp.items.size))
        }
        return out
    }

    fun resolveTaskGroupHeaderTitle(context: Context, groupFilter: String): String {
        return when {
            groupFilter == GROUP_ALL -> "Task · Tất cả ▾"
            groupFilter == GROUP_FOCUS -> "Hôm nay ▾"
            groupFilter.startsWith("WH:") -> "${groupFilter.removePrefix("WH:")} ▾"
            else -> "$groupFilter ▾"
        }
    }

    fun loadTasksForWidget(context: Context, groupFilter: String, sortMode: String, fastMirror: Boolean = false): List<TaskWidgetItem> {
        val md = if (fastMirror) VaultRepository.readQuickTasksMdFast(context) else VaultRepository.readQuickTasksMd(context)
        val doc = parseQuickTasksDoc(md)
        val all = mutableListOf<TaskWidgetItem>()
        val focusBase = 20000

        for ((i, entry) in doc.focusItems.withIndex()) {
            val (starred, starStripped) = stripStar(entry.rawBody)
            val (cleanTitle, remindAt) = TaskReminderHelper.extractFromMarkdownBody(starStripped)
            all.add(
                TaskWidgetItem(
                    uid = "QT|$GROUP_FOCUS|$i|${entry.rawBody.hashCode()}",
                    groupKey = GROUP_FOCUS,
                    groupLabel = "Hôm nay",
                    cleanText = cleanTitle.ifEmpty { starStripped },
                    done = entry.done,
                    starred = starred,
                    recencyRank = focusBase - i,
                    remindAt = remindAt
                )
            )
        }

        var waitRank = 10000
        for (grp in doc.waitGroups) {
            for ((i, entry) in grp.items.withIndex()) {
                val (starred, starStripped) = stripStar(entry.rawBody)
                val (cleanTitle, remindAt) = TaskReminderHelper.extractFromMarkdownBody(starStripped)
                all.add(
                    TaskWidgetItem(
                        uid = "QT|${grp.key}|$i|${entry.rawBody.hashCode()}",
                        groupKey = grp.key,
                        groupLabel = grp.label,
                        cleanText = cleanTitle.ifEmpty { starStripped },
                        done = entry.done,
                        starred = starred,
                        recencyRank = waitRank--,
                        remindAt = remindAt
                    )
                )
            }
        }

        val filtered = when (groupFilter) {
            GROUP_ALL -> all
            GROUP_FOCUS -> all.filter { it.groupKey == GROUP_FOCUS }
            else -> all.filter { it.groupKey.equals(groupFilter, ignoreCase = true) }
        }

        return when (sortMode) {
            SORT_NAME -> filtered.sortedWith { a, b ->
                if (a.done != b.done) a.done.compareTo(b.done)
                else viCollator.compare(a.cleanText, b.cleanText)
            }
            SORT_STAR -> filtered.sortedWith(
                compareBy<TaskWidgetItem> { it.done }
                    .thenByDescending { it.starred }
                    .thenByDescending { it.recencyRank }
            )
            else -> filtered.sortedWith(
                compareBy<TaskWidgetItem> { it.done }
                    .thenByDescending { it.recencyRank }
            )
        }
    }

    fun addTaskFromWidget(
        context: Context,
        text: String,
        targetGroupKey: String,
        starred: Boolean,
        remindAt: String = ""
    ): Boolean {
        val clean = text.trim().replace("\r\n", " ").replace("\n", " ")
        if (clean.isEmpty()) return false
        val body = applyStar(TaskReminderHelper.applyToMarkdownBody(clean, remindAt), starred)
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val effectiveKey = if (targetGroupKey.isBlank() || targetGroupKey == GROUP_ALL) GROUP_FOCUS else targetGroupKey

        if (effectiveKey == GROUP_FOCUS) {
            doc.focusItems.add(0, RawQuickTask(done = false, rawBody = body))
        } else {
            val label = effectiveKey.removePrefix("WH:").ifBlank { "Việc chờ" }
            val key = "WH:$label"
            val grp = doc.waitGroups.find { it.key.equals(key, ignoreCase = true) }
                ?: RawTaskGroup(key, label, isFocus = false).also { doc.waitGroups.add(it) }
            grp.items.add(0, RawQuickTask(done = false, rawBody = body))
        }
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun toggleTaskDone(context: Context, uid: String): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        list[idx].done = !list[idx].done
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun setTaskDone(context: Context, uid: String, done: Boolean): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        list[idx].done = done
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun setTaskRemindAt(context: Context, uid: String, remindAt: String): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        val (starred, starStripped) = stripStar(list[idx].rawBody)
        val updated = TaskReminderHelper.applyToMarkdownBody(starStripped, remindAt)
        list[idx].rawBody = applyStar(updated, starred)
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun toggleTaskStar(context: Context, uid: String): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        val (curStar, clean) = stripStar(list[idx].rawBody)
        list[idx].rawBody = applyStar(clean, !curStar)
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun assignTaskToGroup(context: Context, uid: String, targetGroupKey: String): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (srcList, idx) = locateQuickTask(doc, uid) ?: return false
        val moved = srcList.removeAt(idx)
        if (targetGroupKey == GROUP_FOCUS) {
            doc.focusItems.add(0, moved)
        } else {
            val label = targetGroupKey.removePrefix("WH:").ifBlank { "Việc chờ" }
            val key = "WH:$label"
            val grp = doc.waitGroups.find { it.key.equals(key, ignoreCase = true) }
                ?: RawTaskGroup(key, label, isFocus = false).also { doc.waitGroups.add(it) }
            grp.items.add(0, moved)
        }
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun updateTaskFromWidget(
        context: Context,
        uid: String,
        newCleanText: String,
        starred: Boolean,
        remindAt: String? = null
    ): Boolean {
        val clean = newCleanText.trim().replace("\r\n", " ").replace("\n", " ")
        if (clean.isEmpty()) return false
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        val (_, prevBody) = stripStar(list[idx].rawBody)
        val (_, existingRem) = TaskReminderHelper.extractFromMarkdownBody(prevBody)
        val finalRem = if (remindAt != null) TaskReminderHelper.normalizeRemindAt(remindAt) else existingRem
        val bodyWithRem = TaskReminderHelper.applyToMarkdownBody(clean, finalRem)
        list[idx].rawBody = applyStar(bodyWithRem, starred)
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun deleteTaskFromWidget(context: Context, uid: String): Boolean {
        val doc = parseQuickTasksDoc(VaultRepository.readQuickTasksMdFast(context))
        val (list, idx) = locateQuickTask(doc, uid) ?: return false
        list.removeAt(idx)
        return VaultRepository.saveQuickTasksMd(context, serializeQuickTasksDoc(doc))
    }

    fun findTaskByUid(context: Context, uid: String, fastMirror: Boolean = false): TaskWidgetItem? {
        return loadTasksForWidget(context, GROUP_ALL, SORT_NEWEST, fastMirror).find { it.uid == uid }
    }
}
