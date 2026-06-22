package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var homeWebView: WebView
    private lateinit var addressBar: EditText
    private lateinit var videoPager: ViewPager2
    private lateinit var browseLayer: View
    private lateinit var feedTopBar: View
    private lateinit var startupBox: View
    private lateinit var startupText: TextView
    private lateinit var tiktokModeBtn: ExtendedFloatingActionButton
    private lateinit var normalModeBtn: TextView
    private lateinit var webProgress: ProgressBar

    private var pagerAdapter: VideoPagerAdapter? = null
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerAdapter?.onPageSelected(position)
        }
    }

    /** On the next home page load, scrape links and (re)build the feed. */
    private var collectOnLoad = true

    private val defaultUrl = "https://missav.ai/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AdBlocker.init(applicationContext)
        WebView.setWebContentsDebuggingEnabled(true)

        homeWebView = findViewById(R.id.homeWebView)
        addressBar = findViewById(R.id.addressBar)
        videoPager = findViewById(R.id.videoPager)
        browseLayer = findViewById(R.id.browseLayer)
        feedTopBar = findViewById(R.id.feedTopBar)
        startupBox = findViewById(R.id.startupBox)
        startupText = findViewById(R.id.startupText)
        tiktokModeBtn = findViewById(R.id.tiktokModeBtn)
        normalModeBtn = findViewById(R.id.normalModeBtn)
        webProgress = findViewById(R.id.webProgress)

        homeWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = VideoPagerAdapter.USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        homeWebView.webViewClient = HeaderStrippingWebViewClient { onHomePageLoaded() }
        homeWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                webProgress.progress = newProgress
                webProgress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        homeWebView.addJavascriptInterface(JsBridge(), "Android")

        addressBar.setText(defaultUrl)
        // The home WebView loads in the background so we can scrape links even
        // though the user starts out on the TikTok feed.
        homeWebView.loadUrl(defaultUrl)

        addressBar.setOnEditorActionListener { _, _, event ->
            val isEnterDown = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (event == null || isEnterDown) {
                navigateFromAddressBar()
                true
            } else false
        }

        findViewById<ImageButton>(R.id.webBackBtn).setOnClickListener {
            if (homeWebView.canGoBack()) homeWebView.goBack()
        }
        findViewById<ImageButton>(R.id.webRefreshBtn).setOnClickListener { homeWebView.reload() }
        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener { showMenu(it) }

        findViewById<ImageButton>(R.id.feedRefreshBtn).setOnClickListener { refreshFeed() }
        tiktokModeBtn.setOnClickListener { collectAndEnterTikTok() }
        normalModeBtn.setOnClickListener { switchToNormalMode() }

        videoPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        videoPager.offscreenPageLimit = 1
        videoPager.registerOnPageChangeCallback(pageChangeCallback)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    browseLayer.visibility == View.VISIBLE && homeWebView.canGoBack() ->
                        homeWebView.goBack()
                    browseLayer.visibility == View.VISIBLE ->
                        switchToTikTokMode()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun onHomePageLoaded() {
        if (!collectOnLoad) return
        collectOnLoad = false
        val host = Uri.parse(homeWebView.url).host ?: ""
        if (SiteConfig.matches(host)) {
            homeWebView.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
        } else {
            // Unsupported page on first load: drop into normal browsing.
            switchToNormalMode()
        }
    }

    private fun navigateFromAddressBar() {
        var url = addressBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        homeWebView.loadUrl(url)
    }

    /** Normal-mode FAB: scrape the page the user is currently viewing. */
    private fun collectAndEnterTikTok() {
        val host = Uri.parse(homeWebView.url).host ?: ""
        if (!SiteConfig.matches(host)) {
            Toast.makeText(this, "当前网站暂不支持抖音模式", Toast.LENGTH_SHORT).show()
            return
        }
        showStartup("正在加载视频…")
        switchToTikTokMode()
        homeWebView.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
    }

    /** Feed refresh: reload the source page and rebuild the feed from it. */
    private fun refreshFeed() {
        collectOnLoad = true
        showStartup("正在刷新…")
        homeWebView.reload()
    }

    private fun showStartup(msg: String) {
        startupText.text = msg
        startupBox.visibility = View.VISIBLE
    }

    private fun showVideoFeed(items: List<VideoItem>) {
        startupBox.visibility = View.GONE
        if (items.isEmpty()) {
            Toast.makeText(this, "未找到可播放的视频链接", Toast.LENGTH_SHORT).show()
            switchToNormalMode()
            return
        }
        pagerAdapter = VideoPagerAdapter(items)
        videoPager.adapter = pagerAdapter
        videoPager.post { pagerAdapter?.onPageSelected(videoPager.currentItem) }
    }

    private fun switchToTikTokMode() {
        browseLayer.visibility = View.GONE
        tiktokModeBtn.visibility = View.GONE
        videoPager.visibility = View.VISIBLE
        feedTopBar.visibility = View.VISIBLE
        pagerAdapter?.let { videoPager.post { it.onPageSelected(videoPager.currentItem) } }
    }

    private fun switchToNormalMode() {
        pagerAdapter?.pauseAll()
        startupBox.visibility = View.GONE
        videoPager.visibility = View.GONE
        feedTopBar.visibility = View.GONE
        browseLayer.visibility = View.VISIBLE
        tiktokModeBtn.visibility = View.VISIBLE
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val mAd = popup.menu.add(if (AdBlocker.enabled) "广告拦截：开" else "广告拦截：关")
        val mTik = popup.menu.add("切换到抖音模式")
        val mCopy = popup.menu.add("复制链接")
        val mForward = popup.menu.add("前进")
        popup.setOnMenuItemClickListener { item ->
            when (item) {
                mAd -> {
                    AdBlocker.enabled = !AdBlocker.enabled
                    Toast.makeText(this, if (AdBlocker.enabled) "广告拦截已开启" else "广告拦截已关闭", Toast.LENGTH_SHORT).show()
                    homeWebView.reload()
                }
                mTik -> collectAndEnterTikTok()
                mCopy -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("url", homeWebView.url ?: ""))
                    Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                }
                mForward -> if (homeWebView.canGoForward()) homeWebView.goForward()
            }
            true
        }
        popup.show()
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onItems(json: String) {
            val items = mutableListOf<VideoItem>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    items.add(VideoItem(obj.getString("title"), obj.getString("detailUrl")))
                }
            } catch (e: Exception) {
                // malformed payload from the page; treat as empty result
            }
            runOnUiThread { showVideoFeed(items) }
        }
    }
}
