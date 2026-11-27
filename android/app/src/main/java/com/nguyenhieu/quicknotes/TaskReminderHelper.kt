package com.nguyenhieu.quicknotes

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskReminderHelper {

    // Matches: "📅 2026-11-27 ⏰ 14:30" or "⏰ 2026-11-27 14:30" or "⏰ 14:30"
    private val MD_DATE_TIME_REGEX = Regex("""(?:📅\s*(\d{4}-\d{2}-\d{2})\s*)?⏰\s*(?:(\d{4}-\d{2}-\d{2})\s+)?(\d{1,2}:\d{2})""")
    private val CANONICAL_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})$""")

    fun normalizeRemindAt(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return ""
        val clean = s.replace("T", " ").replace(Regex("""\s+"""), " ")
        val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})""").find(clean) ?: return ""
        val y = m.groupValues[1].toIntOrNull() ?: return ""
        val mo = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return ""
        val d = m.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 } ?: return ""
        val h = m.groupValues[4].toIntOrNull()?.takeIf { it in 0..23 } ?: return ""
        val min = m.groupValues[5].toIntOrNull()?.takeIf { it in 0..59 } ?: return ""
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d", y, mo, d, h, min)
    }

    /**
     * Extracts (cleanTextWithoutReminder, canonicalRemindAt) from a Markdown task body.
     * Preserves date-only "📅 YYYY-MM-DD" tags when no "⏰ HH:mm" time is attached.
     */
    fun extractFromMarkdownBody(rawBody: String): Pair<String, String> {
        val m = MD_DATE_TIME_REGEX.find(rawBody) ?: return Pair(rawBody.trim(), "")
        val datePart = m.groupValues[1].ifBlank { m.groupValues[2] }.ifBlank { todayDateStr() }
        val timeParts = m.groupValues[3].split(":")
        val hh = (timeParts.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
        val mm = (timeParts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        val remindAt = normalizeRemindAt("$datePart ${String.format(Locale.US, "%02d:%02d", hh, mm)}")
        val stripped = rawBody.removeRange(m.range).replace(Regex("""\s{2,}"""), " ").trim()
        return Pair(stripped, remindAt)
    }

    /**
     * Formats clean Markdown task text with optional "📅 YYYY-MM-DD ⏰ HH:mm" suffix.
     */
    fun applyToMarkdownBody(cleanText: String, remindAtRaw: String?): String {
        val (base, _) = extractFromMarkdownBody(cleanText)
        val norm = normalizeRemindAt(remindAtRaw)
        if (norm.isEmpty()) return base
        val parts = norm.split(" ")
        if (parts.size != 2) return base
        // Avoid duplicating an existing identical date-only "📅 YYYY-MM-DD"
        val dateCleaned = base.replace(Regex("""\s*📅\s*\d{4}-\d{2}-\d{2}"""), "").trim()
        return "$dateCleaned 📅 ${parts[0]} ⏰ ${parts[1]}".trim()
    }

    fun parseTriggerMillis(remindAtRaw: String?): Long? {
        val norm = normalizeRemindAt(remindAtRaw)
        val m = CANONICAL_REGEX.matchEntire(norm) ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, m.groupValues[1].toInt())
            set(Calendar.MONTH, m.groupValues[2].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, m.groupValues[3].toInt())
            set(Calendar.HOUR_OF_DAY, m.groupValues[4].toInt())
            set(Calendar.MINUTE, m.groupValues[5].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatMillisToRemindAt(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
    }

    fun todayDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun buildPresetRemindAt(preset: String): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (preset) {
            "in_1h" -> {
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }
            "tonight_21" -> {
                val nowMs = System.currentTimeMillis()
                cal.set(Calendar.HOUR_OF_DAY, 21)
                cal.set(Calendar.MINUTE, 0)
                if (cal.timeInMillis <= nowMs + 60_000L) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            "tomorrow_9" -> {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
            }
            else -> {
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }
        }
        return formatMillisToRemindAt(cal.timeInMillis)
    }

    fun formatShortDisplay(remindAtRaw: String?): Pair<String, Boolean> {
        val triggerMs = parseTriggerMillis(remindAtRaw) ?: return Pair("", false)
        val nowMs = System.currentTimeMillis()
        val overdue = triggerMs < nowMs - 30_000L

        val targetCal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        val todayCal = Calendar.getInstance()
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        val hhmm = String.format(
            Locale.US,
            "%02d:%02d",
            targetCal.get(Calendar.HOUR_OF_DAY),
            targetCal.get(Calendar.MINUTE)
        )
        val sameYear = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
        val isToday = sameYear && targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

        val label = when {
            isToday -> "⏰ $hhmm"
            isTomorrow -> "⏰ Mai $hhmm"
            else -> {
                val dd = targetCal.get(Calendar.DAY_OF_MONTH)
                val mm = targetCal.get(Calendar.MONTH) + 1
                "⏰ $dd/$mm $hhmm"
            }
        }
        return Pair(label, overdue)
    }

    fun formatNotificationDateTime(remindAtRaw: String?): String {
        val triggerMs = parseTriggerMillis(remindAtRaw) ?: return remindAtRaw?.trim().orEmpty()
        val targetCal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        val todayCal = Calendar.getInstance()
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        val hhmm = String.format(
            Locale.US,
            "%02d:%02d",
            targetCal.get(Calendar.HOUR_OF_DAY),
            targetCal.get(Calendar.MINUTE)
        )
        val sameYear = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
        val isToday = sameYear && targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

        return when {
            isToday -> "Hôm nay · $hhmm"
            isTomorrow -> "Ngày mai · $hhmm"
            else -> {
                val dd = targetCal.get(Calendar.DAY_OF_MONTH)
                val mm = targetCal.get(Calendar.MONTH) + 1
                "$dd/$mm · $hhmm"
            }
        }
    }
}
