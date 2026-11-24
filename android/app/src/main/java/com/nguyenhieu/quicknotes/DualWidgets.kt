package com.nguyenhieu.quicknotes

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

object DualWidgetContract {
    const val KIND_NOTES = "notes"
    const val KIND_TASKS = "tasks"

    const val ACTION_ITEM_CLICK = "com.nguyenhieu.quicknotes.ACTION_WIDGET_ITEM_CLICK"
    const val ACTION_HEADER_CLICK = "com.nguyenhieu.quicknotes.ACTION_WIDGET_HEADER_CLICK"
    const val ACTION_CYCLE_SORT = "com.nguyenhieu.quicknotes.ACTION_CYCLE_SORT"

    const val EXTRA_WIDGET_ID = "extra_widget_id"
    const val EXTRA_WIDGET_KIND = "extra_widget_kind"
    const val EXTRA_MODE = "extra_mode"
    const val EXTRA_ITEM_UID = "extra_item_uid"

    const val MODE_ADD_NOTE = "add_note"
    const val MODE_ADD_TASK = "add_task"
    const val MODE_FILTER_GROUP = "filter_group"
    const val MODE_PICK_SORT = "pick_sort"
    const val MODE_ASSIGN_NOTE_GROUP = "assign_note_group"
    const val MODE_ASSIGN_TASK_GROUP = "assign_task_group"
    const val MODE_EDIT_NOTE = "edit_note"
    const val MODE_EDIT_TASK = "edit_task"
    const val MODE_TOGGLE_NOTE_STAR = "toggle_note_star"
    const val MODE_TOGGLE_TASK_STAR = "toggle_task_star"
    const val MODE_TOGGLE_TASK_DONE = "toggle_task_done"

    fun buildActivityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        mutableTemplate: Boolean = false
    ): PendingIntent {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutableTemplate) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opts = ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            return PendingIntent.getActivity(context, requestCode, intent, baseFlags, opts.toBundle())
        }
        return PendingIntent.getActivity(context, requestCode, intent, baseFlags)
    }

    fun buildBroadcastPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Fast list-only refresh (<15ms, zero layout tear-down, preserves scroll!) */
    fun refreshListOnly(context: Context, kind: String) {
        try {
            val mgr = AppWidgetManager.getInstance(context)
            if (kind == KIND_NOTES) {
                val ids = mgr.getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))
                if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.w_notes_list)
            } else {
                val ids = mgr.getAppWidgetIds(ComponentName(context, TasksWidgetProvider::class.java))
                if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.w_tasks_list)
            }
        } catch (_: Exception) {
        }
    }

    /** Fast partial header update + list refresh (zero layout tear-down!) */
    fun refreshHeaderAndList(context: Context, kind: String, widgetId: Int) {
        try {
            val mgr = AppWidgetManager.getInstance(context)
            if (kind == KIND_NOTES) {
                val partial = RemoteViews(context.packageName, R.layout.widget_notes)
                val grp = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
                val srt = WidgetVaultHelper.getWidgetSortMode(context, widgetId)
                partial.setTextViewText(R.id.w_notes_group_btn, WidgetVaultHelper.resolveNoteGroupHeaderTitle(context, grp))
                partial.setTextViewText(R.id.w_notes_sort_btn, WidgetVaultHelper.sortBadgeText(srt))
                mgr.partiallyUpdateAppWidget(widgetId, partial)
                mgr.notifyAppWidgetViewDataChanged(intArrayOf(widgetId), R.id.w_notes_list)
            } else {
                val partial = RemoteViews(context.packageName, R.layout.widget_tasks)
                val grp = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
                val srt = WidgetVaultHelper.getWidgetSortMode(context, widgetId)
                partial.setTextViewText(R.id.w_tasks_group_btn, WidgetVaultHelper.resolveTaskGroupHeaderTitle(context, grp))
                partial.setTextViewText(R.id.w_tasks_sort_btn, WidgetVaultHelper.sortBadgeText(srt))
                mgr.partiallyUpdateAppWidget(widgetId, partial)
                mgr.notifyAppWidgetViewDataChanged(intArrayOf(widgetId), R.id.w_tasks_list)
            }
        } catch (_: Exception) {
        }
    }

    fun refreshAllWidgets(context: Context) {
        refreshListOnly(context, KIND_NOTES)
        refreshListOnly(context, KIND_TASKS)
    }
}

