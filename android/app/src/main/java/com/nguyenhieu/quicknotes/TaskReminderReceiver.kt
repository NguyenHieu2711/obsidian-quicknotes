package com.nguyenhieu.quicknotes

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        when (intent.action) {
            TaskReminderScheduler.ACTION_FIRE_REMINDER -> {
                val uid = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_UID).orEmpty()
                val title = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TITLE).orEmpty()
                val group = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_GROUP).orEmpty()
                val remindAt = intent.getStringExtra(TaskReminderScheduler.EXTRA_REMIND_AT).orEmpty()
                val notifId = intent.getIntExtra(
                    TaskReminderScheduler.EXTRA_NOTIF_ID,
                    TaskReminderScheduler.requestCodeForTask(uid)
                )
                if (uid.isNotBlank() && title.isNotBlank()) {
                    TaskReminderScheduler.showReminderNotification(
                        appCtx,
                        uid = uid,
                        title = title,
                        groupLabel = group.ifBlank { "Task" },
                        remindAt = remindAt,
                        notifId = notifId
                    )
                }
            }

            TaskReminderScheduler.ACTION_MARK_DONE -> {
                val uid = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_UID).orEmpty()
                val notifId = intent.getIntExtra(TaskReminderScheduler.EXTRA_NOTIF_ID, 0)
                TaskReminderScheduler.cancelNotification(appCtx, notifId)
                if (uid.isNotBlank()) {
                    WidgetVaultHelper.setTaskDone(appCtx, uid, done = true)
                    TaskReminderScheduler.syncAllAlarms(appCtx)
                    Toast.makeText(appCtx, "Đã hoàn thành công việc", Toast.LENGTH_SHORT).show()
                }
            }

            TaskReminderScheduler.ACTION_SNOOZE_15M,
            TaskReminderScheduler.ACTION_SNOOZE_1H -> {
                val uid = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_UID).orEmpty()
                val notifId = intent.getIntExtra(TaskReminderScheduler.EXTRA_NOTIF_ID, 0)
                val addMin = if (intent.action == TaskReminderScheduler.ACTION_SNOOZE_1H) 60L else 15L
                TaskReminderScheduler.cancelNotification(appCtx, notifId)
                if (uid.isNotBlank()) {
                    val nextMs = System.currentTimeMillis() + addMin * 60_000L
                    val nextRemindAt = TaskReminderHelper.formatMillisToRemindAt(nextMs)
                    WidgetVaultHelper.setTaskRemindAt(appCtx, uid, nextRemindAt)
                    TaskReminderScheduler.syncAllAlarms(appCtx)
                    val hhmm = nextRemindAt.substringAfter(" ", nextRemindAt)
                    Toast.makeText(appCtx, "Đã hoãn nhắc tới $hhmm", Toast.LENGTH_SHORT).show()
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                TaskReminderScheduler.syncAllAlarms(appCtx)
            }
        }
    }
}
