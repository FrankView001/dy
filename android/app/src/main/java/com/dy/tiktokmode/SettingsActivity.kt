package com.dy.tiktokmode

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Via-style settings screen: vertical list of rows grouped by category.
 * Each row = title + summary + right-side widget (chevron / switch).
 * Tapping a chevron row opens an inline edit dialog; switches toggle in place.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout
    private lateinit var subSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        box = findViewById(R.id.settingsBox)
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        buildRows()
    }

    override fun onResume() {
        super.onResume()
        // Refresh summaries (e.g. rule count after an update completes).
        box.removeAllViews()
        buildRows()
    }

    private fun buildRows() {
        category("搜索")
        val engines = SearchEngines.BUILT_IN
        val engineKeys = engines.map { it.key } + "custom"
        val engineLabels = engines.map { it.label } + "自定义"
        row("搜索引擎", engineLabels[engineKeys.indexOf(prefs.searchEngineKey).coerceAtLeast(0)]) {
            AlertDialog.Builder(this).setTitle("搜索引擎")
                .setItems(engineLabels.toTypedArray()) { _, i ->
                    prefs.searchEngineKey = engineKeys[i]; refresh()
                }.show()
        }
        if (prefs.searchEngineKey == "custom") {
            inputRow("自定义搜索 URL", prefs.customSearchUrl, "需含 %s") { v ->
                prefs.customSearchUrl = v
            }
        }

        category("浏览")
        inputRow("自定义 User-Agent", prefs.customUa, "留空使用默认") { v -> prefs.customUa = v }
        inputRow("主页地址", prefs.homepageUrl, "默认 about:home") { v ->
            prefs.homepageUrl = v.ifBlank { Prefs.HOME_URL }
        }
        inputRow("主页标题", prefs.homepageTitle, "Logo 文字") { v ->
            prefs.homepageTitle = v.ifBlank { "DY 浏览器" }
        }
        inputRow("主页背景图", prefs.homepageBackground, "图片 URL，可选") { v ->
            prefs.homepageBackground = v
        }

        category("广告拦截")
        switchRow("启用广告拦截", "拦截已知广告与追踪域名", prefs.adBlockEnabled) {
            prefs.adBlockEnabled = it; AdBlocker.enabled = it
        }
        val ruleSummary = if (prefs.adSubscriptionRuleCount > 0)
            "已加载 ${prefs.adSubscriptionRuleCount} 条规则" else "尚未更新"
        inputRow("订阅规则 URL", prefs.adSubscriptions, "每行一个，hosts 格式或 AdBlock Plus") { v ->
            prefs.adSubscriptions = v
        }
        subSummary = TextView(this).apply {
            text = "  $ruleSummary"
            setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(dp(18), 0, dp(18), dp(6))
        }
        box.addView(subSummary)
        row("立即更新订阅", "下载并合并所有订阅规则") {
            subSummary.text = "  正在下载…"
            Thread {
                val urls = prefs.adSubscriptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val n = AdSubscription.updateBlocking(this, urls)
                prefs.adSubscriptionRuleCount = AdBlocker.ruleCount()
                runOnUiThread {
                    Toast.makeText(this,
                        if (n > 0) "已更新，新增 $n 条" else "更新失败或返回 0 条",
                        Toast.LENGTH_LONG).show()
                    refresh()
                }
            }.start()
        }

        category("自定义脚本")
        inputRow("自定义 CSS", prefs.customCss, "注入到每个页面", multiline = true) { v -> prefs.customCss = v }
        inputRow("用户脚本", prefs.userScript, "油猴风格，全站注入 JS", multiline = true) { v ->
            prefs.userScript = v
        }

        category("隐私")
        switchRow("发送 Do-Not-Track 头", "在请求中加入 DNT:1", prefs.doNotTrack) {
            prefs.doNotTrack = it
        }

        category("关于")
        row("版本", "v" + try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (_: Exception) { "1.0" }) {}
        row("说明", "WebView 多标签浏览器 · 抖音模式") {}
    }

    private fun refresh() { box.removeAllViews(); buildRows() }

    private fun category(title: String) {
        box.addView(TextView(this).apply {
            text = title
            setTextColor(0xFFFE2C55.toInt())
            textSize = 12f
            setPadding(dp(18), dp(18), dp(18), dp(6))
        })
    }

    private fun row(title: String, summary: String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundResource(rippleBg())
            setOnClickListener { onClick() }
        }
        r.addView(TextView(this).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
        })
        if (summary.isNotEmpty()) {
            r.addView(TextView(this).apply {
                text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
                setPadding(0, dp(2), 0, 0); maxLines = 2
            })
        }
        box.addView(r)
    }

    private fun inputRow(title: String, value: String, hint: String, multiline: Boolean = false, onSave: (String) -> Unit) {
        val summary = if (value.isBlank()) hint else value.take(60) + if (value.length > 60) "…" else ""
        row(title, summary) {
            val input = EditText(this).apply {
                setText(value); setHint(hint)
                if (multiline) {
                    minLines = 4
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    gravity = Gravity.TOP
                }
            }
            AlertDialog.Builder(this).setTitle(title).setView(input)
                .setPositiveButton("保存") { _, _ ->
                    onSave(input.text.toString().trim()); refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }
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
        txt.addView(TextView(this).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
        })
        txt.addView(TextView(this).apply {
            text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(0, dp(2), 0, 0); maxLines = 2
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