// =============================================================================
// 1. WIDGET 1 — OBSIDIAN · QUICK NOTES
// =============================================================================
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DualWidgetContract.ACTION_CYCLE_SORT) {
            val widgetId = intent.getIntExtra(
                DualWidgetContract.EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetVaultHelper.cycleWidgetSortMode(context, widgetId)
                DualWidgetContract.refreshHeaderAndList(context, DualWidgetContract.KIND_NOTES, widgetId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.w_notes_list)
        // Silently sync any external PC/Mac Syncthing changes in background
        VaultRepository.syncFromExternalVaultAsync(context, listOf(VaultRepository.REL_QUICK_NOTES))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetVaultHelper.clearWidgetPrefs(context, appWidgetIds)
    }

    companion object {
        fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)

            val groupFilter = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
            val sortMode = WidgetVaultHelper.getWidgetSortMode(context, widgetId)

            views.setTextViewText(
                R.id.w_notes_group_btn,
                WidgetVaultHelper.resolveNoteGroupHeaderTitle(context, groupFilter)
            )
            views.setTextViewText(
                R.id.w_notes_sort_btn,
                WidgetVaultHelper.sortBadgeText(sortMode)
            )

            // Open App Notes tab
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_tab", if (groupFilter == WidgetVaultHelper.GROUP_QUICK) "quick" else "notes")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("obsidianquick://open_notes/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_notes_open_app,
                DualWidgetContract.buildActivityPendingIntent(context, 10000 + widgetId, openAppIntent)
            )

            // Group Filter picker
            val groupIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = DualWidgetContract.ACTION_HEADER_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_NOTES)
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_FILTER_GROUP)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://notes_grp/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_notes_group_btn,
                DualWidgetContract.buildActivityPendingIntent(context, 12000 + widgetId, groupIntent)
            )

            // Instant 0ms 1-tap Broadcast Sort cycle (Mới → Tên → Sao ★) — ZERO popup / ZERO Activity!
            val sortIntent = Intent(context, NotesWidgetProvider::class.java).apply {
                action = DualWidgetContract.ACTION_CYCLE_SORT
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                data = Uri.parse("obsidianquick://notes_sort/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_notes_sort_btn,
                DualWidgetContract.buildBroadcastPendingIntent(context, 14000 + widgetId, sortIntent)
            )

            // Prominent + Quick Add Note
            val addIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = DualWidgetContract.ACTION_HEADER_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_NOTES)
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_ADD_NOTE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://notes_add/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_notes_add_btn,
                DualWidgetContract.buildActivityPendingIntent(context, 16000 + widgetId, addIntent)
            )
            views.setOnClickPendingIntent(
                R.id.w_notes_empty,
                DualWidgetContract.buildActivityPendingIntent(context, 18000 + widgetId, addIntent)
            )

            // ListView RemoteAdapter + Mutable Template PendingIntent
            val svcIntent = Intent(context, NotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("obsidianquick://notes_service/$widgetId")
            }
            views.setRemoteAdapter(R.id.w_notes_list, svcIntent)
            views.setEmptyView(R.id.w_notes_list, R.id.w_notes_empty)

            val itemTemplateIntent = Intent(context, WidgetTrampolineActivity::class.java).apply {
                action = DualWidgetContract.ACTION_ITEM_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_NOTES)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://notes_item_tpl/$widgetId")
            }
            views.setPendingIntentTemplate(
                R.id.w_notes_list,
                DualWidgetContract.buildActivityPendingIntent(
                    context,
                    20000 + widgetId,
                    itemTemplateIntent,
                    mutableTemplate = true
                )
            )

            mgr.updateAppWidget(widgetId, views)
        }
    }
}

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return NotesRemoteViewsFactory(applicationContext, widgetId)
    }
}

