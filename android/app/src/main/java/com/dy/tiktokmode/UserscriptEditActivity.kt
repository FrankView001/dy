package com.dy.tiktokmode

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

class UserscriptEditActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "scriptId" }

    private lateinit var store: UserscriptStore
    private var script: Userscript? = null

    private lateinit var nameInput: EditText
    private lateinit var runAtLabel: TextView
    private lateinit var matchesHolder: LinearLayout
    private lateinit var excludesHolder: LinearLayout
    private val matchEdits = mutableListOf<EditText>()
    private val excludeEdits = mutableListOf<EditText>()
    private var runAt: String = Userscript.RUN_DOC_END

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = UserscriptStore(this)
        val id = intent.getStringExtra(EXTRA_ID) ?: ""
        script = store.byId(id)
        if (script == null) { finish(); return }
        runAt = script!!.runAt
        setContentView(build())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build(): View {
        val s = script!!
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E0E10.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); background = null
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { saveAndExit() }
        })
        top.addView(TextView(this).apply {
            text = "编辑"; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = "保存"; setTextColor(0xFFFE2C55.toInt()); textSize = 14f
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener { saveAndExit() }
        })
        root.addView(top)

        val scroll = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }

        box.addView(label("脚本名称"))
        nameInput = EditText(this).apply {
            setText(s.name); setTextColor(0xFFFFFFFF.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        box.addView(nameInput)

        box.addView(label("运行时机"))
        runAtLabel = TextView(this).apply {
            text = s.runAt
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener { pickRunAt() }
        }
        box.addView(runAtLabel)

        box.addView(label("匹配（Match）"))
        matchesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(matchesHolder)
        s.matches.forEach { addMatchRow(it, false) }
        if (s.matches.isEmpty()) addMatchRow("*://*/*", false)
        box.addView(addRowBtn("＋ 添加匹配") { addMatchRow("", false) })

        box.addView(label("排除（Exclude）"))
        excludesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(excludesHolder)
        s.excludes.forEach { addMatchRow(it, true) }
        box.addView(addRowBtn("＋ 添加排除") { addMatchRow("", true) })

        // Advanced
        box.addView(TextView(this).apply {
            text = "高级"; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(0, dp(20), 0, dp(8))
        })
        box.addView(advancedButton("编辑源代码") { editSource() })
        box.addView(advancedButton("重置") { resetScript() })

        scroll.addView(box)
        root.addView(scroll)
        return root
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text; setTextColor(0xFFB5B5BC.toInt()); textSize = 12f
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun addMatchRow(value: String, isExclude: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(this).apply {
            setText(value); setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val del = TextView(this).apply {
            text = "✕"; setTextColor(0xFFFE2C55.toInt()); textSize = 16f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                (if (isExclude) excludesHolder else matchesHolder).removeView(row)
                (if (isExclude) excludeEdits else matchEdits).remove(edit)
            }
        }
        row.addView(edit); row.addView(del)
        (if (isExclude) excludesHolder else matchesHolder).addView(row)
        (if (isExclude) excludeEdits else matchEdits).add(edit)
    }

    private fun addRowBtn(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(0xFFFE2C55.toInt()); textSize = 13f
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener { onClick() }
    }

    private fun advancedButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        background = null
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun pickRunAt() {
        val opts = arrayOf(Userscript.RUN_DOC_END, Userscript.RUN_DOC_START, Userscript.RUN_DOC_IDLE)
        AlertDialog.Builder(this)
            .setTitle("运行时机")
            .setItems(opts) { _, which ->
                runAt = opts[which]; runAtLabel.text = runAt
            }
            .show()
    }

    private fun editSource() {
        val s = script ?: return
        val edit = EditText(this).apply {
            setText(s.source); minLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
        }
        AlertDialog.Builder(this)
            .setTitle("源代码")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                s.source = edit.text.toString()
                store.update(s)
                Toast.makeText(this, "已更新源代码", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resetScript() {
        val s = script ?: return
        AlertDialog.Builder(this)
            .setMessage("重置为最近一次保存的状态？")
            .setPositiveButton("重置") { _, _ ->
                script = store.byId(s.id)
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAndExit() {
        val s = script ?: return finish()
        s.name = nameInput.text.toString().ifBlank { "未命名脚本" }
        s.runAt = runAt
        s.matches = matchEdits.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        s.excludes = excludeEdits.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        store.update(s)
        finish()
    }
}
