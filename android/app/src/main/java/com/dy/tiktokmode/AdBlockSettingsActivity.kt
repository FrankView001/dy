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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

class AdBlockSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rules: AdRuleStore
    private lateinit var subs: RuleSubscriptionStore
    private lateinit var rulesHolder: LinearLayout
    private lateinit var subsHolder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        rules = AdRuleStore(this)
        subs = RuleSubscriptionStore(this)
        setContentView(build())
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
            text = "广告拦截"; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setPadding(dp(8), 0, 0, 0)
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

        box.addView(switchRow("启用内建规则", prefs.adBlockBuiltInEnabled) { v ->
            prefs.adBlockBuiltInEnabled = v
            AdBlocker.builtInEnabled = v
        })
        box.addView(divider())

        box.addView(sectionHeader("自定义规则（标记广告）"))
        rulesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(rulesHolder)
        renderRules()

        box.addView(divider())
        box.addView(sectionHeader("规则订阅"))
        subsHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(subsHolder)
        val addSub = TextView(this).apply {
            text = "+ 添加订阅"
            setTextColor(0xFFFE2C55.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { showAddSubscription() }
        }
        box.addView(addSub)
        renderSubs()

        scroll.addView(box)
        root.addView(scroll)
        return root
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
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

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0xFF2A2A2E.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }

    private fun renderRules() {
        rulesHolder.removeAllViews()
        val all = rules.all()
        if (all.isEmpty()) {
            rulesHolder.addView(TextView(this).apply {
                text = "（暂无自定义规则）"; setTextColor(0xFF6E6E76.toInt()); textSize = 13f
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        all.forEach { rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            text.addView(TextView(this).apply {
                this.text = rule.selector; setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
            })
            text.addView(TextView(this).apply {
                this.text = if (rule.domain.isBlank()) "全部网站" else rule.domain
                setTextColor(0xFF8A8A92.toInt()); textSize = 11f
            })
            row.addView(text)
            row.addView(TextView(this).apply {
                text = "删除"; setTextColor(0xFFFE2C55.toInt()); textSize = 13f
                setPadding(dp(12), dp(6), dp(6), dp(6))
                setOnClickListener { rules.remove(rule.id); renderRules() }
            })
            rulesHolder.addView(row)
        }
    }

    private fun renderSubs() {
        subsHolder.removeAllViews()
        val all = subs.all()
        if (all.isEmpty()) {
            subsHolder.addView(TextView(this).apply {
                text = "（暂无订阅源）"; setTextColor(0xFF6E6E76.toInt()); textSize = 13f
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        all.forEach { sub ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            text.addView(TextView(this).apply {
                this.text = sub.name; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            })
            text.addView(TextView(this).apply {
                this.text = sub.url; setTextColor(0xFF8A8A92.toInt()); textSize = 11f; maxLines = 1
            })
            row.addView(text)
            row.addView(Switch(this).apply {
                isChecked = sub.enabled
                setOnCheckedChangeListener { _, v -> subs.setEnabled(sub.id, v) }
            })
            row.addView(TextView(this).apply {
                text = "✕"; setTextColor(0xFFFE2C55.toInt()); textSize = 14f
                setPadding(dp(12), dp(6), dp(6), dp(6))
                setOnClickListener { subs.remove(sub.id); renderSubs() }
            })
            subsHolder.addView(row)
        }
    }

    private fun showAddSubscription() {
        val nameInput = EditText(this).apply { hint = "名称（如 EasyList）" }
        val urlInput = EditText(this).apply { hint = "订阅 URL" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(nameInput); addView(urlInput)
        }
        AlertDialog.Builder(this)
            .setTitle("添加订阅")
            .setView(box)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    subs.add(name, url); renderSubs()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
