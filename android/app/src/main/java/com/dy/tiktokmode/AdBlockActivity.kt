package com.dy.tiktokmode

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Dedicated ad-block settings page reached by long-pressing the menu's
 * "广告拦截" tile: built-in rules toggle, a view of all user-marked ad
 * selectors, and the rule-subscription editor/updater.
 */
class AdBlockActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_block)
        prefs = Prefs(this)
        box = findViewById(R.id.adBlockBox)
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        buildRows()
    }

    private fun refresh() { box.removeAllViews(); buildRows() }

    private fun buildRows() {
        switchRow("启用内建规则", "使用内置的广告/追踪域名列表", prefs.adBlockEnabled) {
            prefs.adBlockEnabled = it; AdBlocker.enabled = it
        }

        val marks = AdMarkStore.allMarks()
        val markCount = marks.values.sumOf { it.size }
        row("自定义规则", "已标记 $markCount 条 (来自「标记广告」)") { showMarkedAds() }

        val ruleSummary = if (prefs.adSubscriptionRuleCount > 0)
            "已加载 ${prefs.adSubscriptionRuleCount} 条规则" else "尚未更新"
        row("规则订阅", ruleSummary) { showSubscriptionDialog() }
    }

    private fun showMarkedAds() {
        val marks = AdMarkStore.allMarks()
        val sheet = AlertDialog.Builder(this).setTitle("已标记的广告")
        val scroll = androidx.core.widget.NestedScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
        if (marks.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "还没有手动标记的广告，从菜单点击「标记广告」即可标记"
                setTextColor(0xFF888890.toInt()); setPadding(dp(10), dp(16), dp(10), dp(16))
            })
        }
        marks.forEach { (host, selectors) ->
            list.addView(TextView(this).apply {
                text = host; setTextColor(0xFFFE2C55.toInt()); textSize = 12f
                setPadding(dp(10), dp(10), dp(10), dp(2))
            })
            selectors.forEach { sel ->
                list.addView(TextView(this).apply {
                    text = sel; setTextColor(0xFFE8E8EC.toInt()); textSize = 13f
                    setPadding(dp(14), dp(2), dp(10), dp(2))
                })
            }
        }
        scroll.addView(list)
        sheet.setView(scroll)
        if (marks.isNotEmpty()) {
            sheet.setNegativeButton("全部清除") { _, _ ->
                AdMarkStore.clearAll()
                Toast.makeText(this, "已清除全部标记", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        sheet.setPositiveButton("关闭", null).show()
    }

    private fun showSubscriptionDialog() {
        val input = EditText(this).apply {
            setText(prefs.adSubscriptions); hint = "每行一个，hosts 格式或 AdBlock Plus"
            minLines = 4; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this).setTitle("规则订阅 URL").setView(input)
            .setPositiveButton("保存并更新") { _, _ ->
                prefs.adSubscriptions = input.text.toString().trim()
                Toast.makeText(this, "正在下载…", Toast.LENGTH_SHORT).show()
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
            .setNegativeButton("取消", null)
            .show()
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
        r.addView(TextView(this).apply {
            text = summary; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
            setPadding(0, dp(2), 0, 0); maxLines = 2
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
