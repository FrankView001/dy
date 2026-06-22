package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var history: HistoryStore
    private lateinit var bookmarks: BookmarkStore
    private lateinit var tabs: TabManager

    private lateinit var webContainer: FrameLayout
    private lateinit var omnibox: EditText
    private lateinit var webProgress: ProgressBar
    private lateinit var tabCountBtn: TextView
    private lateinit var browserRoot: View
    private lateinit var fullscreenContainer: FrameLayout

    // find bar
    private lateinit var findBar: View
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView

    // TikTok overlay
    private lateinit var videoPager: ViewPager2
    private lateinit var feedTopBar: View
    private lateinit var startupBox: View
    private lateinit var startupText: TextView
    private var pagerAdapter: VideoPagerAdapter? = null
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) { pagerAdapter?.onPageSelected(position) }
    }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data?.data
            filePathCallback?.onReceiveValue(if (data != null) arrayOf(data) else emptyArray())
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        history = HistoryStore(this)
        bookmarks = BookmarkStore(this)
        AdBlocker.init(applicationContext)
        WebView.setWebContentsDebuggingEnabled(true)

        webContainer = findViewById(R.id.webContainer)
        omnibox = findViewById(R.id.omnibox)
        webProgress = findViewById(R.id.webProgress)
        tabCountBtn = findViewById(R.id.tabCountBtn)
        browserRoot = findViewById(R.id.browserRoot)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        findBar = findViewById(R.id.findBar)
        findInput = findViewById(R.id.findInput)
        findCount = findViewById(R.id.findCount)
        videoPager = findViewById(R.id.videoPager)
        feedTopBar = findViewById(R.id.feedTopBar)
        startupBox = findViewById(R.id.startupBox)
        startupText = findViewById(R.id.startupText)

        tabs = TabManager { incognito -> createWebView(incognito) }

        setupOmnibox()
        setupBottomBar()
        setupFindBar()
        setupTikTok()

        findViewById<ImageButton>(R.id.refreshBtn).setOnClickListener { tabs.current?.webView?.reload() }
        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener { showMenu() }
        tabCountBtn.setOnClickListener { showTabSwitcher() }
        tabCountBtn.setOnLongClickListener {
            openInNewTab(Prefs.HOME_URL, incognito = false); omnibox.requestFocus(); true
        }

        // First tab.
        openInNewTab(prefs.homepageUrl, incognito = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
    }

    // ---------------- WebView factory ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(incognito: Boolean): WebView {
        val wv = WebView(this)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !incognito
            databaseEnabled = !incognito
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            applyUaAndImages(this)
        }
        if (incognito) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        }

        val client = BrowserWebViewClient(
            prefs,
            onStarted = { url -> onPageStarted(wv, url) },
            onFinished = { url, title -> onPageFinished(wv, url, title) }
        )
        wv.webViewClient = client
        wv.webChromeClient = BrowserChromeClient(
            progressCb = { p ->
                if (tabs.current?.webView === wv) {
                    webProgress.progress = p
                    webProgress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                }
            },
            titleCb = { t -> tabForWebView(wv)?.let { if (t != null) it.title = t } },
            showCustomViewCb = { view, cb -> enterFullscreen(view, cb) },
            hideCustomViewCb = { exitFullscreen() },
            fileChooserCb = { cb -> openFileChooser(cb) }
        )
        // Link the client to its tab once the tab exists (set right after newTab()).
        wv.tag = client

        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            startDownload(url, contentDisposition, mimeType)
        }
        wv.addJavascriptInterface(JsBridge(), "Android")
        wv.addJavascriptInterface(HomeBridge(), "DYHome")

        wv.setOnLongClickListener { showLongPressMenu(wv) }
        return wv
    }

    private fun applyUaAndImages(s: WebSettings) {
        s.userAgentString = when {
            prefs.customUa.isNotBlank() -> prefs.customUa
            prefs.desktopMode -> DESKTOP_UA
            else -> MOBILE_UA
        }
        s.loadsImagesAutomatically = !prefs.noImageMode
        s.blockNetworkImage = prefs.noImageMode
    }

    private fun tabForWebView(wv: WebView): BrowserTab? = tabs.tabs.firstOrNull { it.webView === wv }

    // ---------------- Navigation ----------------

    private fun openInNewTab(url: String, incognito: Boolean) {
        val tab = tabs.newTab(incognito)
        (tab.webView.tag as? BrowserWebViewClient)?.tab = tab
        attachCurrentTab()
        loadUrl(tab, url)
        updateTabCount()
    }

    private fun attachCurrentTab() {
        val tab = tabs.current ?: return
        webContainer.removeAllViews()
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        webContainer.addView(tab.webView)
        syncOmnibox(tab.currentUrl)
        applyUaAndImages(tab.webView.settings)
    }

    private fun loadUrl(tab: BrowserTab, raw: String) {
        val url = if (raw.isBlank()) Prefs.HOME_URL else raw
        tab.currentUrl = url
        if (url == Prefs.HOME_URL) {
            renderHome(tab.webView)
            syncOmnibox(url)
        } else {
            tab.webView.loadUrl(url)
        }
    }

    private fun navigateFromOmnibox() {
        val tab = tabs.current ?: return
        val resolved = SearchEngines.resolve(omnibox.text.toString(), prefs)
        loadUrl(tab, resolved)
        omnibox.clearFocus()
    }

    private fun onPageStarted(wv: WebView, url: String) {
        if (tabs.current?.webView === wv) syncOmnibox(url)
        tabForWebView(wv)?.currentUrl = url
    }

    private fun onPageFinished(wv: WebView, url: String, title: String?) {
        val tab = tabForWebView(wv) ?: return
        tab.currentUrl = url
        if (title != null) tab.title = title
        if (tabs.current?.webView === wv) syncOmnibox(url)
        if (!tab.incognito) history.add(title ?: url, url)
    }

    private fun syncOmnibox(url: String) {
        if (omnibox.hasFocus()) return
        omnibox.setText(if (url == Prefs.HOME_URL) "" else url)
    }

    // ---------------- Home page ----------------

    private fun renderHome(wv: WebView) {
        val quick = StringBuilder()
        bookmarks.all().take(12).forEach {
            val safe = Html.escapeHtml(it.title.ifBlank { it.url })
            quick.append("<a class='ql' onclick=\"DYHome.openUrl('${it.url.replace("'", "%27")}')\">$safe</a>")
        }
        val title = Html.escapeHtml(prefs.homepageTitle)
        val bg = if (prefs.homepageBackground.isNotBlank())
            "background-image:url('${prefs.homepageBackground}');background-size:cover;background-position:center;"
        else
            "background:linear-gradient(160deg,#15151a,#0d0d10);"
        val html = """
            <!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
            <style>
            *{box-sizing:border-box;-webkit-tap-highlight-color:transparent;}
            body{margin:0;height:100vh;$bg color:#fff;font-family:-apple-system,Roboto,sans-serif;
                 display:flex;flex-direction:column;align-items:center;justify-content:center;}
            .logo{font-size:34px;font-weight:800;letter-spacing:1px;margin-bottom:26px;
                  background:linear-gradient(90deg,#FE2C55,#ff7aa0);-webkit-background-clip:text;-webkit-text-fill-color:transparent;}
            .box{display:flex;width:84%;max-width:520px;background:#2a2a2e;border-radius:24px;padding:4px;}
            input{flex:1;border:0;background:transparent;color:#fff;font-size:16px;padding:14px 18px;outline:none;}
            button{border:0;background:#FE2C55;color:#fff;border-radius:20px;padding:0 20px;font-size:15px;}
            .links{display:flex;flex-wrap:wrap;justify-content:center;gap:10px;margin-top:30px;width:84%;max-width:520px;}
            .ql{background:rgba(255,255,255,.08);color:#eee;padding:9px 14px;border-radius:14px;font-size:13px;
                text-decoration:none;max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
            </style></head><body>
            <div class='logo'>$title</div>
            <div class='box'><input id='q' placeholder='搜索或输入网址' autocomplete='off'
                 onkeydown="if(event.key==='Enter'){DYHome.search(this.value);}">
            <button onclick="DYHome.search(document.getElementById('q').value)">搜索</button></div>
            <div class='links'>$quick</div>
            </body></html>
        """.trimIndent()
        wv.loadDataWithBaseURL(Prefs.HOME_URL, html, "text/html", "utf-8", Prefs.HOME_URL)
    }

    private inner class HomeBridge {
        @JavascriptInterface
        fun search(text: String) {
            runOnUiThread {
                val tab = tabs.current ?: return@runOnUiThread
                loadUrl(tab, SearchEngines.resolve(text, prefs))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread { tabs.current?.let { loadUrl(it, url) } }
        }
    }

    // ---------------- Omnibox / bars ----------------

    private fun setupOmnibox() {
        omnibox.setOnEditorActionListener { _, _, event ->
            val enterDown = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (event == null || enterDown) { navigateFromOmnibox(); true } else false
        }
        omnibox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) syncOmnibox(tabs.current?.currentUrl ?: Prefs.HOME_URL)
        }
    }

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            tabs.current?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        findViewById<ImageButton>(R.id.forwardBtn).setOnClickListener {
            tabs.current?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        findViewById<ImageButton>(R.id.homeBtn).setOnClickListener {
            tabs.current?.let { loadUrl(it, Prefs.HOME_URL) }
        }
    }

    private fun updateTabCount() { tabCountBtn.text = tabs.count().toString() }

    // ---------------- Find in page ----------------

    private fun setupFindBar() {
        findInput.setOnEditorActionListener { _, _, _ ->
            tabs.current?.webView?.findAllAsync(findInput.text.toString()); true
        }
        findViewById<ImageButton>(R.id.findNext).setOnClickListener { tabs.current?.webView?.findNext(true) }
        findViewById<ImageButton>(R.id.findPrev).setOnClickListener { tabs.current?.webView?.findNext(false) }
        findViewById<ImageButton>(R.id.findClose).setOnClickListener {
            tabs.current?.webView?.clearMatches(); findBar.visibility = View.GONE
        }
        tabs.current?.webView?.setFindListener { active, count, _ ->
            findCount.text = if (count > 0) "${active + 1}/$count" else "0/0"
        }
    }

    private fun openFindBar() {
        findBar.visibility = View.VISIBLE
        findInput.requestFocus()
        tabs.current?.webView?.setFindListener { active, count, _ ->
            findCount.text = if (count > 0) "${active + 1}/$count" else "0/0"
        }
    }

    // ---------------- Menu ----------------

    /** A single grid tile spec. */
    private data class Tile(val icon: Int, val label: String, val active: Boolean, val onClick: () -> Unit)

    private fun showMenu() {
        val sheet = BottomSheetDialog(this)
        val scroll = androidx.core.widget.NestedScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet)
            setPadding(dp(6), dp(10), dp(6), dp(20))
        }

        // Drag handle.
        box.addView(View(this).apply {
            setBackgroundResource(R.drawable.drag_handle)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8)
            }
        })

        val url = tabs.current?.currentUrl ?: ""
        val isBookmarked = bookmarks.isBookmarked(url)

        fun act(block: () -> Unit): () -> Unit = { sheet.dismiss(); block() }

        box.addView(sectionTitle("常用"))
        box.addView(tileGrid(listOf(
            Tile(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                if (isBookmarked) "已收藏" else "添加书签", isBookmarked, act {
                    if (isBookmarked) bookmarks.remove(url)
                    else bookmarks.add(tabs.current?.title ?: url, url)
                    Toast.makeText(this, if (isBookmarked) "已移除书签" else "已添加书签", Toast.LENGTH_SHORT).show()
                }),
            Tile(R.drawable.ic_bookmark, "书签", false, act { showBookmarks() }),
            Tile(R.drawable.ic_history, "历史", false, act { showHistory() }),
            Tile(R.drawable.ic_search, "页内查找", false, act { openFindBar() }),
            Tile(R.drawable.ic_music_note, "抖音模式", false, act { enterTikTok() }),
            Tile(R.drawable.ic_incognito, "隐身标签", false, act { openInNewTab(Prefs.HOME_URL, true); omnibox.requestFocus() }),
            Tile(R.drawable.ic_link, "复制链接", false, act { copyUrl() }),
            Tile(R.drawable.ic_share, "其他应用", false, act { openExternally() })
        )))

        box.addView(sectionTitle("开关"))
        box.addView(tileGrid(listOf(
            Tile(R.drawable.ic_shield, "广告拦截", prefs.adBlockEnabled, act {
                prefs.adBlockEnabled = !prefs.adBlockEnabled; reloadCurrent()
            }),
            Tile(R.drawable.ic_night, "夜间模式", prefs.nightMode, act {
                prefs.nightMode = !prefs.nightMode; reloadCurrent()
            }),
            Tile(R.drawable.ic_desktop, "电脑模式", prefs.desktopMode, act {
                prefs.desktopMode = !prefs.desktopMode
                tabs.current?.let { applyUaAndImages(it.webView.settings); it.webView.reload() }
            }),
            Tile(R.drawable.ic_image, "无图模式", prefs.noImageMode, act {
                prefs.noImageMode = !prefs.noImageMode
                tabs.current?.let { applyUaAndImages(it.webView.settings); it.webView.reload() }
            })
        )))

        box.addView(sectionTitle("工具"))
        box.addView(tileGrid(listOf(
            Tile(R.drawable.ic_stream, "资源嗅探", false, act { showSniffer() }),
            Tile(R.drawable.ic_camera, "整页截图", false, act { captureScreenshot() }),
            Tile(R.drawable.ic_download, "离线保存", false, act { saveOffline() }),
            Tile(R.drawable.ic_code, "查看源码", false, act { viewSource() }),
            Tile(R.drawable.ic_delete, "清除数据", false, act { confirmClearData() }),
            Tile(R.drawable.ic_settings, "设置", false, act { startActivity(Intent(this, SettingsActivity::class.java)) })
        )))

        scroll.addView(box)
        sheet.setContentView(scroll)
        sheet.show()
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF8A8A92.toInt())
        textSize = 12f
        setPadding(dp(14), dp(14), dp(14), dp(6))
    }

    /** Builds a 4-column grid of icon tiles. */
    private fun tileGrid(tiles: List<Tile>): android.widget.GridLayout {
        val cols = 4
        val grid = android.widget.GridLayout(this).apply {
            columnCount = cols
        }
        tiles.forEach { t ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setBackgroundResource(outValueSelectableBackground())
                setOnClickListener { t.onClick() }
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
            }
            val iconWrap = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                if (t.active) setBackgroundResource(R.drawable.bg_tile_active)
            }
            iconWrap.addView(ImageView(this).apply {
                setImageResource(t.icon)
                if (t.active) setColorFilter(0xFFFE2C55.toInt())
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER }
            })
            cell.addView(iconWrap)
            cell.addView(TextView(this).apply {
                text = t.label
                setTextColor(if (t.active) 0xFFFE2C55.toInt() else 0xFFE8E8EC.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            grid.addView(cell)
        }
        return grid
    }

    private fun outValueSelectableBackground(): Int {
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return outValue.resourceId
    }

    private fun reloadCurrent() { tabs.current?.webView?.reload() }

    // ---------------- Lists: bookmarks / history / tabs / sniffer ----------------

    private fun showBookmarks() = showSiteList("书签", bookmarks.all(), onClear = { bookmarks.clear() })

    private fun showHistory() = showSiteList("历史记录", history.all(), onClear = { history.clear() })

    private fun showSiteList(title: String, items: List<SiteEntry>, onClear: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(12), dp(8), dp(24)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            this.text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f; setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(Button(this).apply { text = "清空"; setOnClickListener { onClear(); sheet.dismiss() } })
        box.addView(header)
        if (items.isEmpty()) {
            box.addView(TextView(this).apply { text = "（空）"; setTextColor(0xFF888890.toInt()); setPadding(dp(14), dp(20), 0, 0) })
        }
        val scroll = androidx.core.widget.NestedScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.take(300).forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundResource(outValueSelectableBackground())
                setOnClickListener { sheet.dismiss(); tabs.current?.let { loadUrl(it, entry.url) } }
            }
            row.addView(TextView(this).apply {
                text = entry.title.ifBlank { entry.url }; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; maxLines = 1
            })
            row.addView(TextView(this).apply {
                text = entry.url; setTextColor(0xFF8A8A92.toInt()); textSize = 12f; maxLines = 1
            })
            list.addView(row)
        }
        scroll.addView(list)
        box.addView(scroll)
        sheet.setContentView(box)
        sheet.show()
    }

    private fun showTabSwitcher() {
        val sheet = BottomSheetDialog(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(12), dp(8), dp(24)) }
        box.addView(sectionTitle("标签页 (${tabs.count()})"))
        tabs.tabs.toList().forEach { tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(8), dp(12))
                setBackgroundResource(outValueSelectableBackground())
                setOnClickListener { sheet.dismiss(); tabs.selectTab(tab); attachCurrentTab(); updateTabCount() }
            }
            val txt = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            txt.addView(TextView(this).apply {
                text = (if (tab.incognito) "🥷 " else "") + tab.title; setTextColor(0xFFFFFFFF.toInt()); maxLines = 1
            })
            txt.addView(TextView(this).apply { text = tab.currentUrl; setTextColor(0xFF8A8A92.toInt()); textSize = 12f; maxLines = 1 })
            row.addView(txt)
            row.addView(ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                setBackgroundResource(outValueSelectableBackground())
                setOnClickListener {
                    val next = tabs.close(tab)
                    if (next == null) openInNewTab(Prefs.HOME_URL, false) else attachCurrentTab()
                    updateTabCount(); sheet.dismiss(); showTabSwitcher()
                }
            })
            box.addView(row)
        }
        val newBtn = Button(this).apply { text = "＋ 新标签页"; setOnClickListener { sheet.dismiss(); openInNewTab(Prefs.HOME_URL, false); omnibox.requestFocus() } }
        box.addView(newBtn)
        sheet.setContentView(box)
        sheet.show()
    }

    private fun showSniffer() {
        val res = tabs.current?.sniffer?.snapshot() ?: emptyList()
        val sheet = BottomSheetDialog(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(12), dp(8), dp(24)) }
        box.addView(sectionTitle("嗅探到的媒体资源 (${res.size})"))
        if (res.isEmpty()) {
            box.addView(TextView(this).apply { text = "未发现视频/音频资源，播放后再试"; setTextColor(0xFF888890.toInt()); setPadding(dp(14), dp(16), 0, 0) })
        }
        val scroll = androidx.core.widget.NestedScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        res.forEach { r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundResource(outValueSelectableBackground())
                setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("media", r.url))
                    Toast.makeText(this@MainActivity, "已复制链接", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(TextView(this).apply { text = "【${r.type}】"; setTextColor(0xFFFE2C55.toInt()); textSize = 12f })
            row.addView(TextView(this).apply { text = r.url; setTextColor(0xFFDDDDDD.toInt()); textSize = 12f; maxLines = 2 })
            list.addView(row)
        }
        scroll.addView(list); box.addView(scroll)
        sheet.setContentView(box); sheet.show()
    }

    // ---------------- Tools ----------------

    private fun copyUrl() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", tabs.current?.currentUrl ?: ""))
        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
    }

    private fun openExternally() {
        val url = tabs.current?.currentUrl ?: return
        if (url == Prefs.HOME_URL) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
    }

    private fun viewSource() {
        val wv = tabs.current?.webView ?: return
        wv.evaluateJavascript("document.documentElement.outerHTML") { raw ->
            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript
            val escaped = Html.escapeHtml(decoded)
            val page = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{background:#0d0d10;color:#cfcfd4;font:12px/1.5 monospace;padding:10px;white-space:pre-wrap;word-break:break-all;}</style>" +
                "</head><body>$escaped</body></html>"
            tabs.current?.let {
                it.currentUrl = "view-source"
                it.webView.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        }
    }

    private fun captureScreenshot() {
        val wv = tabs.current?.webView ?: return
        try {
            val scale = resources.displayMetrics.density
            val width = wv.width
            val height = (wv.contentHeight * scale).toInt().coerceAtLeast(wv.height)
            if (width <= 0 || height <= 0) { Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show(); return }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            wv.draw(canvas)
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val file = File(dir, "shot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            Toast.makeText(this, "已保存截图：${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "截图失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveOffline() {
        val wv = tabs.current?.webView ?: return
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val file = File(dir, "page_${System.currentTimeMillis()}.mht")
        wv.saveWebArchive(file.absolutePath, false) { path ->
            runOnUiThread {
                Toast.makeText(this, if (path != null) "已离线保存：$path" else "保存失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startDownload(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            tabs.current?.webView?.settings?.userAgentString?.let { request.addRequestHeader("User-Agent", it) }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "开始下载：$name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLongPressMenu(wv: WebView): Boolean {
        val result = wv.hitTestResult
        val type = result.type
        val extra = result.extra ?: return false
        if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            AlertDialog.Builder(this)
                .setItems(arrayOf("以图搜图", "看图模式", "下载图片", "复制图片地址")) { _, which ->
                    when (which) {
                        0 -> tabs.current?.let { loadUrl(it, "https://lens.google.com/uploadbyurl?url=" + Uri.encode(extra)) }
                        1 -> openImageViewer(extra)
                        2 -> startDownload(extra, null, "image/*")
                        3 -> {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("img", extra))
                        }
                    }
                }.show()
            return true
        }
        return false
    }

    private fun openImageViewer(imageUrl: String) {
        val page = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>html,body{margin:0;height:100%;background:#000;}img{width:100%;height:100%;object-fit:contain;}</style></head>" +
            "<body><img src='${imageUrl.replace("'", "%27")}'></body></html>"
        tabs.current?.webView?.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("清除数据")
            .setMessage("将清除 Cookie、缓存、网页存储与历史记录。")
            .setPositiveButton("清除") { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                tabs.current?.webView?.clearCache(true)
                history.clear()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openFileChooser(cb: ValueCallback<Array<Uri>>): Boolean {
        filePathCallback = cb
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            fileChooserLauncher.launch(Intent.createChooser(intent, "选择文件"))
            true
        } catch (e: Exception) { filePathCallback = null; false }
    }

    // ---------------- Fullscreen video ----------------

    private fun enterFullscreen(view: View, cb: WebChromeClient.CustomViewCallback) {
        if (customView != null) { cb.onCustomViewHidden(); return }
        customView = view
        customViewCallback = cb
        fullscreenContainer.addView(view)
        fullscreenContainer.visibility = View.VISIBLE
        browserRoot.visibility = View.GONE
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        browserRoot.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    // ---------------- TikTok mode ----------------

    private fun setupTikTok() {
        videoPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        videoPager.offscreenPageLimit = 1
        videoPager.registerOnPageChangeCallback(pageChangeCallback)
        findViewById<View>(R.id.normalModeBtn).setOnClickListener { exitTikTok() }
        findViewById<ImageButton>(R.id.feedRefreshBtn).setOnClickListener {
            val host = Uri.parse(tabs.current?.currentUrl).host ?: ""
            if (SiteConfig.matches(host)) {
                showStartup("正在刷新…")
                tabs.current?.webView?.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
            }
        }
    }

    private fun enterTikTok() {
        val host = Uri.parse(tabs.current?.currentUrl).host ?: ""
        if (!SiteConfig.matches(host)) {
            Toast.makeText(this, "当前网站暂不支持抖音模式（支持 missav / 白小唐）", Toast.LENGTH_SHORT).show()
            return
        }
        showStartup("正在加载视频…")
        videoPager.visibility = View.VISIBLE
        feedTopBar.visibility = View.VISIBLE
        browserRoot.visibility = View.GONE
        tabs.current?.webView?.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
    }

    private fun exitTikTok() {
        pagerAdapter?.pauseAll()
        videoPager.visibility = View.GONE
        feedTopBar.visibility = View.GONE
        startupBox.visibility = View.GONE
        browserRoot.visibility = View.VISIBLE
    }

    private fun showStartup(msg: String) { startupText.text = msg; startupBox.visibility = View.VISIBLE }

    private fun showVideoFeed(items: List<VideoItem>) {
        startupBox.visibility = View.GONE
        if (items.isEmpty()) {
            Toast.makeText(this, "未找到可播放的视频链接", Toast.LENGTH_SHORT).show()
            exitTikTok(); return
        }
        pagerAdapter = VideoPagerAdapter(items)
        videoPager.adapter = pagerAdapter
        videoPager.post { pagerAdapter?.onPageSelected(videoPager.currentItem) }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onItems(json: String) {
            val items = mutableListOf<VideoItem>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(VideoItem(o.getString("title"), o.getString("detailUrl")))
                }
            } catch (e: Exception) {}
            runOnUiThread { showVideoFeed(items) }
        }
    }

    // ---------------- Back / lifecycle ----------------

    private fun handleBack() {
        when {
            customView != null -> exitFullscreen()
            videoPager.visibility == View.VISIBLE -> exitTikTok()
            findBar.visibility == View.VISIBLE -> { tabs.current?.webView?.clearMatches(); findBar.visibility = View.GONE }
            tabs.current?.webView?.canGoBack() == true -> tabs.current?.webView?.goBack()
            tabs.count() > 1 -> { tabs.current?.let { tabs.close(it) }; attachCurrentTab(); updateTabCount() }
            else -> finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed UA / search engine; re-apply to active tab.
        tabs.current?.let { applyUaAndImages(it.webView.settings) }
    }

    override fun onDestroy() {
        tabs.tabs.forEach { try { it.webView.destroy() } catch (e: Exception) {} }
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
