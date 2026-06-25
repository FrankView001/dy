package com.dy.tiktokmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

class UserscriptListActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: UserscriptStore
    private lateinit var listHolder: LinearLayout
    private val http = OkHttpClient()
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = UserscriptStore(this)
        setContentView(build())
        renderList()
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build(): View {
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
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "脚本"; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = "＋"; setTextColor(0xFFFE2C55.toInt()); textSize = 22f
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener { showAddMenu(this) }
        })
        top.addView(TextView(this).apply {
            text = "更新"; setTextColor(0xFFFE2C55.toInt()); textSize = 14f
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener { updateAll() }
        })
        root.addView(top)

        // Global controls
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        controls.addView(controlRow("启用脚本", prefs.userscriptsEnabled) { prefs.userscriptsEnabled = it })
        controls.addView(autoUpdateRow())
        root.addView(controls)

        root.addView(TextView(this).apply {
            text = "脚本"; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(dp(16), dp(14), dp(16), dp(6))
        })

        val scroll = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listHolder)
        root.addView(scroll)
        return root
    }

    private fun controlRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = label; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
        return row
    }

    private fun autoUpdateRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val label = TextView(this).apply {
            text = "自动更新"; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val current = TextView(this).apply {
            text = autoUpdateLabel(prefs.userscriptAutoUpdate)
            setTextColor(0xFFB5B5BC.toInt()); textSize = 14f
            setPadding(dp(6), dp(6), dp(10), dp(6))
            setOnClickListener {
                val labels = arrayOf("从不", "每天", "每 3 天", "每周", "每月")
                val keys = arrayOf("never", "daily", "every3", "weekly", "monthly")
                AlertDialog.Builder(this@UserscriptListActivity)
                    .setTitle("自动更新周期")
                    .setItems(labels) { _, which ->
                        prefs.userscriptAutoUpdate = keys[which]
                        text = autoUpdateLabel(keys[which])
                    }
                    .show()
            }
        }
        row.addView(label); row.addView(current)
        return row
    }

    private fun autoUpdateLabel(key: String) = when (key) {
        "daily" -> "每天"; "every3" -> "每 3 天"; "weekly" -> "每周"; "monthly" -> "每月"; else -> "从不"
    }

    private fun renderList() {
        listHolder.removeAllViews()
        val items = store.all()
        if (items.isEmpty()) {
            listHolder.addView(TextView(this).apply {
                text = "暂无脚本，点击右上 ＋ 添加"
                setTextColor(0xFF888890.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return
        }
        items.forEach { script ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    startActivity(Intent(this@UserscriptListActivity, UserscriptEditActivity::class.java)
                        .putExtra(UserscriptEditActivity.EXTRA_ID, script.id))
                }
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            text.addView(TextView(this).apply {
                this.text = script.name; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
            })
            text.addView(TextView(this).apply {
                this.text = script.matches.joinToString(", ").ifBlank { "无匹配" }
                setTextColor(0xFF8A8A92.toInt()); textSize = 11f; maxLines = 1
            })
            row.addView(text)
            row.addView(Switch(this).apply {
                isChecked = script.enabled
                setOnCheckedChangeListener { _, v ->
                    script.enabled = v; store.update(script)
                }
            })
            listHolder.addView(row)
        }
    }

    private fun showAddMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("添加脚本")
        menu.menu.add("下载脚本")
        menu.menu.add("导入脚本")
        menu.setOnMenuItemClickListener { item ->
            when (item.title) {
                "添加脚本" -> { addManual(); true }
                "下载脚本" -> { downloadByUrl(); true }
                "导入脚本" -> {
                    Toast.makeText(this, "暂未实现：从本地文件导入", Toast.LENGTH_SHORT).show(); true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun addManual() {
        val edit = EditText(this).apply {
            hint = "在此粘贴脚本代码"
            minLines = 8; setHorizontallyScrolling(false)
        }
        AlertDialog.Builder(this)
            .setTitle("添加脚本")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val src = edit.text.toString()
                if (src.isNotBlank()) { store.add(src); renderList() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadByUrl() {
        val edit = EditText(this).apply { hint = "脚本 URL（.user.js）" }
        AlertDialog.Builder(this)
            .setTitle("下载脚本")
            .setView(edit)
            .setPositiveButton("下载") { _, _ ->
                val url = edit.text.toString().trim()
                if (url.isNotBlank()) fetchAndAdd(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun fetchAndAdd(url: String) {
        Toast.makeText(this, "正在下载...", Toast.LENGTH_SHORT).show()
        io.execute {
            try {
                val resp = http.newCall(Request.Builder().url(url).build()).execute()
                val body = resp.body?.string()
                if (resp.isSuccessful && !body.isNullOrBlank()) {
                    runOnUiThread {
                        store.add(body, url); renderList()
                        Toast.makeText(this, "已下载脚本", Toast.LENGTH_SHORT).show()
                    }
                } else runOnUiThread {
                    Toast.makeText(this, "下载失败：HTTP ${resp.code}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun updateAll() {
        val items = store.all().filter { it.downloadUrl.isNotBlank() }
        if (items.isEmpty()) {
            Toast.makeText(this, "没有可更新的脚本", Toast.LENGTH_SHORT).show(); return
        }
        Toast.makeText(this, "正在更新 ${items.size} 个脚本…", Toast.LENGTH_SHORT).show()
        io.execute {
            var ok = 0
            items.forEach { s ->
                try {
                    val resp = http.newCall(Request.Builder().url(s.downloadUrl).build()).execute()
                    val body = resp.body?.string()
                    if (resp.isSuccessful && !body.isNullOrBlank()) {
                        s.source = body; s.lastUpdated = System.currentTimeMillis(); store.update(s); ok++
                    }
                } catch (_: Exception) {}
            }
            runOnUiThread {
                Toast.makeText(this, "更新完成：$ok / ${items.size}", Toast.LENGTH_SHORT).show()
                renderList()
            }
        }
    }
}
