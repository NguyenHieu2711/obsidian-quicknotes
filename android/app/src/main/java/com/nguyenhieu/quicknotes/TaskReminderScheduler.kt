package com.nguyenhieu.quicknotes

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

object TaskReminderScheduler {

    const val CHANNEL_ID = "obsidian_quick_task_reminders_v1"
    const val ACTION_FIRE_REMINDER = "com.nguyenhieu.quicknotes.ACTION_TASK_REMINDER_FIRE"
    const val ACTION_MARK_DONE = "com.nguyenhieu.quicknotes.ACTION_TASK_REMINDER_DONE"
    const val ACTION_SNOOZE_15M = "com.nguyenhieu.quicknotes.ACTION_TASK_REMINDER_SNOOZE_15M"
    const val ACTION_SNOOZE_1H = "com.nguyenhieu.quicknotes.ACTION_TASK_REMINDER_SNOOZE_1H"

    const val EXTRA_TASK_UID = "extra_task_uid"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_GROUP = "extra_task_group"
    const val EXTRA_REMIND_AT = "extra_remind_at"
    const val EXTRA_NOTIF_ID = "extra_notif_id"

    private const val PREF_SCHEDULED_CODES = "scheduled_task_alarm_codes"
    private const val PREF_FIRED_KEYS = "fired_task_reminder_keys"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nhắc hẹn công việc",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo nổi trên màn hình khi tới giờ hẹn Task"
            enableLights(true)
            lightColor = Color.parseColor("#38BDF8")
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 100L, 180L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(soundUri, audioAttrs)
        }
        nm.createNotificationChannel(channel)
    }

    fun requestCodeForTask(uid: String): Int {
        // Stable non-negative code in range [40000, 940000)
        val clean = uid.substringBeforeLast('|', uid)
        return 40000 + ((clean.hashCode() and 0x7fffffff) % 900000)
    }

    @Synchronized
    fun syncAllAlarms(context: Context) {
        val appCtx = context.applicationContext
        ensureChannel(appCtx)

        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = appCtx.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Cancel all previously registered alarms cleanly so completed/edited tasks never ghost-fire
        val prevCodes = readIntSet(prefs.getString(PREF_SCHEDULED_CODES, "[]"))
        for (code in prevCodes) {
            val cancelIntent = Intent(appCtx, TaskReminderReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                code,
                cancelIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                try {
                    am.cancel(pi)
                    pi.cancel()
                } catch (_: Throwable) {
                }
            }
        }

        // 2. Load all incomplete tasks with valid upcoming reminder times
        val tasks = try {
            WidgetVaultHelper.loadTasksForWidget(
                appCtx,
                WidgetVaultHelper.GROUP_ALL,
                WidgetVaultHelper.SORT_NEWEST,
                fastMirror = true
            )
        } catch (_: Throwable) {
            emptyList()
        }

        val nowMs = System.currentTimeMillis()
        val firedKeys = readStringSet(prefs.getString(PREF_FIRED_KEYS, "[]"))
        val nextCodes = LinkedHashSet<Int>()

        for (task in tasks) {
            if (task.done || task.remindAt.isBlank()) continue
            val triggerMs = TaskReminderHelper.parseTriggerMillis(task.remindAt) ?: continue
            val fireKey = "${task.uid.substringBeforeLast('|', task.uid)}|${task.remindAt}"
            if (fireKey in firedKeys) continue
            // Skip reminders more than 2 minutes in the past (avoids spam after sync/reboot)
            if (triggerMs < nowMs - 120_000L) continue

            val effectiveTrigger = triggerMs.coerceAtLeast(nowMs + 1500L)
            val reqCode = requestCodeForTask(task.uid)
            val fireIntent = Intent(appCtx, TaskReminderReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
                putExtra(EXTRA_TASK_UID, task.uid)
                putExtra(EXTRA_TASK_TITLE, task.cleanText)
                putExtra(EXTRA_TASK_GROUP, task.groupLabel)
                putExtra(EXTRA_REMIND_AT, task.remindAt)
                putExtra(EXTRA_NOTIF_ID, reqCode)
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                reqCode,
                fireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleAlarmCompat(am, effectiveTrigger, pi)
            nextCodes.add(reqCode)
        }

        val arr = JSONArray()
        for (c in nextCodes) arr.put(c)
        prefs.edit().putString(PREF_SCHEDULED_CODES, arr.toString()).apply()
    }

    private fun scheduleAlarmCompat(am: AlarmManager, triggerAtMs: Long, pi: PendingIntent) {
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    fun showReminderNotification(
        context: Context,
        uid: String,
        title: String,
        groupLabel: String,
        remindAt: String,
        notifId: Int
    ) {
        val appCtx = context.applicationContext
        ensureChannel(appCtx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Verify task wasn't already completed or deleted before firing
        val liveTask = WidgetVaultHelper.findTaskByUid(appCtx, uid, fastMirror = true)
        if (liveTask != null && liveTask.done) return
        val displayTitle = liveTask?.cleanText?.ifBlank { title } ?: title
        val displayGroup = liveTask?.groupLabel?.ifBlank { groupLabel } ?: groupLabel

        markFired(appCtx, uid, remindAt)

        val openAppIntent = Intent(appCtx, MainActivity::class.java).apply {
            putExtra("open_tab", "task")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            appCtx,
            notifId + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(appCtx, TaskReminderReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_TASK_UID, uid)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val donePi = PendingIntent.getBroadcast(
            appCtx,
            notifId + 2,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dateTimeLabel = TaskReminderHelper.formatNotificationDateTime(remindAt)

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_w_task_badge)
            .setColor(Color.parseColor("#22C55E"))
            .setShowWhen(false)
            .setContentTitle(displayTitle)
            .setContentText(dateTimeLabel)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, "✓ Hoàn thành", donePi)
            .build()

        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(notifId, notification)
    }

    fun cancelNotification(context: Context, notifId: Int) {
        if (notifId <= 0) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(notifId)
    }

    private fun markFired(context: Context, uid: String, remindAt: String) {
        val prefs = context.getSharedPreferences(VaultRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val set = readStringSet(prefs.getString(PREF_FIRED_KEYS, "[]")).toMutableList()
        val key = "${uid.substringBeforeLast('|', uid)}|$remindAt"
        if (!set.contains(key)) {
            set.add(key)
            while (set.size > 120) set.removeAt(0)
            val arr = JSONArray()
            for (k in set) arr.put(k)
            prefs.edit().putString(PREF_FIRED_KEYS, arr.toString()).apply()
        }
    }

    private fun readIntSet(raw: String?): Set<Int> {
        val out = LinkedHashSet<Int>()
        try {
            val arr = JSONArray(raw ?: "[]")
            for (i in 0 until arr.length()) out.add(arr.optInt(i))
        } catch (_: Throwable) {
        }
        return out
    }

    private fun readStringSet(raw: String?): Set<String> {
        val out = LinkedHashSet<String>()
        try {
            val arr = JSONArray(raw ?: "[]")
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
        } catch (_: Throwable) {
        }
        return out
    }
}