private class NotesRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<NoteWidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val groupFilter = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
        val sortMode = WidgetVaultHelper.getWidgetSortMode(context, widgetId)
        // Reads from 0.01ms RAM / 0.2ms local mirror — ZERO SAF IPC lag!
        items = WidgetVaultHelper.loadNotesForWidget(context, groupFilter, sortMode, fastMirror = true)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_item_note)

        rv.setTextViewText(R.id.wn_text, item.cleanText)

        if (item.isUnassigned) {
            rv.setTextViewText(R.id.wn_group_chip, "+ Gắn nhóm")
            rv.setTextColor(R.id.wn_group_chip, Color.parseColor("#38BDF8"))
            rv.setInt(R.id.wn_group_chip, "setBackgroundResource", R.drawable.widget_tag_chip_bg)
        } else {
            rv.setTextViewText(R.id.wn_group_chip, item.groupLabel)
            rv.setTextColor(R.id.wn_group_chip, Color.parseColor("#A5B1CC"))
            rv.setInt(R.id.wn_group_chip, "setBackgroundResource", R.drawable.widget_tag_chip_neutral_bg)
        }

        // Hide timestamp for ultra-clean card
        rv.setViewVisibility(R.id.wn_time, View.GONE)

        rv.setImageViewResource(
            R.id.wn_star,
            if (item.starred) R.drawable.ic_w_star_on else R.drawable.ic_w_star_off
        )

        rv.setOnClickFillInIntent(
            R.id.wn_text,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_EDIT_NOTE)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )
        rv.setOnClickFillInIntent(
            R.id.wn_group_chip,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_ASSIGN_NOTE_GROUP)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )
        rv.setOnClickFillInIntent(
            R.id.wn_star,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_TOGGLE_NOTE_STAR)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.uid?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}

// =============================================================================
// 2. WIDGET 2 — OBSIDIAN · QUICK TASK
// =============================================================================
class TasksWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DualWidgetContract.ACTION_CYCLE_SORT) {
            val widgetId = intent.getIntExtra(
                DualWidgetContract.EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetVaultHelper.cycleWidgetSortMode(context, widgetId)
                DualWidgetContract.refreshHeaderAndList(context, DualWidgetContract.KIND_TASKS, widgetId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.w_tasks_list)
        VaultRepository.syncFromExternalVaultAsync(
            context,
            listOf(VaultRepository.REL_DAILY_ANCHOR, VaultRepository.REL_TASK_WAREHOUSE)
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetVaultHelper.clearWidgetPrefs(context, appWidgetIds)
    }

    companion object {
        fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)

            val groupFilter = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
            val sortMode = WidgetVaultHelper.getWidgetSortMode(context, widgetId)

            views.setTextViewText(
                R.id.w_tasks_group_btn,
                WidgetVaultHelper.resolveTaskGroupHeaderTitle(context, groupFilter)
            )
            views.setTextViewText(
                R.id.w_tasks_sort_btn,
                WidgetVaultHelper.sortBadgeText(sortMode)
            )

            // Open App Task tab
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_tab", "task")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("obsidianquick://open_tasks/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_tasks_open_app,
                DualWidgetContract.buildActivityPendingIntent(context, 30000 + widgetId, openAppIntent)
            )

