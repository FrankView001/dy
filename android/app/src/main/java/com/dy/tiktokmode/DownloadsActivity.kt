package com.dy.tiktokmode

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DownloadsActivity : AppCompatActivity() {

    private lateinit var store: DownloadStore
    private val selected = HashSet<Long>()
    private var editMode = false
    private var typeFilter: String = "全部"

    private lateinit var listHolder: LinearLayout
    private lateinit var filterStrip: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var editBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DownloadStore(this)
        setContentView(buildRoot())
        rebuildList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildRoot(): View {
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
            setImageResource(R.drawable.ic_arrow_back)
            background = null
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "下载"; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setPadding(dp(8), 0, 0, 0)
        })
        root.addView(top)

        val scrollFilters = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        filterStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        listOf("全部", "文档", "压缩包", "安装包", "图片", "视频", "音频", "其它").forEach { addFilterChip(it) }
        scrollFilters.addView(filterStrip)
        root.addView(scrollFilters)

        val scroll = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listHolder)
        root.addView(scroll)

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        bottomBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        bottomBar.addView(Button(this).apply {
            text = "编辑"; background = null; setTextColor(0xFFFE2C55.toInt())
            setOnClickListener { toggleEdit() }
        })
        root.addView(bottomBar)

        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        editBar.addView(Button(this).apply { text = "全选"; background = null; setTextColor(0xFFFFFFFF.toInt()); setOnClickListener { selectAll() } })
        editBar.addView(Button(this).apply { text = "删除"; background = null; setTextColor(0xFFFFFFFF.toInt()); setOnClickListener { deleteSelected() } })
        editBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        editBar.addView(Button(this).apply { text = "完成"; background = null; setTextColor(0xFFFE2C55.toInt()); setOnClickListener { toggleEdit() } })
        root.addView(editBar)

        return root
    }

    private fun addFilterChip(label: String) {
        val chip = TextView(this).apply {
            text = label
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setTextColor(if (label == typeFilter) 0xFFFE2C55.toInt() else 0xFFB5B5BC.toInt())
            background = resources.getDrawable(R.drawable.bg_pill, theme)
            textSize = 13f
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) }
        chip.layoutParams = params
        chip.setOnClickListener {
            typeFilter = label
            for (i in 0 until filterStrip.childCount) {
                val tv = filterStrip.getChildAt(i) as? TextView ?: continue
                tv.setTextColor(if (tv.text == typeFilter) 0xFFFE2C55.toInt() else 0xFFB5B5BC.toInt())
            }
            rebuildList()
        }
        filterStrip.addView(chip)
    }

    private fun rebuildList() {
        listHolder.removeAllViews()
        val items = store.all().filter { matchType(it) }
        if (items.isEmpty()) {
            listHolder.addView(TextView(this).apply {
                text = "暂无下载记录"; setTextColor(0xFF888890.toInt())
                gravity = Gravity.CENTER; setPadding(0, dp(60), 0, 0)
            })
            return
        }
        var lastGroup = ""
        items.forEach { entry ->
            val group = groupForTime(entry.time)
            if (group != lastGroup) {
                listHolder.addView(TextView(this).apply {
                    text = group; setTextColor(0xFF8A8A92.toInt()); textSize = 12f
                    setPadding(dp(14), dp(14), dp(14), dp(6))
                })
                lastGroup = group
            }
            listHolder.addView(buildRow(entry))
        }
    }

    private fun matchType(entry: DownloadEntry): Boolean {
        if (typeFilter == "全部") return true
        val name = entry.fileName.lowercase()
        return when (typeFilter) {
            "文档" -> listOf(".pdf", ".doc", ".docx", ".txt", ".xls", ".xlsx", ".ppt", ".pptx", ".md").any { name.endsWith(it) }
            "压缩包" -> listOf(".zip", ".rar", ".7z", ".tar", ".gz").any { name.endsWith(it) }
            "安装包" -> listOf(".apk", ".exe", ".dmg", ".msi", ".deb", ".rpm").any { name.endsWith(it) }
            "图片" -> listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg").any { name.endsWith(it) }
            "视频" -> listOf(".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".flv").any { name.endsWith(it) }
            "音频" -> listOf(".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg").any { name.endsWith(it) }
            "其它" -> {
                val all = listOf(".pdf", ".doc", ".docx", ".txt", ".xls", ".xlsx", ".ppt", ".pptx", ".md",
                    ".zip", ".rar", ".7z", ".tar", ".gz",
                    ".apk", ".exe", ".dmg", ".msi", ".deb", ".rpm",
                    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg",
                    ".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".flv",
                    ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg")
                !all.any { name.endsWith(it) }
            }
            else -> true
        }
    }

    private fun buildRow(entry: DownloadEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        if (editMode) {
            row.addView(CheckBox(this).apply {
                isChecked = selected.contains(entry.id)
                setOnCheckedChangeListener { _, c -> if (c) selected.add(entry.id) else selected.remove(entry.id) }
            })
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        text.addView(TextView(this).apply {
            this.text = entry.fileName; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; maxLines = 1
        })
        text.addView(TextView(this).apply {
            this.text = "${formatSize(entry.sizeBytes)} · ${entry.status}"
            setTextColor(0xFF8A8A92.toInt()); textSize = 12f
        })
        row.addView(text)
        return row
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    private fun groupForTime(time: Long): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        val sameDay = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "今天"
        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        if (yesterday) return "昨天"
        return SimpleDateFormat("MM月dd日 EEE", Locale.CHINA).format(Date(time))
    }

    private fun toggleEdit() {
        editMode = !editMode
        selected.clear()
        bottomBar.visibility = if (editMode) View.GONE else View.VISIBLE
        editBar.visibility = if (editMode) View.VISIBLE else View.GONE
        rebuildList()
    }

    private fun selectAll() {
        val all = store.all().filter { matchType(it) }.map { it.id }
        if (selected.containsAll(all)) selected.clear() else selected.addAll(all)
        rebuildList()
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage("删除选中的 ${selected.size} 项？")
            .setPositiveButton("删除") { _, _ ->
                store.all().filter { selected.contains(it.id) }.forEach { entry ->
                    try { File(entry.fileName).delete() } catch (e: Exception) {}
                }
                store.removeIds(selected.toSet())
                selected.clear()
                rebuildList()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
