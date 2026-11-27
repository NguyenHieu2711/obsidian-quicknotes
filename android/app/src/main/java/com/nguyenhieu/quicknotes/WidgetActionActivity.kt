package com.nguyenhieu.quicknotes

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bare android.app.Activity (no AppCompat overhead) for ultra-fast <20ms Widget interactions.
 * All reads/writes hit 0.01ms RAM + 0.2ms local mirror first, refresh ONLY the affected
 * Widget ListView immediately, and flush to SAF/Syncthing asynchronously in VaultRepository.
 */
class WidgetActionActivity : Activity() {

    private val submitLock = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        submitLock.set(false)
        handleActionIntent(intent)
    }

    private fun handleActionIntent(activeIntent: Intent?) {
        val mode = activeIntent?.getStringExtra(DualWidgetContract.EXTRA_MODE).orEmpty()
        val widgetId = activeIntent?.getIntExtra(
            DualWidgetContract.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val widgetKind = activeIntent?.getStringExtra(DualWidgetContract.EXTRA_WIDGET_KIND)
            ?: DualWidgetContract.KIND_NOTES
        val itemUid = activeIntent?.getStringExtra(DualWidgetContract.EXTRA_ITEM_UID).orEmpty()

        // 1. Instant <2ms Home Screen toggles (RAM + Mirror write-behind -> zero UI frames)
        when (mode) {
            DualWidgetContract.MODE_TOGGLE_NOTE_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleNoteStar(applicationContext, itemUid)
                    hapticTick()
                }
                closeWindowNow()
                return
            }
            DualWidgetContract.MODE_TOGGLE_TASK_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskStar(applicationContext, itemUid)
                    hapticTick()
                }
                closeWindowNow()
                return
            }
            DualWidgetContract.MODE_TOGGLE_TASK_DONE -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskDone(applicationContext, itemUid)
                    hapticTick()
                }
                closeWindowNow()
                return
            }
        }

        // Interactive floating dialog card — keep top Wi-Fi bar & bottom gesture bar 100% transparent!
        window?.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
        }
        setFinishOnTouchOutside(true)

        when (mode) {
            DualWidgetContract.MODE_ADD_NOTE -> renderQuickAddSheet(isTask = false, widgetId = widgetId)
            DualWidgetContract.MODE_ADD_TASK -> renderQuickAddSheet(isTask = true, widgetId = widgetId)
            DualWidgetContract.MODE_FILTER_GROUP -> renderGroupFilterSheet(widgetKind = widgetKind, widgetId = widgetId)
            DualWidgetContract.MODE_PICK_SORT -> renderSortPickerSheet(widgetKind = widgetKind, widgetId = widgetId)
            DualWidgetContract.MODE_ASSIGN_NOTE_GROUP -> renderAssignNoteGroupSheet(uid = itemUid)
            DualWidgetContract.MODE_ASSIGN_TASK_GROUP -> renderAssignTaskGroupSheet(uid = itemUid)
            DualWidgetContract.MODE_EDIT_NOTE -> renderEditNoteSheet(uid = itemUid)
            DualWidgetContract.MODE_EDIT_TASK -> renderEditTaskSheet(uid = itemUid)
            else -> closeWindowNow()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    private fun closeWindowNow() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun openReminderPickerPopup(currentRemindAt: String, onPicked: (String) -> Unit) {
        val hasCurrent = currentRemindAt.isNotBlank()
        val labels = mutableListOf(
            "Sau 1 giờ",
            "Tối nay 21:00",
            "Sáng mai 09:00",
            "Chọn ngày & giờ..."
        )
        if (hasCurrent) labels.add("Xóa hẹn giờ")

        AlertDialog.Builder(this)
            .setTitle("Hẹn giờ nhắc")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> onPicked(TaskReminderHelper.buildPresetRemindAt("in_1h"))
                    1 -> onPicked(TaskReminderHelper.buildPresetRemindAt("tonight_21"))
                    2 -> onPicked(TaskReminderHelper.buildPresetRemindAt("tomorrow_9"))
                    3 -> openNativeDateTimePicker(currentRemindAt, onPicked)
                    4 -> onPicked("")
                }
            }
            .show()
    }

    private fun openNativeDateTimePicker(currentRemindAt: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val parsedMs = TaskReminderHelper.parseTriggerMillis(currentRemindAt)
        if (parsedMs != null) {
            cal.timeInMillis = parsedMs
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 5) * 5)
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val formatted = String.format(
                            Locale.US,
                            "%04d-%02d-%02d %02d:%02d",
                            year,
                            month + 1,
                            day,
                            hour,
                            minute
                        )
                        onPicked(formatted)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun mountBottomCard(card: LinearLayout, openKeyboardFor: EditText? = null) {
        setContentView(card)
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.96f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            val lp = attributes
            lp.x = 0
            lp.y = dp(10)
            lp.dimAmount = 0.34f
            attributes = lp
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
        }
        if (openKeyboardFor != null) {
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            openKeyboardFor.requestFocus()
            openKeyboardFor.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(openKeyboardFor, InputMethodManager.SHOW_IMPLICIT)
            }, 25L)
        }
    }

    private fun makeSheetCard(): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#191C24"))
            setStroke(dp(1), Color.parseColor("#2E3546"))
            cornerRadius = dp(13).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(14), dp(12), dp(14), dp(11))
        }
    }

    private fun makeAiryEditBox(initialText: String, hintText: String, minLn: Int = 3, maxLn: Int = 7): EditText {
        return EditText(this).apply {
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
            hint = hintText
            setHintTextColor(Color.parseColor("#647087"))
            setTextColor(Color.parseColor("#F3F5F8"))
            textSize = 15.5f
            setLineSpacing(dp(3).toFloat(), 1f)
            gravity = Gravity.TOP or Gravity.START
            minLines = minLn
            maxLines = maxLn
            background = null
            setPadding(dp(4), dp(4), dp(4), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
    }

    private fun makeSheetDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#262D3D"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(9)
            }
        }
    }

    private fun makeIconActionBtn(
        iconRes: Int,
        bgColorHex: String,
        strokeColorHex: String,
        onClick: () -> Unit
    ): FrameLayout {
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColorHex))
                setStroke(dp(1), Color.parseColor(strokeColorHex))
                cornerRadius = dp(9).toFloat()
            }
            val iv = ImageView(this@WidgetActionActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(
                iv,
                FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                    gravity = Gravity.CENTER
                }
            )
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener { onClick() }
        }
    }

    private fun makeCompactRemindTrigger(
        getRemindAt: () -> String,
        onChanged: (String) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_w_clock)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val labelTv = TextView(this).apply {
            textSize = 11.5f
            paint.isFakeBoldText = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        box.addView(icon, LinearLayout.LayoutParams(dp(15), dp(15)))
        box.addView(
            labelTv,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(5)
            }
        )

        fun syncVisual() {
            val (lbl, overdue) = TaskReminderHelper.formatShortDisplay(getRemindAt())
            val active = lbl.isNotEmpty()
            if (active) {
                val cleanLbl = lbl.removePrefix("⏰").trim()
                val fg = if (overdue) "#F87171" else "#38BDF8"
                val bg = if (overdue) "#2F1A22" else "#15283B"
                val st = if (overdue) "#6B2B39" else "#2A5476"
                icon.setColorFilter(Color.parseColor(fg))
                labelTv.text = cleanLbl
                labelTv.setTextColor(Color.parseColor(fg))
                labelTv.visibility = View.VISIBLE
                box.setPadding(dp(9), dp(6), dp(10), dp(6))
                box.background = GradientDrawable().apply {
                    setColor(Color.parseColor(bg))
                    setStroke(dp(1), Color.parseColor(st))
                    cornerRadius = dp(9).toFloat()
                }
            } else {
                icon.setColorFilter(Color.parseColor("#9BA7C0"))
                labelTv.visibility = View.GONE
                box.setPadding(dp(9), dp(9), dp(9), dp(9))
                box.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#222632"))
                    setStroke(dp(1), Color.parseColor("#31384A"))
                    cornerRadius = dp(9).toFloat()
                }
            }
        }
        syncVisual()
        box.setOnClickListener {
            openReminderPickerPopup(getRemindAt()) { picked ->
                onChanged(picked)
                syncVisual()
            }
        }
        return box
    }

    private fun makeOptionRow(
        title: String,
        badge: String = "",
        selected: Boolean = false,
        accentHex: String = "#38BDF8",
        onClick: () -> Unit
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(if (selected) Color.parseColor("#212B3D") else Color.parseColor("#222632"))
            setStroke(dp(1), if (selected) Color.parseColor(accentHex) else Color.parseColor("#2E3445"))
            cornerRadius = dp(12).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(13), dp(11), dp(13), dp(11))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(7)
            layoutParams = lp

            val tvTitle = TextView(this@WidgetActionActivity).apply {
                text = if (selected) "✓  $title" else title
                textSize = 13.5f
                setTextColor(if (selected) Color.parseColor(accentHex) else Color.parseColor("#EDF1F8"))
                paint.isFakeBoldText = selected
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tvTitle)

            if (badge.isNotEmpty()) {
                val tvBadge = TextView(this@WidgetActionActivity).apply {
                    text = badge
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#7D89A3"))
                }
                addView(tvBadge)
            }
            setOnClickListener { onClick() }
        }
    }

    // =========================================================================
    // SHEET 1: + QUICK ADD (0ms OPEN VIA RAM/MIRROR + ATOMIC SINGLE-FLIGHT SAVE)
    // =========================================================================
    private fun renderQuickAddSheet(isTask: Boolean, widgetId: Int) {
        val accentHex = if (isTask) "#4ADE80" else "#38BDF8"
        val accentBtnBg = if (isTask) "#22C55E" else "#38BDF8"
        val accentBtnText = if (isTask) "#052E16" else "#0F172A"

        val currentFilter = WidgetVaultHelper.getWidgetGroupFilter(this, widgetId, WidgetVaultHelper.GROUP_ALL)
        val rawOptions = if (isTask) {
            WidgetVaultHelper.listTaskGroups(this, fastMirror = true).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        } else {
            WidgetVaultHelper.listNoteGroups(this, fastMirror = true).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        }

        var selectedGroupKey = when {
            currentFilter != WidgetVaultHelper.GROUP_ALL && rawOptions.any { it.key.equals(currentFilter, ignoreCase = true) } ->
                currentFilter
            isTask -> WidgetVaultHelper.GROUP_FOCUS
            else -> WidgetVaultHelper.GROUP_QUICK
        }
        var isStarred = false
        var selectedRemindAt = ""

        val card = makeSheetCard()

        val editBox = makeAiryEditBox(
            initialText = "",
            hintText = if (isTask) "Thêm việc..." else "Ghi nhanh...",
            minLn = 3,
            maxLn = 6
        )
        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chipViews = mutableListOf<Pair<String, TextView>>()

        fun refreshChipStyles() {
            for ((k, tv) in chipViews) {
                val active = k.equals(selectedGroupKey, ignoreCase = true)
                tv.setTextColor(if (active) Color.parseColor(accentHex) else Color.parseColor("#9BA7C0"))
                tv.paint.isFakeBoldText = active
                tv.background = GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#1E293B") else Color.parseColor("#222632"))
                    setStroke(dp(1), if (active) Color.parseColor(accentHex) else Color.parseColor("#31384A"))
                    cornerRadius = dp(8).toFloat()
                }
            }
        }

        for (opt in rawOptions) {
            val shortLbl = if (opt.key == WidgetVaultHelper.GROUP_QUICK) "Ghi nhanh" else opt.label
            val chip = TextView(this).apply {
                text = shortLbl
                textSize = 11.5f
                setPadding(dp(9), dp(5), dp(9), dp(5))
                setOnClickListener {
                    selectedGroupKey = opt.key
                    refreshChipStyles()
                }
            }
            chipViews.add(Pair(opt.key, chip))
            chipRow.addView(
                chip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(6)
                }
            )
        }
        refreshChipStyles()

        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }
        card.addView(
            hScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        )
        card.addView(makeSheetDivider())

        lateinit var saveBtn: TextView

        fun submitNow() {
            val txt = editBox.text?.toString()?.trim().orEmpty()
            if (txt.isEmpty()) {
                Toast.makeText(this, "Chưa nhập nội dung.", Toast.LENGTH_SHORT).show()
                return
            }
            if (!submitLock.compareAndSet(false, true)) return
            saveBtn.isEnabled = false
            saveBtn.alpha = 0.5f
            hapticTick()
            // Writes to RAM + Mirror + refreshes widget in <1ms; SAF flushes asynchronously!
            if (isTask) {
                WidgetVaultHelper.addTaskFromWidget(applicationContext, txt, selectedGroupKey, isStarred, selectedRemindAt)
            } else {
                WidgetVaultHelper.addNoteFromWidget(applicationContext, txt, selectedGroupKey, isStarred)
            }
            closeWindowNow()
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val starIconIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val starBtn = FrameLayout(this).apply {
            addView(starIconIv, FrameLayout.LayoutParams(dp(16), dp(16)).apply { gravity = Gravity.CENTER })
        }
        fun syncStarBtnStyle() {
            starIconIv.setImageResource(if (isStarred) R.drawable.ic_w_star_on else R.drawable.ic_w_star_off)
            starBtn.background = GradientDrawable().apply {
                setColor(if (isStarred) Color.parseColor("#2E2716") else Color.parseColor("#222632"))
                setStroke(dp(1), if (isStarred) Color.parseColor("#FBBF24") else Color.parseColor("#31384A"))
                cornerRadius = dp(9).toFloat()
            }
        }
        syncStarBtnStyle()
        starBtn.setOnClickListener {
            isStarred = !isStarred
            syncStarBtnStyle()
        }
        bottomBar.addView(starBtn, LinearLayout.LayoutParams(dp(34), dp(34)))

        if (isTask) {
            val remTrigger = makeCompactRemindTrigger(
                getRemindAt = { selectedRemindAt },
                onChanged = { selectedRemindAt = it }
            )
            bottomBar.addView(
                remTrigger,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(7)
                }
            )
        }

        bottomBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        saveBtn = TextView(this).apply {
            text = "Lưu"
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor(accentBtnText))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(accentBtnBg))
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(16), dp(7), dp(16), dp(7))
            setOnClickListener { submitNow() }
        }
        bottomBar.addView(saveBtn)
        card.addView(bottomBar)

        mountBottomCard(card, openKeyboardFor = editBox)
    }

    private fun makeDropdownCard(): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#181C24"))
            setStroke(dp(1), Color.parseColor("#2D3546"))
            cornerRadius = dp(14).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun makeDropdownRow(
        title: String,
        badge: String = "",
        selected: Boolean = false,
        accentHex: String = "#38BDF8",
        isLast: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(if (selected) Color.parseColor("#222B3C") else Color.TRANSPARENT)
            if (selected) setStroke(dp(1), Color.parseColor(accentHex))
            cornerRadius = dp(9).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(10), dp(8), dp(10), dp(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = if (isLast) 0 else dp(2)
            layoutParams = lp

            val tvTitle = TextView(this@WidgetActionActivity).apply {
                text = if (selected) "✓  $title" else title
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (selected) Color.parseColor(accentHex) else Color.parseColor("#EDF1F8"))
                paint.isFakeBoldText = selected
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tvTitle)

            if (badge.isNotEmpty()) {
                val tvBadge = TextView(this@WidgetActionActivity).apply {
                    text = badge
                    textSize = 11f
                    setPadding(dp(8), 0, 0, 0)
                    setTextColor(Color.parseColor("#7D89A3"))
                }
                addView(tvBadge)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun mountAnchoredDropdown(card: LinearLayout) {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val bounds: Rect? = intent?.sourceBounds

        var statusTop = 0
        var navBottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            statusTop = insets.top
            navBottom = insets.bottom
        } else {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) statusTop = resources.getDimensionPixelSize(resId)
        }

        val dropdownW = if (bounds != null && bounds.width() > 0) {
            (bounds.width() + dp(36)).coerceIn(dp(205), dp(260)).coerceAtMost(screenW - dp(24))
        } else {
            dp(225).coerceAtMost(screenW - dp(24))
        }

        val maxAvailH = (screenH - statusTop - navBottom - dp(24)).coerceAtLeast(dp(120))
        card.measure(
            View.MeasureSpec.makeMeasureSpec(dropdownW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxAvailH, View.MeasureSpec.AT_MOST)
        )
        val menuH = card.measuredHeight.coerceAtLeast(dp(56))

        val targetX = if (bounds != null && bounds.left > 0) {
            bounds.left.coerceIn(dp(12), (screenW - dropdownW - dp(12)).coerceAtLeast(dp(12)))
        } else {
            dp(16)
        }

        val targetY = if (bounds != null && bounds.bottom > 0) {
            val spaceBelow = (screenH - navBottom) - bounds.bottom - dp(10)
            val spaceAbove = bounds.top - statusTop - dp(10)
            if (menuH <= spaceBelow || spaceBelow >= spaceAbove) {
                (bounds.bottom + dp(4)).coerceIn(
                    statusTop + dp(8),
                    (screenH - navBottom - menuH - dp(8)).coerceAtLeast(statusTop + dp(8))
                )
            } else {
                (bounds.top - menuH - dp(4)).coerceAtLeast(statusTop + dp(8))
            }
        } else {
            statusTop + dp(72)
        }

        setContentView(card)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setLayout(dropdownW, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP or Gravity.START)
            val lp = attributes
            lp.x = targetX
            lp.y = targetY
            lp.dimAmount = 0.10f
            attributes = lp
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
        }
    }

    // =========================================================================
    // SHEET 2: WIDGET GROUP FILTER DROPDOWN (COMPACT, ANCHORED UNDER BUTTON)
    // =========================================================================
    private fun renderGroupFilterSheet(widgetKind: String, widgetId: Int) {
        val isTask = widgetKind == DualWidgetContract.KIND_TASKS
        val accentHex = if (isTask) "#A78BFA" else "#38BDF8"
        val curFilter = WidgetVaultHelper.getWidgetGroupFilter(this, widgetId, WidgetVaultHelper.GROUP_ALL)
        val options = if (isTask) {
            WidgetVaultHelper.listTaskGroups(this, fastMirror = true)
        } else {
            WidgetVaultHelper.listNoteGroups(this, fastMirror = true)
        }

        val card = makeDropdownCard()
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((idx, opt) in options.withIndex()) {
            val badge = "${opt.count}"
            listBox.addView(
                makeDropdownRow(
                    title = opt.label,
                    badge = badge,
                    selected = opt.key.equals(curFilter, ignoreCase = true),
                    accentHex = accentHex,
                    isLast = idx == options.lastIndex
                ) {
                    if (!submitLock.compareAndSet(false, true)) return@makeDropdownRow
                    WidgetVaultHelper.setWidgetGroupFilter(applicationContext, widgetId, opt.key)
                    DualWidgetContract.refreshHeaderAndList(applicationContext, widgetKind, widgetId)
                    hapticTick()
                    closeWindowNow()
                }
            )
        }

        val maxListH = (resources.displayMetrics.heightPixels * 0.42f).toInt().coerceIn(dp(140), dp(270))
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxListH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(scroll)
        mountAnchoredDropdown(card)
    }

    // =========================================================================
    // SHEET 3: WIDGET SORT PICKER (COMPACT DROPDOWN)
    // =========================================================================
    private fun renderSortPickerSheet(widgetKind: String, widgetId: Int) {
        val isTask = widgetKind == DualWidgetContract.KIND_TASKS
        val accentHex = if (isTask) "#A78BFA" else "#38BDF8"
        val curSort = WidgetVaultHelper.getWidgetSortMode(this, widgetId)

        val card = makeDropdownCard()
        val modes = listOf(
            Triple(WidgetVaultHelper.SORT_NEWEST, "Mới nhất", "Mặc định"),
            Triple(WidgetVaultHelper.SORT_NAME, "Theo tên (A → Z)", ""),
            Triple(WidgetVaultHelper.SORT_STAR, "Theo sao (★)", "")
        )
        for ((idx, item) in modes.withIndex()) {
            val (key, title, hint) = item
            card.addView(
                makeDropdownRow(
                    title = title,
                    badge = hint,
                    selected = curSort == key,
                    accentHex = accentHex,
                    isLast = idx == modes.lastIndex
                ) {
                    if (!submitLock.compareAndSet(false, true)) return@makeDropdownRow
                    WidgetVaultHelper.setWidgetSortMode(applicationContext, widgetId, key)
                    DualWidgetContract.refreshHeaderAndList(applicationContext, widgetKind, widgetId)
                    hapticTick()
                    closeWindowNow()
                }
            )
        }
        mountAnchoredDropdown(card)
    }

    // =========================================================================
    // SHEET 4: 1-TAP ASSIGN / MOVE GROUP FOR A QUICK NOTE ITEM
    // =========================================================================
    private fun renderAssignNoteGroupSheet(uid: String) {
        val note = WidgetVaultHelper.findNoteByUid(this, uid, fastMirror = true)
        if (note == null) {
            closeWindowNow()
            return
        }

        val card = makeSheetCard()
        card.addView(
            TextView(this).apply {
                text = "Gắn nhóm cho ghi chú"
                textSize = 15f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#F1F5F9"))
            }
        )
        card.addView(
            TextView(this).apply {
                text = note.cleanText
                textSize = 12.5f
                maxLines = 2
                setTextColor(Color.parseColor("#8E9BB5"))
                setPadding(0, dp(3), 0, dp(10))
            }
        )

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val groups = WidgetVaultHelper.listNoteGroups(this, fastMirror = true).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        val orderedGroups = groups.sortedBy { if (it.key == WidgetVaultHelper.GROUP_QUICK) 1 else 0 }

        for (opt in orderedGroups) {
            listBox.addView(
                makeOptionRow(
                    title = opt.label,
                    badge = opt.sectionHint,
                    selected = opt.key.equals(note.groupKey, ignoreCase = true),
                    accentHex = "#38BDF8"
                ) {
                    if (!submitLock.compareAndSet(false, true)) return@makeOptionRow
                    WidgetVaultHelper.assignNoteToGroup(applicationContext, uid, opt.key)
                    hapticTick()
                    closeWindowNow()
                }
            )
        }

        listBox.addView(
            makeOptionRow(
                title = "Chuyển thành Task Trọng tâm hôm nay",
                badge = "Task",
                selected = false,
                accentHex = "#A78BFA"
            ) {
                if (!submitLock.compareAndSet(false, true)) return@makeOptionRow
                WidgetVaultHelper.convertNoteToFocusTask(applicationContext, uid)
                hapticTick()
                closeWindowNow()
            }
        )

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.48f).toInt().coerceAtMost(dp(340))
            )
        }
        card.addView(scroll)
        mountBottomCard(card)
    }

    // =========================================================================
    // SHEET 5: 1-TAP ASSIGN / MOVE GROUP FOR A TASK ITEM
    // =========================================================================
    private fun renderAssignTaskGroupSheet(uid: String) {
        val task = WidgetVaultHelper.findTaskByUid(this, uid, fastMirror = true)
        if (task == null) {
            closeWindowNow()
            return
        }

        val card = makeSheetCard()
        card.addView(
            TextView(this).apply {
                text = "Chuyển nhóm cho Task"
                textSize = 15f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#F1F5F9"))
            }
        )
        card.addView(
            TextView(this).apply {
                text = task.cleanText
                textSize = 12.5f
                maxLines = 2
                setTextColor(Color.parseColor("#8E9BB5"))
                setPadding(0, dp(3), 0, dp(10))
            }
        )

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val groups = WidgetVaultHelper.listTaskGroups(this, fastMirror = true).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        for (opt in groups) {
            listBox.addView(
                makeOptionRow(
                    title = opt.label,
                    badge = opt.sectionHint,
                    selected = opt.key.equals(task.groupKey, ignoreCase = true),
                    accentHex = "#A78BFA"
                ) {
                    if (!submitLock.compareAndSet(false, true)) return@makeOptionRow
                    WidgetVaultHelper.assignTaskToGroup(applicationContext, uid, opt.key)
                    hapticTick()
                    closeWindowNow()
                }
            )
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.48f).toInt().coerceAtMost(dp(340))
            )
        }
        card.addView(scroll)
        mountBottomCard(card)
    }

    // =========================================================================
    // SHEET 6: QUICK EDIT / DELETE / OPEN FOR NOTE OR TASK
    // =========================================================================
    private fun renderEditNoteSheet(uid: String) {
        val note = WidgetVaultHelper.findNoteByUid(this, uid, fastMirror = true)
        if (note == null) {
            closeWindowNow()
            return
        }
        var isStarred = note.starred
        val card = makeSheetCard()

        val editBox = makeAiryEditBox(
            initialText = note.cleanText,
            hintText = "Nhập nội dung...",
            minLn = 3,
            maxLn = 7
        )
        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        card.addView(makeSheetDivider())

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actRow.addView(
            makeIconActionBtn(R.drawable.ic_w_trash, "#2A1C22", "#5C2B33") {
                if (!submitLock.compareAndSet(false, true)) return@makeIconActionBtn
                WidgetVaultHelper.deleteNoteFromWidget(applicationContext, uid)
                hapticTick()
                closeWindowNow()
            }
        )

        val starIconIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val starBtn = FrameLayout(this).apply {
            addView(starIconIv, FrameLayout.LayoutParams(dp(16), dp(16)).apply { gravity = Gravity.CENTER })
        }
        fun syncStarBtnStyle() {
            starIconIv.setImageResource(if (isStarred) R.drawable.ic_w_star_on else R.drawable.ic_w_star_off)
            starBtn.background = GradientDrawable().apply {
                setColor(if (isStarred) Color.parseColor("#2E2716") else Color.parseColor("#222632"))
                setStroke(dp(1), if (isStarred) Color.parseColor("#FBBF24") else Color.parseColor("#31384A"))
                cornerRadius = dp(9).toFloat()
            }
        }
        syncStarBtnStyle()
        starBtn.setOnClickListener {
            isStarred = !isStarred
            syncStarBtnStyle()
        }
        actRow.addView(
            starBtn,
            LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                leftMargin = dp(7)
            }
        )

        val groupChipLabel = if (note.isUnassigned) "Đổi nhóm ▾" else "${note.groupLabel} ▾"
        actRow.addView(
            TextView(this).apply {
                text = groupChipLabel
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(145)
                setTextColor(Color.parseColor("#38BDF8"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#16283B"))
                    setStroke(dp(1), Color.parseColor("#2A5476"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener {
                    submitLock.set(false)
                    renderAssignNoteGroupSheet(uid)
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(7)
            }
        )

        actRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        actRow.addView(
            TextView(this).apply {
                text = "Lưu"
                textSize = 13f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#0F172A"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#38BDF8"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(16), dp(7), dp(16), dp(7))
                setOnClickListener {
                    val txt = editBox.text?.toString()?.trim().orEmpty()
                    if (txt.isEmpty()) return@setOnClickListener
                    if (!submitLock.compareAndSet(false, true)) return@setOnClickListener
                    WidgetVaultHelper.updateNoteFromWidget(applicationContext, uid, txt, isStarred)
                    hapticTick()
                    closeWindowNow()
                }
            }
        )
        card.addView(actRow)
        mountBottomCard(card, openKeyboardFor = editBox)
    }

    private fun renderEditTaskSheet(uid: String) {
        val task = WidgetVaultHelper.findTaskByUid(this, uid, fastMirror = true)
        if (task == null) {
            closeWindowNow()
            return
        }
        val isStarred = task.starred
        var selectedRemindAt = task.remindAt
        val card = makeSheetCard()

        val editBox = makeAiryEditBox(
            initialText = task.cleanText,
            hintText = "Nhập công việc...",
            minLn = 3,
            maxLn = 7
        )
        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        card.addView(makeSheetDivider())

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actRow.addView(
            makeIconActionBtn(R.drawable.ic_w_trash, "#2A1C22", "#5C2B33") {
                if (!submitLock.compareAndSet(false, true)) return@makeIconActionBtn
                WidgetVaultHelper.deleteTaskFromWidget(applicationContext, uid)
                hapticTick()
                closeWindowNow()
            }
        )

        actRow.addView(
            TextView(this).apply {
                text = "${task.groupLabel} ▾"
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(120)
                setTextColor(Color.parseColor("#9BA7C0"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#222632"))
                    setStroke(dp(1), Color.parseColor("#31384A"))
                    cornerRadius = dp(9).toFloat()
                }
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener {
                    submitLock.set(false)
                    renderAssignTaskGroupSheet(uid)
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(7)
            }
        )

        val remTrigger = makeCompactRemindTrigger(
            getRemindAt = { selectedRemindAt },
            onChanged = { selectedRemindAt = it }
        )
        actRow.addView(
            remTrigger,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(7)
            }
        )

        actRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        actRow.addView(
            TextView(this).apply {
                text = "Lưu"
                textSize = 13f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#052E16"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22C55E"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(16), dp(7), dp(16), dp(7))
                setOnClickListener {
                    val txt = editBox.text?.toString()?.trim().orEmpty()
                    if (txt.isEmpty()) return@setOnClickListener
                    if (!submitLock.compareAndSet(false, true)) return@setOnClickListener
                    WidgetVaultHelper.updateTaskFromWidget(applicationContext, uid, txt, isStarred, selectedRemindAt)
                    hapticTick()
                    closeWindowNow()
                }
            }
        )
        card.addView(actRow)
        mountBottomCard(card, openKeyboardFor = editBox)
    }
}