            // Group Filter picker
            val groupIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = DualWidgetContract.ACTION_HEADER_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_TASKS)
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_FILTER_GROUP)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://tasks_grp/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_tasks_group_btn,
                DualWidgetContract.buildActivityPendingIntent(context, 32000 + widgetId, groupIntent)
            )

            // Instant 0ms 1-tap Broadcast Sort cycle (Mới → Tên → Sao ★) — ZERO popup / ZERO Activity!
            val sortIntent = Intent(context, TasksWidgetProvider::class.java).apply {
                action = DualWidgetContract.ACTION_CYCLE_SORT
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                data = Uri.parse("obsidianquick://tasks_sort/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_tasks_sort_btn,
                DualWidgetContract.buildBroadcastPendingIntent(context, 34000 + widgetId, sortIntent)
            )

            // Prominent + Quick Add Task
            val addIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = DualWidgetContract.ACTION_HEADER_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_TASKS)
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_ADD_TASK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://tasks_add/$widgetId")
            }
            views.setOnClickPendingIntent(
                R.id.w_tasks_add_btn,
                DualWidgetContract.buildActivityPendingIntent(context, 36000 + widgetId, addIntent)
            )
            views.setOnClickPendingIntent(
                R.id.w_tasks_empty,
                DualWidgetContract.buildActivityPendingIntent(context, 38000 + widgetId, addIntent)
            )

            // ListView RemoteAdapter + Mutable Template PendingIntent
            val svcIntent = Intent(context, TasksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("obsidianquick://tasks_service/$widgetId")
            }
            views.setRemoteAdapter(R.id.w_tasks_list, svcIntent)
            views.setEmptyView(R.id.w_tasks_list, R.id.w_tasks_empty)

            val itemTemplateIntent = Intent(context, WidgetTrampolineActivity::class.java).apply {
                action = DualWidgetContract.ACTION_ITEM_CLICK
                putExtra(DualWidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(DualWidgetContract.EXTRA_WIDGET_KIND, DualWidgetContract.KIND_TASKS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                data = Uri.parse("obsidianquick://tasks_item_tpl/$widgetId")
            }
            views.setPendingIntentTemplate(
                R.id.w_tasks_list,
                DualWidgetContract.buildActivityPendingIntent(
                    context,
                    40000 + widgetId,
                    itemTemplateIntent,
                    mutableTemplate = true
                )
            )

            mgr.updateAppWidget(widgetId, views)
        }
    }
}

class TasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return TasksRemoteViewsFactory(applicationContext, widgetId)
    }
}

private class TasksRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<TaskWidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val groupFilter = WidgetVaultHelper.getWidgetGroupFilter(context, widgetId, WidgetVaultHelper.GROUP_ALL)
        val sortMode = WidgetVaultHelper.getWidgetSortMode(context, widgetId)
        // Reads from 0.01ms RAM / 0.2ms local mirror — ZERO SAF IPC lag!
        items = WidgetVaultHelper.loadTasksForWidget(context, groupFilter, sortMode, fastMirror = true)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_item_task)

        rv.setImageViewResource(
            R.id.wt_check,
            if (item.done) R.drawable.ic_w_check_on else R.drawable.ic_w_check_off
        )

        if (item.done) {
            val sp = SpannableString(item.cleanText)
            sp.setSpan(StrikethroughSpan(), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rv.setTextViewText(R.id.wt_text, sp)
            rv.setTextColor(R.id.wt_text, Color.parseColor("#6B7489"))
        } else {
            rv.setTextViewText(R.id.wt_text, item.cleanText)
            rv.setTextColor(R.id.wt_text, Color.parseColor("#EDF1F8"))
        }

        rv.setTextViewText(R.id.wt_group_chip, item.groupLabel)
        if (item.groupKey == WidgetVaultHelper.GROUP_FOCUS) {
            rv.setTextColor(R.id.wt_group_chip, Color.parseColor("#C4B5FD"))
        } else {
            rv.setTextColor(R.id.wt_group_chip, Color.parseColor("#9BA7C0"))
        }

        rv.setImageViewResource(
            R.id.wt_star,
            if (item.starred) R.drawable.ic_w_star_on else R.drawable.ic_w_star_off
        )

        rv.setOnClickFillInIntent(
            R.id.wt_check,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_TOGGLE_TASK_DONE)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )
        rv.setOnClickFillInIntent(
            R.id.wt_text,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_EDIT_TASK)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )
        rv.setOnClickFillInIntent(
            R.id.wt_group_chip,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_ASSIGN_TASK_GROUP)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )
        rv.setOnClickFillInIntent(
            R.id.wt_star,
            Intent().apply {
                putExtra(DualWidgetContract.EXTRA_MODE, DualWidgetContract.MODE_TOGGLE_TASK_STAR)
                putExtra(DualWidgetContract.EXTRA_ITEM_UID, item.uid)
            }
        )

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.uid?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
