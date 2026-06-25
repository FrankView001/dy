package com.dy.tiktokmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Combined Bookmarks / History / Offline page (matches the spec's tabbed layout). */
class LibraryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_BOOKMARKS = 0
        const val TAB_HISTORY = 1
        const val TAB_OFFLINE = 2
        const val RESULT_URL = "url"
    }

    private lateinit var bookmarks: BookmarkStore
    private lateinit var folders: BookmarkFolderStore
    private lateinit var history: HistoryStore
    private lateinit var offline: OfflineStore

    private var activeTab = TAB_BOOKMARKS
    private var editMode = false
    private val selectedKeys = HashSet<String>()
    private var currentFolderId: String = ""
    private var searchQuery = ""

    private lateinit var tabBookmarks: TextView
    private lateinit var tabHistory: TextView
    private lateinit var tabOffline: TextView
    private lateinit var listHolder: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var bottomBar: LinearLayout
    private lateinit var editBottomBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookmarks = BookmarkStore(this)
        folders = BookmarkFolderStore(this)
        history = HistoryStore(this)
        offline = OfflineStore(this)
        activeTab = intent.getIntExtra(EXTRA_TAB, TAB_BOOKMARKS)
        setContentView(buildRoot())
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------- UI construction ----------------

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E0E10.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top navigation: back + 3 tabs
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        topBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            background = null
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        })
        tabBookmarks = makeTab("书签") { switchTab(TAB_BOOKMARKS) }
        tabHistory = makeTab("历史") { switchTab(TAB_HISTORY) }
        tabOffline = makeTab("离线") { switchTab(TAB_OFFLINE) }
        topBar.addView(tabBookmarks)
        topBar.addView(tabHistory)
        topBar.addView(tabOffline)
        root.addView(topBar)

        // Search bar
        searchInput = EditText(this).apply {
            hint = "搜索"
            setBackgroundColor(0xFF1C1C1F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF6E6E76.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString().orEmpty().trim().lowercase()
                    rebuildList()
                }
            })
        }
        root.addView(searchInput)

        // List area
        val scroll = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listHolder)
        root.addView(scroll)

        bottomBar = buildBottomBar()
        editBottomBar = buildEditBar()
        root.addView(bottomBar)
        root.addView(editBottomBar)
        editBottomBar.visibility = View.GONE
        return root
    }

    private fun makeTab(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
    }

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        // Left slot: tabs button (history) / empty for others
        val leftBtn = Button(this).apply {
            text = ""
            background = null
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tag = "left"
        }
        bar.addView(leftBtn)

        // Middle/right buttons rebuilt per tab
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val rightBtn1 = Button(this).apply {
            background = null
            setTextColor(0xFFFE2C55.toInt())
            tag = "right1"
        }
        val rightBtn2 = Button(this).apply {
            background = null
            setTextColor(0xFFFE2C55.toInt())
            tag = "right2"
            text = "编辑"
            setOnClickListener { toggleEditMode() }
        }
        bar.addView(rightBtn1)
        bar.addView(rightBtn2)
        return bar
    }

    private fun buildEditBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1C1C1F.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(editButton("全选") { selectAll() })
        bar.addView(editButton("移动") { moveSelected() }.apply { tag = "move" })
        bar.addView(editButton("删除") { deleteSelected() })
        bar.addView(editButton("打开") { openSelected() }.apply { tag = "open" })
        bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        bar.addView(Button(this).apply {
            text = "完成"
            background = null
            setTextColor(0xFFFE2C55.toInt())
            setOnClickListener { toggleEditMode() }
        })
        return bar
    }

    private fun editButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        background = null
        setTextColor(0xFFFFFFFF.toInt())
        setOnClickListener { onClick() }
    }

    // ---------------- Tab switching / state ----------------

    private fun switchTab(tab: Int) {
        if (editMode) toggleEditMode()
        activeTab = tab
        currentFolderId = ""
        refresh()
    }

    private fun refresh() {
        val accent = 0xFFFE2C55.toInt()
        val muted = 0xFFB5B5BC.toInt()
        tabBookmarks.setTextColor(if (activeTab == TAB_BOOKMARKS) accent else muted)
        tabHistory.setTextColor(if (activeTab == TAB_HISTORY) accent else muted)
        tabOffline.setTextColor(if (activeTab == TAB_OFFLINE) accent else muted)
        rebuildBottomBar()
        rebuildList()
    }

    private fun rebuildBottomBar() {
        val leftBtn = bottomBar.findViewWithTag<Button>("left")
        val right1 = bottomBar.findViewWithTag<Button>("right1")
        val right2 = bottomBar.findViewWithTag<Button>("right2")
        val moveBtn = editBottomBar.findViewWithTag<Button>("move")
        val openBtn = editBottomBar.findViewWithTag<Button>("open")
        moveBtn?.visibility = if (activeTab == TAB_BOOKMARKS) View.VISIBLE else View.GONE
        openBtn?.visibility = if (activeTab == TAB_BOOKMARKS) View.VISIBLE else View.GONE

        when (activeTab) {
            TAB_BOOKMARKS -> {
                leftBtn.text = ""; leftBtn.setOnClickListener(null)
                right1.text = "···"
                right1.setOnClickListener { showBookmarkMore(right1) }
                right2.text = "编辑"
            }
            TAB_HISTORY -> {
                leftBtn.text = "标签页"
                leftBtn.setOnClickListener {
                    setResult(RESULT_FIRST_USER + 1, Intent().putExtra("action", "tabs"))
                    finish()
                }
                right1.text = "清空"
                right1.setOnClickListener { showHistoryClear() }
                right2.text = "编辑"
            }
            TAB_OFFLINE -> {
                leftBtn.text = ""; leftBtn.setOnClickListener(null)
                right1.text = ""
                right1.setOnClickListener(null)
                right2.text = "编辑"
            }
        }
    }

    // ---------------- List rendering ----------------

    private fun rebuildList() {
        listHolder.removeAllViews()
        when (activeTab) {
            TAB_BOOKMARKS -> renderBookmarks()
            TAB_HISTORY -> renderHistory()
            TAB_OFFLINE -> renderOffline()
        }
    }

    private fun renderBookmarks() {
        if (currentFolderId.isNotEmpty()) {
            listHolder.addView(folderHeader())
        } else {
            // Show folders first.
            val folderList = folders.all()
            folderList.forEach { folder ->
                listHolder.addView(buildRow(
                    key = "folder:${folder.id}",
                    title = "📁 ${folder.name}",
                    subtitle = "${bookmarks.inFolder(folder.id).size} 个书签",
                    onClick = { currentFolderId = folder.id; rebuildList(); rebuildBottomBar() }
                ))
            }
        }
        val items = bookmarks.inFolder(currentFolderId).filter { match(it.title, it.url) }
        if (items.isEmpty() && currentFolderId.isEmpty() && folders.all().isEmpty()) {
            listHolder.addView(emptyText("还没有书签"))
            return
        }
        items.forEach { entry ->
            listHolder.addView(buildRow(
                key = "bm:${entry.url}",
                title = entry.title.ifBlank { entry.url },
                subtitle = entry.url,
                onClick = { selectUrl(entry.url) }
            ))
        }
    }

    private fun renderHistory() {
        val items = history.all().filter { match(it.title, it.url) }
        if (items.isEmpty()) { listHolder.addView(emptyText("还没有历史记录")); return }
        var lastGroup = ""
        items.forEach { entry ->
            val group = groupForTime(entry.time)
            if (group != lastGroup) {
                listHolder.addView(groupHeader(group)); lastGroup = group
            }
            listHolder.addView(buildRow(
                key = "h:${entry.url}",
                title = entry.title.ifBlank { entry.url },
                subtitle = entry.url,
                onClick = { selectUrl(entry.url) }
            ))
        }
    }

    private fun renderOffline() {
        val items = offline.all().filter { match(it.title, it.url) }
        if (items.isEmpty()) { listHolder.addView(emptyText("还没有离线页面")); return }
        var lastGroup = ""
        items.forEach { page ->
            val group = groupForTime(page.time)
            if (group != lastGroup) {
                listHolder.addView(groupHeader(group)); lastGroup = group
            }
            listHolder.addView(buildRow(
                key = "off:${page.path}",
                title = page.title.ifBlank { page.url },
                subtitle = page.url,
                onClick = { selectUrl("file://${page.path}") }
            ))
        }
    }

    private fun folderHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            background = null
            setOnClickListener { currentFolderId = ""; rebuildList() }
        })
        val name = folders.all().firstOrNull { it.id == currentFolderId }?.name ?: "文件夹"
        row.addView(TextView(this).apply {
            text = name; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
            setPadding(dp(8), 0, 0, 0)
        })
        return row
    }

    private fun emptyText(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF888890.toInt())
        gravity = Gravity.CENTER
        setPadding(0, dp(60), 0, 0)
    }

    private fun groupHeader(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF8A8A92.toInt())
        textSize = 12f
        setPadding(dp(14), dp(14), dp(14), dp(6))
    }

    private fun buildRow(key: String, title: String, subtitle: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = resources.getDrawable(android.R.drawable.list_selector_background, theme)
        }
        if (editMode && !key.startsWith("folder:")) {
            val cb = CheckBox(this).apply {
                isChecked = selectedKeys.contains(key)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedKeys.add(key) else selectedKeys.remove(key)
                }
            }
            row.addView(cb)
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        text.addView(TextView(this).apply {
            this.text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; maxLines = 1
        })
        if (subtitle.isNotBlank()) {
            text.addView(TextView(this).apply {
                this.text = subtitle; setTextColor(0xFF8A8A92.toInt()); textSize = 12f; maxLines = 1
            })
        }
        row.addView(text)
        row.setOnClickListener {
            if (editMode && !key.startsWith("folder:")) {
                val cb = (row.getChildAt(0) as? CheckBox) ?: return@setOnClickListener
                cb.isChecked = !cb.isChecked
            } else {
                onClick()
            }
        }
        return row
    }

    private fun match(title: String, url: String): Boolean {
        if (searchQuery.isBlank()) return true
        return title.lowercase().contains(searchQuery) || url.lowercase().contains(searchQuery)
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
        val sdf = SimpleDateFormat("MM月dd日", Locale.CHINA)
        return sdf.format(Date(time))
    }

    // ---------------- Bottom-bar actions ----------------

    private fun toggleEditMode() {
        editMode = !editMode
        selectedKeys.clear()
        bottomBar.visibility = if (editMode) View.GONE else View.VISIBLE
        editBottomBar.visibility = if (editMode) View.VISIBLE else View.GONE
        rebuildList()
    }

    private fun selectAll() {
        val items: List<String> = when (activeTab) {
            TAB_BOOKMARKS -> bookmarks.inFolder(currentFolderId).filter { match(it.title, it.url) }.map { "bm:${it.url}" }
            TAB_HISTORY -> history.all().filter { match(it.title, it.url) }.map { "h:${it.url}" }
            TAB_OFFLINE -> offline.all().filter { match(it.title, it.url) }.map { "off:${it.path}" }
            else -> emptyList()
        }
        if (selectedKeys.containsAll(items)) selectedKeys.clear() else selectedKeys.addAll(items)
        rebuildList()
    }

    private fun deleteSelected() {
        if (selectedKeys.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage("删除选中的 ${selectedKeys.size} 项？")
            .setPositiveButton("删除") { _, _ ->
                when (activeTab) {
                    TAB_BOOKMARKS -> bookmarks.removeUrls(selectedKeys.map { it.removePrefix("bm:") }.toSet())
                    TAB_HISTORY -> history.removeUrls(selectedKeys.map { it.removePrefix("h:") }.toSet())
                    TAB_OFFLINE -> {
                        val paths = selectedKeys.map { it.removePrefix("off:") }.toSet()
                        offline.removePaths(paths)
                        paths.forEach { try { File(it).delete() } catch (e: Exception) {} }
                    }
                }
                selectedKeys.clear()
                rebuildList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openSelected() {
        // Open all selected bookmark URLs (one at a time via setResult is too limiting; pick the first).
        val first = selectedKeys.firstOrNull { it.startsWith("bm:") }?.removePrefix("bm:")
        if (first != null) selectUrl(first)
    }

    private fun moveSelected() {
        if (activeTab != TAB_BOOKMARKS || selectedKeys.isEmpty()) return
        val folderList = folders.all()
        val labels = mutableListOf("根目录").apply { folderList.forEach { add(it.name) } }
        val ids = mutableListOf("").apply { folderList.forEach { add(it.id) } }
        AlertDialog.Builder(this)
            .setTitle("移动到")
            .setItems(labels.toTypedArray()) { _, which ->
                val urls = selectedKeys.filter { it.startsWith("bm:") }.map { it.removePrefix("bm:") }.toSet()
                bookmarks.moveToFolder(urls, ids[which])
                selectedKeys.clear()
                rebuildList()
            }
            .show()
    }

    private fun selectUrl(url: String) {
        setResult(RESULT_OK, Intent().putExtra(RESULT_URL, url))
        finish()
    }

    // ---------------- Bookmark "more" menu ----------------

    private fun showBookmarkMore(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("添加书签")
        menu.menu.add("新建文件夹")
        menu.menu.add("排序方式")
        menu.menu.add("导入书签")
        menu.menu.add("备份书签")
        menu.setOnMenuItemClickListener { item ->
            when (item.title) {
                "添加书签" -> {
                    BookmarkDialog.show(this, bookmarks, folders,
                        defaultTitle = "", defaultUrl = "") { rebuildList() }
                    true
                }
                "新建文件夹" -> {
                    val edit = EditText(this).apply { hint = "文件夹名称" }
                    AlertDialog.Builder(this).setTitle("新建文件夹").setView(edit)
                        .setPositiveButton("创建") { _, _ ->
                            val name = edit.text.toString().trim()
                            if (name.isNotEmpty()) { folders.add(name); rebuildList() }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
                "排序方式" -> {
                    Toast.makeText(this, "排序：按时间倒序（默认）", Toast.LENGTH_SHORT).show(); true
                }
                "导入书签" -> {
                    Toast.makeText(this, "暂未实现：导入 HTML/JSON 书签", Toast.LENGTH_SHORT).show(); true
                }
                "备份书签" -> {
                    val dir = getExternalFilesDir(null) ?: filesDir
                    val backup = File(dir, "bookmarks_backup_${System.currentTimeMillis()}.json")
                    backup.writeText(File(filesDir, "bookmarks.json").let { if (it.exists()) it.readText() else "[]" })
                    Toast.makeText(this, "已备份到 ${backup.absolutePath}", Toast.LENGTH_LONG).show()
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun showHistoryClear() {
        val labels = arrayOf("今天", "今天和昨天", "过去 7 天", "所有时间")
        AlertDialog.Builder(this)
            .setTitle("清空历史")
            .setItems(labels) { _, which ->
                val ms = when (which) {
                    0 -> 24L * 3600_000
                    1 -> 48L * 3600_000
                    2 -> 7L * 24 * 3600_000
                    else -> Long.MAX_VALUE
                }
                history.clear(ms)
                rebuildList()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
