package com.nguyenhieu.quicknotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONObject

class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.nguyenhieu.quicknotes.ACTION_WIDGET_REFRESH") {
            VaultRepository.notifyWidgets(context)
        }
    }

    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_companion)

        val summaryLines = mutableListOf<String>()
        var doingCount = 0
        try {
            val rawAnchor = VaultRepository.readDailyAnchorJson(context)
            val tasks = JSONObject(rawAnchor).optJSONArray("customTasks")
            if (tasks != null) {
                for (i in 0 until tasks.length()) {
                    val t = tasks.optJSONObject(i) ?: continue
                    val done = t.optString("status") == "done"
                    if (!done) {
                        doingCount++
                        if (summaryLines.size < 3) {
                            summaryLines.add("○ ${t.optString("text")}")
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        var draftCount = 0
        try {
            val md = VaultRepository.readQuickNotesMd(context)
            var inQuick = false
            for (ln in md.lines()) {
                val tr = ln.trim()
                if (tr.startsWith("# ")) {
                    inQuick = tr.contains("Ghi nhanh", ignoreCase = true)
                } else if (inQuick && tr.startsWith("- ")) {
                    draftCount++
                }
            }
        } catch (_: Exception) {
        }

        val headerTitle = when {
            doingCount > 0 && draftCount > 0 -> "Trọng tâm ($doingCount) · Nháp ($draftCount)"
            doingCount > 0 -> "Trọng tâm hôm nay ($doingCount)"
            draftCount > 0 -> "Obsidian Quick · $draftCount nháp chờ dọn"
            else -> "Obsidian Quick"
        }
        views.setTextViewText(R.id.widget_title, headerTitle)

        val bodyText = if (summaryLines.isNotEmpty()) {
            summaryLines.joinToString("\n")
        } else {
            "Chạm ⚡ Nháp để ghi ngay 0 giây hoặc mở quản lý Task..."
        }
        views.setTextViewText(R.id.widget_summary, bodyText)

        val quickIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "quick")
            putExtra("focus_input", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val taskIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "task")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.widget_btn_quick, PendingIntent.getActivity(context, 101, quickIntent, flags))
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 102, quickIntent, flags))
        views.setOnClickPendingIntent(R.id.widget_btn_task, PendingIntent.getActivity(context, 103, taskIntent, flags))

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
