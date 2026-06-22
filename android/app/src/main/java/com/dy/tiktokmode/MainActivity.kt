package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var homeWebView: android.webkit.WebView
    private lateinit var addressBar: EditText
    private lateinit var videoPager: ViewPager2
    private lateinit var browseLayer: View
    private lateinit var tiktokModeBtn: View
    private lateinit var normalModeBtn: Button
    private lateinit var startupText: TextView

    private var pagerAdapter: VideoPagerAdapter? = null
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerAdapter?.onPageSelected(position)
        }
    }

    /** When true, the next successful home page load auto-enters TikTok mode. */
    private var autoEnterArmed = true

    private val defaultUrl = "https://missav.ai/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        homeWebView = findViewById(R.id.homeWebView)
        addressBar = findViewById(R.id.addressBar)
        videoPager = findViewById(R.id.videoPager)
        browseLayer = findViewById(R.id.browseLayer)
        tiktokModeBtn = findViewById(R.id.tiktokModeBtn)
        normalModeBtn = findViewById(R.id.normalModeBtn)
        startupText = findViewById(R.id.startupText)

        homeWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        homeWebView.webViewClient = HeaderStrippingWebViewClient { onHomePageLoaded() }
        homeWebView.addJavascriptInterface(JsBridge(), "Android")

        addressBar.setText(defaultUrl)
        // The home WebView loads in the background so we can scrape links from it
        // even though the user starts out looking at the TikTok feed.
        homeWebView.loadUrl(defaultUrl)

        findViewById<View>(R.id.goButton).setOnClickListener { navigateFromAddressBar() }
        addressBar.setOnEditorActionListener { _, _, event ->
            val isEnterDown = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (event == null || isEnterDown) {
                navigateFromAddressBar()
                true
            } else false
        }

        tiktokModeBtn.setOnClickListener { collectAndEnterTikTok() }
        normalModeBtn.setOnClickListener { switchToNormalMode() }

        videoPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        videoPager.offscreenPageLimit = 1
        videoPager.registerOnPageChangeCallback(pageChangeCallback)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // In normal mode with web history -> go back within the page.
                    browseLayer.visibility == View.VISIBLE && homeWebView.canGoBack() ->
                        homeWebView.goBack()
                    // In normal mode at the root -> return to the TikTok feed.
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

    /** Fires on every home WebView page finish; auto-enters TikTok mode once. */
    private fun onHomePageLoaded() {
        if (!autoEnterArmed) return
        val host = Uri.parse(homeWebView.url).host ?: ""
        if (SiteConfig.matches(host)) {
            autoEnterArmed = false
            homeWebView.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
        } else {
            // Unsupported default page: drop the user into normal browsing.
            autoEnterArmed = false
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

    /** Normal-mode floating button: scrape the current page and enter the feed. */
    private fun collectAndEnterTikTok() {
        val host = Uri.parse(homeWebView.url).host ?: ""
        if (!SiteConfig.matches(host)) {
            Toast.makeText(this, "当前网站暂不支持抖音模式", Toast.LENGTH_SHORT).show()
            return
        }
        startupText.text = "正在加载视频…"
        startupText.visibility = View.VISIBLE
        homeWebView.evaluateJavascript(SiteConfig.collectLinksJs(host), null)
    }

    private fun showVideoFeed(items: List<VideoItem>) {
        startupText.visibility = View.GONE
        if (items.isEmpty()) {
            Toast.makeText(this, "当前页面没有找到可用的视频链接！", Toast.LENGTH_SHORT).show()
            // Fall back to normal browsing so the user isn't stuck on a blank feed.
            switchToNormalMode()
            return
        }
        pagerAdapter = VideoPagerAdapter(items)
        videoPager.adapter = pagerAdapter
        switchToTikTokMode()
        videoPager.post { pagerAdapter?.onPageSelected(videoPager.currentItem) }
    }

    private fun switchToTikTokMode() {
        browseLayer.visibility = View.GONE
        tiktokModeBtn.visibility = View.GONE
        videoPager.visibility = View.VISIBLE
        normalModeBtn.visibility = View.VISIBLE
        pagerAdapter?.let { videoPager.post { it.onPageSelected(videoPager.currentItem) } }
    }

    private fun switchToNormalMode() {
        pagerAdapter?.pauseAll()
        startupText.visibility = View.GONE
        videoPager.visibility = View.GONE
        normalModeBtn.visibility = View.GONE
        browseLayer.visibility = View.VISIBLE
        tiktokModeBtn.visibility = View.VISIBLE
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
