package com.dy.tiktokmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Tampermonkey-style script manager: list of user scripts with per-script enable toggle. */
class UserScriptActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: UserScriptStore
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_scripts)
        prefs = Prefs(this)
        store = UserScriptStore(this)
        box = findViewById(R.id.scriptBox)
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.addBtn).setOnClickListener { showAddMenu(it) }
        findViewById<TextView>(R.id.updateBtn).setOnClickListener { updateAll() }
        buildRows()
    }

    override fun onResume() {
        super.onResume()
        box.removeAllViews(); buildRows()
    }

    private fun refresh() { box.removeAllViews(); buildRows() }

    private fun showAddMenu(anchor: android.view.View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("添加脚本"); menu.menu.add("下载脚本"); menu.menu.add("导入脚本")
        menu.setOnMenuItemClickListener { item ->
            when (item.title) {
                "添加脚本" -> newScriptDialog()
                "下载脚本" -> downloadScriptDialog()
                "导入脚本" -> Toast.makeText(this, "请通过「下载脚本」粘贴源码地址导入", Toast.LENGTH_SHORT).show()
            }
            true
        }
        menu.show()
    }

    private fun newScriptDialog() {
        val input = EditText(this).apply { hint = "脚本名字" }
        AlertDialog.Builder(this).setTitle("添加脚本").setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "新脚本" }
                store.add(UserScript(id = "s_" + System.nanoTime(), name = name, code = "// ==UserScript==\n"))
                refresh()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun downloadScriptDialog() {
        val input = EditText(this).apply { hint = "脚本源码 URL (.user.js)" }
        AlertDialog.Builder(this).setTitle("下载脚本").setView(input)
            .setPositiveButton("下载") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isBlank()) return@setPositiveButton
                Toast.makeText(this, "正在下载…", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val code = java.net.URL(url).readText()
                        val name = Regex("@name\\s+(.+)").find(code)?.groupValues?.get(1)?.trim() ?: "下载的脚本"
                        store.add(UserScript(id = "s_" + System.nanoTime(), name = name, code = code, updateUrl = url))
                        runOnUiThread { Toast.makeText(this, "已添加: $name", Toast.LENGTH_SHORT).show(); refresh() }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show() }
                    }
                }.start()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun updateAll() {
        val targets = store.all().filter { it.updateUrl.isNotBlank() }
        if (targets.isEmpty()) { Toast.makeText(this, "没有可更新的脚本", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "正在更新 ${targets.size} 个脚本…", Toast.LENGTH_SHORT).show()
        Thread {
            var n = 0
            targets.forEach { s ->
                try {
                    val code = java.net.URL(s.updateUrl).readText()
                    store.update(s.copy(code = code))
                    n++
                } catch (_: Exception) {}
            }
            runOnUiThread { Toast.makeText(this, "已更新 $n 个脚本", Toast.LENGTH_SHORT).show(); refresh() }
        }.start()
    }

    private fun buildRows() {
        switchRow("启用脚本", "关闭后停用所有脚本", prefs.userScriptsEnabled) {
            prefs.userScriptsEnabled = it
        }
        row("自动更新", autoUpdateLabel(prefs.userScriptAutoUpdate)) {
            val options = listOf("从不" to "never", "每天" to "daily", "每3天" to "3days", "每周" to "weekly", "每月" to "monthly")
            AlertDialog.Builder(this).setTitle("自动更新")
                .setItems(options.map { it.first }.toTypedArray()) { _, i ->
                    prefs.userScriptAutoUpdate = options[i].second; refresh()
                }.show()
        }

        box.addView(TextView(this).apply {
            text = "脚本"; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(dp(18), dp(16), dp(18), dp(4))
        })
        val scripts = store.all()
        if (scripts.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "还没有脚本，点击右上角「+」添加"
                setTextColor(0xFF888890.toInt()); setPadding(dp(18), dp(8), dp(18), dp(16))
            })
        }
        scripts.forEach { s -> box.addView(scriptRow(s)) }
    }

    private fun scriptRow(s: UserScript): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundResource(rippleBg())
            setOnClickListener {
                startActivity(Intent(this@UserScriptActivity, UserScriptEditActivity::class.java)
                    .putExtra("id", s.id))
            }
        }
        r.addView(TextView(this).apply {
            text = s.name; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = Switch(this).apply { isChecked = s.enabled }
        sw.setOnCheckedChangeListener { _, checked -> store.setEnabled(s.id, checked) }
        r.addView(sw)
        return r
    }

    private fun autoUpdateLabel(key: String): String = when (key) {
        "daily" -> "每天"; "3days" -> "每3天"; "weekly" -> "每周"; "monthly" -> "每月"; else -> "从不"
    }

    private fun row(title: String, summary: String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundResource(rippleBg())
            setOnClickListener { onClick() }
        }
        r.addView(TextView(this).apply { text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f })
        r.addView(TextView(this).apply {
            text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        box.addView(r)
    }

    private fun switchRow(title: String, summary: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundResource(rippleBg())
        }
        val txt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        txt.addView(TextView(this).apply { text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f })
        txt.addView(TextView(this).apply {
            text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        r.addView(txt)
        val sw = Switch(this).apply { isChecked = value }
        sw.setOnCheckedChangeListener { _, b -> onChange(b) }
        r.addView(sw)
        r.setOnClickListener { sw.isChecked = !sw.isChecked }
        box.addView(r)
    }

    private fun rippleBg(): Int {
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return outValue.resourceId
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
