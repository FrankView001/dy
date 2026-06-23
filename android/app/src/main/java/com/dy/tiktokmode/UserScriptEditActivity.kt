package com.dy.tiktokmode

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Single-script editor: name, run timing, match/exclude URL patterns, source. */
class UserScriptEditActivity : AppCompatActivity() {

    private lateinit var store: UserScriptStore
    private lateinit var box: LinearLayout
    private lateinit var script: UserScript

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_script_edit)
        store = UserScriptStore(this)
        box = findViewById(R.id.editBox)
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        val id = intent.getStringExtra("id") ?: return finish()
        script = store.all().find { it.id == id } ?: return finish()
        buildRows()
    }

    private fun refresh() {
        script = store.all().find { it.id == script.id } ?: return finish()
        box.removeAllViews(); buildRows()
    }

    private fun buildRows() {
        row("脚本名字", script.name) {
            val input = EditText(this).apply { setText(script.name) }
            AlertDialog.Builder(this).setTitle("脚本名字").setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.isNotEmpty()) { store.update(script.copy(name = n)); refresh() }
                }.setNegativeButton("取消", null).show()
        }
        row("运行时机", runAtLabel(script.runAt)) {
            val options = listOf("document-start", "document-end", "document-idle")
            AlertDialog.Builder(this).setTitle("运行时机")
                .setItems(options.toTypedArray()) { _, i ->
                    store.update(script.copy(runAt = options[i])); refresh()
                }.show()
        }

        section("匹配")
        script.matches.forEach { m -> patternRow(m) { store.update(script.copy(matches = script.matches - m)); refresh() } }
        addPatternRow("添加匹配") { p -> store.update(script.copy(matches = script.matches + p)); refresh() }

        section("排除")
        script.excludes.forEach { m -> patternRow(m) { store.update(script.copy(excludes = script.excludes - m)); refresh() } }
        addPatternRow("添加排除") { p -> store.update(script.copy(excludes = script.excludes + p)); refresh() }

        section("高级")
        row("编辑源代码", "") {
            val input = EditText(this).apply {
                setText(script.code); minLines = 10; gravity = Gravity.TOP
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            AlertDialog.Builder(this).setTitle("编辑源代码").setView(input)
                .setPositiveButton("保存") { _, _ -> store.update(script.copy(code = input.text.toString())); refresh() }
                .setNegativeButton("取消", null).show()
        }
        row("重置", "删除此脚本") {
            AlertDialog.Builder(this).setTitle("重置")
                .setMessage("确定要删除「${script.name}」吗？")
                .setPositiveButton("删除") { _, _ -> store.remove(script.id); finish() }
                .setNegativeButton("取消", null).show()
        }
    }

    private fun runAtLabel(v: String) = when (v) {
        "document-start" -> "document-start"; "document-idle" -> "document-idle"; else -> "document-end"
    }

    private fun section(title: String) {
        box.addView(TextView(this).apply {
            text = title; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(dp(18), dp(16), dp(18), dp(4))
        })
    }

    private fun patternRow(pattern: String, onRemove: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        r.addView(TextView(this).apply {
            text = pattern; setTextColor(0xFFE8E8EC.toInt()); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        r.addView(TextView(this).apply {
            text = "删除"; setTextColor(0xFFFE2C55.toInt()); textSize = 13f
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { onRemove() }
        })
        box.addView(r)
    }

    private fun addPatternRow(label: String, onAdd: (String) -> Unit) {
        row("+ $label", "") {
            val input = EditText(this).apply { hint = "例如 *://*.example.com/*" }
            AlertDialog.Builder(this).setTitle(label).setView(input)
                .setPositiveButton("添加") { _, _ ->
                    val p = input.text.toString().trim()
                    if (p.isNotEmpty()) onAdd(p)
                }.setNegativeButton("取消", null).show()
        }
    }

    private fun row(title: String, summary: String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundResource(rippleBg())
            setOnClickListener { onClick() }
        }
        r.addView(TextView(this).apply { text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f })
        if (summary.isNotEmpty()) {
            r.addView(TextView(this).apply {
                text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
                setPadding(0, dp(2), 0, 0); maxLines = 2
            })
        }
        box.addView(r)
    }

    private fun rippleBg(): Int {
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return outValue.resourceId
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
