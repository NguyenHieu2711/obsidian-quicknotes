package com.nguyenhieu.quicknotes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Zero-window (@android:style/Theme.NoDisplay) trampoline for ListView widget item clicks.
 * - Star & Task Check toggles execute directly in <1ms with ZERO window, ZERO black status/nav bars, ZERO flash!
 * - Interactive actions (Assign Group / Edit) forward immediately to floating WidgetActionActivity.
 */
class WidgetTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent?.getStringExtra(DualWidgetContract.EXTRA_MODE).orEmpty()
        val itemUid = intent?.getStringExtra(DualWidgetContract.EXTRA_ITEM_UID).orEmpty()

        when (mode) {
            DualWidgetContract.MODE_TOGGLE_NOTE_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleNoteStar(applicationContext, itemUid)
                    hapticTick()
                }
            }
            DualWidgetContract.MODE_TOGGLE_TASK_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskStar(applicationContext, itemUid)
                    hapticTick()
                }
            }
            DualWidgetContract.MODE_TOGGLE_TASK_DONE -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskDone(applicationContext, itemUid)
                    hapticTick()
                }
            }
            DualWidgetContract.MODE_ASSIGN_NOTE_GROUP,
            DualWidgetContract.MODE_ASSIGN_TASK_GROUP,
            DualWidgetContract.MODE_EDIT_NOTE,
            DualWidgetContract.MODE_EDIT_TASK -> {
                val forward = Intent(this, WidgetActionActivity::class.java).apply {
                    putExtras(intent)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                startActivity(forward)
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }

        // Theme.NoDisplay strictly requires finish() before onCreate returns
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun hapticTick() {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vib?.vibrate(VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
}
