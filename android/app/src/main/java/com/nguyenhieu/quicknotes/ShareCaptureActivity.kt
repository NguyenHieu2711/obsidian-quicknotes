package com.nguyenhieu.quicknotes

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShareCaptureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedSubject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val sharedBody = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val initialText = when {
            sharedSubject.isNotEmpty() && sharedBody.isNotEmpty() && !sharedBody.contains(sharedSubject) ->
                "$sharedSubject — $sharedBody"
            sharedBody.isNotEmpty() -> sharedBody
            else -> sharedSubject
        }

        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#0E1420"))
            setStroke(dp(1), Color.parseColor("#27364F"))
            cornerRadius = dp(20).toFloat()
        }

        val inputBg = GradientDrawable().apply {
            setColor(Color.parseColor("#131B2B"))
            setStroke(dp(1), Color.parseColor("#2B3D5B"))
            cornerRadius = dp(12).toFloat()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }

        val header = TextView(this).apply {
            text = "Lưu nhanh vào Obsidian"
            textSize = 16.5f
            setTextColor(Color.parseColor("#F8FAFC"))
            paint.isFakeBoldText = true
        }
        root.addView(header)

        val editBox = EditText(this).apply {
            setText(initialText)
            setTextColor(Color.parseColor("#E2E8F0"))
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "Nội dung ghi nhanh..."
            textSize = 14.5f
            minLines = 2
            maxLines = 5
            background = inputBg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            lp.bottomMargin = dp(12)
            layoutParams = lp
        }
        root.addView(editBox)

        fun makeActionRow(label: String, accent: Boolean, onClick: () -> Unit): TextView {
            val rowBg = GradientDrawable().apply {
                setColor(if (accent) Color.parseColor("#152942") else Color.parseColor("#121926"))
                setStroke(dp(1), if (accent) Color.parseColor("#38BDF8") else Color.parseColor("#24324A"))
                cornerRadius = dp(12).toFloat()
            }
            return TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(if (accent) Color.parseColor("#38BDF8") else Color.parseColor("#E2E8F0"))
                paint.isFakeBoldText = accent
                background = rowBg
                setPadding(dp(14), dp(11), dp(14), dp(11))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(8)
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        fun dispatchAndClose(targetType: String, streamTitle: String = "", labelToast: String) {
            val content = editBox.text?.toString()?.trim().orEmpty()
            if (content.isEmpty()) {
                Toast.makeText(this, "Chưa có nội dung để lưu.", Toast.LENGTH_SHORT).show()
                return
            }
            val ok = VaultRepository.appendCaptureItem(this, content, targetType, streamTitle)
            if (ok) {
                Toast.makeText(this, "✓ Đã lưu vào $labelToast", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Không thể ghi vào Vault.", Toast.LENGTH_SHORT).show()
            }
        }

        val listWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        listWrap.addView(
            makeActionRow("⚡ Thả vào Nháp nhanh (Nghĩ sau)", accent = true) {
                dispatchAndClose("quick", labelToast = "Nháp nhanh")
            }
        )

        val streams = VaultRepository.listActiveNoteStreams(this)
        for ((grp, title) in streams.take(6)) {
            val prefix = if (grp == "active") "🎯" else "📥"
            listWrap.addView(
                makeActionRow("$prefix $title", accent = false) {
                    dispatchAndClose("stream", title, title)
                }
            )
        }

        listWrap.addView(
            makeActionRow("☑ Thêm vào Trọng tâm hôm nay", accent = false) {
                dispatchAndClose("focus_task", labelToast = "Trọng tâm hôm nay")
            }
        )
        listWrap.addView(
            makeActionRow("📋 Đưa vào Kho chờ Task", accent = false) {
                dispatchAndClose("warehouse_task", labelToast = "Kho chờ Task")
            }
        )

        val scroll = ScrollView(this).apply {
            addView(listWrap)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(scroll)

        setContentView(root)
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.BOTTOM)
    }
}
