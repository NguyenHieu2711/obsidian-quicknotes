package com.nguyenhieu.quicknotes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WidgetActionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent?.getStringExtra(DualWidgetContract.EXTRA_MODE).orEmpty()
        val widgetId = intent?.getIntExtra(
            DualWidgetContract.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val widgetKind = intent?.getStringExtra(DualWidgetContract.EXTRA_WIDGET_KIND)
            ?: DualWidgetContract.KIND_NOTES
        val itemUid = intent?.getStringExtra(DualWidgetContract.EXTRA_ITEM_UID).orEmpty()

        // 1. Fast silent Home Screen toggles (0 UI frames, zero flicker)
        when (mode) {
            DualWidgetContract.MODE_TOGGLE_NOTE_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleNoteStar(this, itemUid)
                    hapticTick()
                }
                finishSilently()
                return
            }
            DualWidgetContract.MODE_TOGGLE_TASK_STAR -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskStar(this, itemUid)
                    hapticTick()
                }
                finishSilently()
                return
            }
            DualWidgetContract.MODE_TOGGLE_TASK_DONE -> {
                if (itemUid.isNotEmpty()) {
                    WidgetVaultHelper.toggleTaskDone(this, itemUid)
                    hapticTick()
                }
                finishSilently()
                return
            }
        }

        // 2. Interactive floating sheet modes — enable subtle dim backdrop
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
        }

        when (mode) {
            DualWidgetContract.MODE_ADD_NOTE -> renderQuickAddSheet(isTask = false, widgetId = widgetId)
            DualWidgetContract.MODE_ADD_TASK -> renderQuickAddSheet(isTask = true, widgetId = widgetId)
            DualWidgetContract.MODE_FILTER_GROUP -> renderGroupFilterSheet(widgetKind = widgetKind, widgetId = widgetId)
            DualWidgetContract.MODE_PICK_SORT -> renderSortPickerSheet(widgetKind = widgetKind, widgetId = widgetId)
            DualWidgetContract.MODE_ASSIGN_NOTE_GROUP -> renderAssignNoteGroupSheet(uid = itemUid)
            DualWidgetContract.MODE_ASSIGN_TASK_GROUP -> renderAssignTaskGroupSheet(uid = itemUid)
            DualWidgetContract.MODE_EDIT_NOTE -> renderEditNoteSheet(uid = itemUid)
            DualWidgetContract.MODE_EDIT_TASK -> renderEditTaskSheet(uid = itemUid)
            else -> finishSilently()
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
            vib?.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun finishSilently() {
        DualWidgetContract.refreshAllWidgets(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun mountBottomCard(card: LinearLayout, openKeyboardFor: EditText? = null) {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(24), dp(12), dp(14))
            setOnClickListener { finishSilently() }
        }
        card.isClickable = true
        outer.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(
            outer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (openKeyboardFor != null) {
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            openKeyboardFor.requestFocus()
            openKeyboardFor.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(openKeyboardFor, InputMethodManager.SHOW_IMPLICIT)
            }, 60L)
        }
    }

    private fun makeSheetCard(): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#191C24"))
            setStroke(dp(1), Color.parseColor("#2F3647"))
            cornerRadius = dp(20).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
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
    // SHEET 1: + QUICK ADD (NOTE OR TASK) WITH GROUP CHIPS & STAR TOGGLE
    // =========================================================================
    private fun renderQuickAddSheet(isTask: Boolean, widgetId: Int) {
        val accentHex = if (isTask) "#A78BFA" else "#38BDF8"
        val accentBtnBg = if (isTask) "#8B5CF6" else "#38BDF8"
        val accentBtnText = if (isTask) "#FFFFFF" else "#0F172A"

        val currentFilter = WidgetVaultHelper.getWidgetGroupFilter(this, widgetId, WidgetVaultHelper.GROUP_ALL)
        val rawOptions = if (isTask) {
            WidgetVaultHelper.listTaskGroups(this).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        } else {
            WidgetVaultHelper.listNoteGroups(this).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        }

        var selectedGroupKey = when {
            currentFilter != WidgetVaultHelper.GROUP_ALL && rawOptions.any { it.key.equals(currentFilter, ignoreCase = true) } ->
                currentFilter
            isTask -> WidgetVaultHelper.GROUP_FOCUS
            else -> WidgetVaultHelper.GROUP_QUICK
        }
        var isStarred = false

        val card = makeSheetCard()

        // Header Row: Title + Star toggle + Save button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvHeader = TextView(this).apply {
            text = if (isTask) "Thêm Task nhanh" else "Ghi chú nhanh"
            textSize = 15.5f
            setTextColor(Color.parseColor("#F1F5F9"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvHeader)

        val starBtn = TextView(this).apply {
            textSize = 12.5f
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        fun syncStarBtnStyle() {
            starBtn.text = if (isStarred) "★ Có sao" else "☆ Sao"
            starBtn.setTextColor(if (isStarred) Color.parseColor("#FBBF24") else Color.parseColor("#94A3B8"))
            starBtn.background = GradientDrawable().apply {
                setColor(if (isStarred) Color.parseColor("#2E2716") else Color.parseColor("#232834"))
                setStroke(dp(1), if (isStarred) Color.parseColor("#FBBF24") else Color.parseColor("#343C4F"))
                cornerRadius = dp(9).toFloat()
            }
        }
        syncStarBtnStyle()
        starBtn.setOnClickListener {
            isStarred = !isStarred
            syncStarBtnStyle()
        }
        topRow.addView(
            starBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
        )

        val inputBg = GradientDrawable().apply {
            setColor(Color.parseColor("#222632"))
            setStroke(dp(1), Color.parseColor("#353D52"))
            cornerRadius = dp(13).toFloat()
        }
        val editBox = EditText(this).apply {
            hint = if (isTask) "Nhập đầu việc..." else "Cứ gõ nhanh (gắn nhóm ngay hoặc sau)..."
            setHintTextColor(Color.parseColor("#68748D"))
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 14.5f
            minLines = 2
            maxLines = 4
            background = inputBg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        fun submitNow() {
            val txt = editBox.text?.toString()?.trim().orEmpty()
            if (txt.isEmpty()) {
                Toast.makeText(this, "Chưa nhập nội dung.", Toast.LENGTH_SHORT).show()
                return
            }
            val ok = if (isTask) {
                WidgetVaultHelper.addTaskFromWidget(this, txt, selectedGroupKey, isStarred)
            } else {
                WidgetVaultHelper.addNoteFromWidget(this, txt, selectedGroupKey, isStarred)
            }
            if (ok) {
                hapticTick()
                finishSilently()
            } else {
                Toast.makeText(this, "Chưa kết nối thư mục Vault.", Toast.LENGTH_SHORT).show()
            }
        }

        val saveBtn = TextView(this).apply {
            text = "Lưu"
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor(accentBtnText))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(accentBtnBg))
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { submitNow() }
        }
        topRow.addView(saveBtn)
        card.addView(topRow)

        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(11)
                bottomMargin = dp(10)
            }
        )

        // Group Selector Horizontal Chips
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
                    cornerRadius = dp(9).toFloat()
                }
            }
        }

        for (opt in rawOptions) {
            val shortLbl = if (opt.key == WidgetVaultHelper.GROUP_QUICK) "Ghi nhanh (Mặc định)" else opt.label
            val chip = TextView(this).apply {
                text = shortLbl
                textSize = 12f
                setPadding(dp(10), dp(6), dp(10), dp(6))
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
        card.addView(hScroll)

        mountBottomCard(card, openKeyboardFor = editBox)
    }

    // =========================================================================
    // SHEET 2: WIDGET GROUP FILTER PICKER
    // =========================================================================
    private fun renderGroupFilterSheet(widgetKind: String, widgetId: Int) {
        val isTask = widgetKind == DualWidgetContract.KIND_TASKS
        val accentHex = if (isTask) "#A78BFA" else "#38BDF8"
        val curFilter = WidgetVaultHelper.getWidgetGroupFilter(this, widgetId, WidgetVaultHelper.GROUP_ALL)
        val options = if (isTask) WidgetVaultHelper.listTaskGroups(this) else WidgetVaultHelper.listNoteGroups(this)

        val card = makeSheetCard()
        val hdr = TextView(this).apply {
            text = if (isTask) "Lọc nhóm Task trên Widget" else "Lọc nhóm Notes trên Widget"
            textSize = 15f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#F1F5F9"))
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(hdr)

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (opt in options) {
            val badge = if (opt.sectionHint.isNotEmpty()) "${opt.sectionHint} · ${opt.count}" else "${opt.count}"
            listBox.addView(
                makeOptionRow(
                    title = opt.label,
                    badge = badge,
                    selected = opt.key.equals(curFilter, ignoreCase = true),
                    accentHex = accentHex
                ) {
                    WidgetVaultHelper.setWidgetGroupFilter(this, widgetId, opt.key)
                    hapticTick()
                    finishSilently()
                }
            )
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.52f).toInt().coerceAtMost(dp(380))
            )
        }
        card.addView(scroll)
        mountBottomCard(card)
    }

    // =========================================================================
    // SHEET 3: WIDGET SORT PICKER (MỚI NHẤT / TÊN A-Z / SAO ★)
    // =========================================================================
    private fun renderSortPickerSheet(widgetKind: String, widgetId: Int) {
        val isTask = widgetKind == DualWidgetContract.KIND_TASKS
        val accentHex = if (isTask) "#A78BFA" else "#38BDF8"
        val curSort = WidgetVaultHelper.getWidgetSortMode(this, widgetId)

        val card = makeSheetCard()
        card.addView(
            TextView(this).apply {
                text = "Sắp xếp danh sách Widget"
                textSize = 15f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#F1F5F9"))
                setPadding(0, 0, 0, dp(10))
            }
        )

        val modes = listOf(
            Triple(WidgetVaultHelper.SORT_NEWEST, "Mới nhất", "Mặc định"),
            Triple(WidgetVaultHelper.SORT_NAME, "Theo tên (A → Z)", "Chữ cái"),
            Triple(WidgetVaultHelper.SORT_STAR, "Theo sao (★ lên đầu)", "Ưu tiên")
        )
        for ((key, title, hint) in modes) {
            card.addView(
                makeOptionRow(
                    title = title,
                    badge = hint,
                    selected = curSort == key,
                    accentHex = accentHex
                ) {
                    WidgetVaultHelper.setWidgetSortMode(this, widgetId, key)
                    hapticTick()
                    finishSilently()
                }
            )
        }
        mountBottomCard(card)
    }

    // =========================================================================
    // SHEET 4: 1-TAP ASSIGN / MOVE GROUP FOR A QUICK NOTE ITEM
    // =========================================================================
    private fun renderAssignNoteGroupSheet(uid: String) {
        val note = WidgetVaultHelper.findNoteByUid(this, uid)
        if (note == null) {
            finishSilently()
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
        val groups = WidgetVaultHelper.listNoteGroups(this).filter { it.key != WidgetVaultHelper.GROUP_ALL }

        // Show named groups first so "+ Gắn nhóm" puts user's real groups right on top
        val orderedGroups = groups.sortedBy { if (it.key == WidgetVaultHelper.GROUP_QUICK) 1 else 0 }
        for (opt in orderedGroups) {
            listBox.addView(
                makeOptionRow(
                    title = opt.label,
                    badge = opt.sectionHint,
                    selected = opt.key.equals(note.groupKey, ignoreCase = true),
                    accentHex = "#38BDF8"
                ) {
                    WidgetVaultHelper.assignNoteToGroup(this, uid, opt.key)
                    hapticTick()
                    finishSilently()
                }
            )
        }

        // Extra action: Convert straight to Trọng tâm hôm nay Task
        listBox.addView(
            makeOptionRow(
                title = "Chuyển thành Task Trọng tâm hôm nay",
                badge = "Task",
                selected = false,
                accentHex = "#A78BFA"
            ) {
                WidgetVaultHelper.convertNoteToFocusTask(this, uid)
                hapticTick()
                finishSilently()
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
        val task = WidgetVaultHelper.findTaskByUid(this, uid)
        if (task == null) {
            finishSilently()
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
        val groups = WidgetVaultHelper.listTaskGroups(this).filter { it.key != WidgetVaultHelper.GROUP_ALL }
        for (opt in groups) {
            listBox.addView(
                makeOptionRow(
                    title = opt.label,
                    badge = opt.sectionHint,
                    selected = opt.key.equals(task.groupKey, ignoreCase = true),
                    accentHex = "#A78BFA"
                ) {
                    WidgetVaultHelper.assignTaskToGroup(this, uid, opt.key)
                    hapticTick()
                    finishSilently()
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
        val note = WidgetVaultHelper.findNoteByUid(this, uid)
        if (note == null) {
            finishSilently()
            return
        }
        var isStarred = note.starred
        val card = makeSheetCard()

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            TextView(this).apply {
                text = if (note.isUnassigned) "Ghi nhanh" else note.groupLabel
                textSize = 14.5f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#38BDF8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )

        val btnOpenApp = TextView(this).apply {
            text = "Mở App ↗"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener {
                startActivity(Intent(this@WidgetActionActivity, MainActivity::class.java).apply {
                    putExtra("open_tab", if (note.isUnassigned) "quick" else "notes")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                finishSilently()
            }
        }
        topRow.addView(btnOpenApp)
        card.addView(topRow)

        val editBox = EditText(this).apply {
            setText(note.cleanText)
            setSelection(note.cleanText.length)
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 14.5f
            minLines = 2
            maxLines = 5
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222632"))
                setStroke(dp(1), Color.parseColor("#353D52"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnDelete = TextView(this).apply {
            text = "Xóa"
            textSize = 12.5f
            setTextColor(Color.parseColor("#F87171"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A1C22"))
                setStroke(dp(1), Color.parseColor("#5C2B33"))
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener {
                WidgetVaultHelper.deleteNoteFromWidget(this@WidgetActionActivity, uid)
                hapticTick()
                finishSilently()
            }
        }
        actRow.addView(btnDelete)

        val btnGroup = TextView(this).apply {
            text = "Đổi nhóm"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#2F4666"))
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener {
                val curTxt = editBox.text?.toString()?.trim().orEmpty()
                if (curTxt.isNotEmpty() && curTxt != note.cleanText) {
                    WidgetVaultHelper.updateNoteFromWidget(this@WidgetActionActivity, uid, curTxt, isStarred)
                }
                renderAssignNoteGroupSheet(uid)
            }
        }
        actRow.addView(
            btnGroup,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            }
        )

        actRow.addView(
            TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }
        )

        val btnSave = TextView(this).apply {
            text = "Lưu sửa"
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#0F172A"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#38BDF8"))
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(15), dp(7), dp(15), dp(7))
            setOnClickListener {
                val txt = editBox.text?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    WidgetVaultHelper.updateNoteFromWidget(this@WidgetActionActivity, uid, txt, isStarred)
                    hapticTick()
                }
                finishSilently()
            }
        }
        actRow.addView(btnSave)
        card.addView(actRow)

        mountBottomCard(card, openKeyboardFor = editBox)
    }

    private fun renderEditTaskSheet(uid: String) {
        val task = WidgetVaultHelper.findTaskByUid(this, uid)
        if (task == null) {
            finishSilently()
            return
        }
        var isStarred = task.starred
        val card = makeSheetCard()

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            TextView(this).apply {
                text = task.groupLabel
                textSize = 14.5f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor("#A78BFA"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topRow.addView(
            TextView(this).apply {
                text = "Mở App ↗"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(8), dp(5), dp(8), dp(5))
                setOnClickListener {
                    startActivity(Intent(this@WidgetActionActivity, MainActivity::class.java).apply {
                        putExtra("open_tab", "task")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finishSilently()
                }
            }
        )
        card.addView(topRow)

        val editBox = EditText(this).apply {
            setText(task.cleanText)
            setSelection(task.cleanText.length)
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 14.5f
            minLines = 2
            maxLines = 4
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222632"))
                setStroke(dp(1), Color.parseColor("#353D52"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        card.addView(
            editBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actRow.addView(
            TextView(this).apply {
                text = "Xóa"
                textSize = 12.5f
                setTextColor(Color.parseColor("#F87171"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A1C22"))
                    setStroke(dp(1), Color.parseColor("#5C2B33"))
                    cornerRadius = dp(9).toFloat()
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener {
                    WidgetVaultHelper.deleteTaskFromWidget(this@WidgetActionActivity, uid)
                    hapticTick()
                    finishSilently()
                }
            }
        )
        actRow.addView(
            TextView(this).apply {
                text = "Đổi nhóm"
                textSize = 12.5f
                setTextColor(Color.parseColor("#C4B5FD"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#231F36"))
                    setStroke(dp(1), Color.parseColor("#4B3F72"))
                    cornerRadius = dp(9).toFloat()
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener {
                    val curTxt = editBox.text?.toString()?.trim().orEmpty()
                    if (curTxt.isNotEmpty() && curTxt != task.cleanText) {
                        WidgetVaultHelper.updateTaskFromWidget(this@WidgetActionActivity, uid, curTxt, isStarred)
                    }
                    renderAssignTaskGroupSheet(uid)
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            }
        )
        actRow.addView(TextView(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        actRow.addView(
            TextView(this).apply {
                text = "Lưu sửa"
                textSize = 13f
                paint.isFakeBoldText = true
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#8B5CF6"))
                    cornerRadius = dp(9).toFloat()
                }
                setPadding(dp(15), dp(7), dp(15), dp(7))
                setOnClickListener {
                    val txt = editBox.text?.toString()?.trim().orEmpty()
                    if (txt.isNotEmpty()) {
                        WidgetVaultHelper.updateTaskFromWidget(this@WidgetActionActivity, uid, txt, isStarred)
                        hapticTick()
                    }
                    finishSilently()
                }
            }
        )
        card.addView(actRow)
        mountBottomCard(card, openKeyboardFor = editBox)
    }
}
