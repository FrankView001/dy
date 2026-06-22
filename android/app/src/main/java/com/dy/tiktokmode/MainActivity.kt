package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.widget.EditText
import android.widget.ImageButton
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
    private lateinit var closeTiktokBtn: ImageButton

    private var pagerAdapter: VideoPagerAdapter? = null
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerAdapter?.onPageSelected(position)
        }
    }

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
        closeTiktokBtn = findViewById(R.id.closeTiktokBtn)

        homeWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        homeWebView.webViewClient = HeaderStrippingWebViewClient()
        homeWebView.addJavascriptInterface(JsBridge(), "Android")

        addressBar.setText(defaultUrl)
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

        tiktokModeBtn.setOnClickListener { enterTikTokMode() }
        closeTiktokBtn.setOnClickListener { exitTikTokMode() }

        videoPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        videoPager.offscreenPageLimit = 1
        videoPager.registerOnPageChangeCallback(pageChangeCallback)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (videoPager.visibility == View.VISIBLE) {
                    exitTikTokMode()
                } else if (homeWebView.canGoBack()) {
                    homeWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun navigateFromAddressBar() {
        var url = addressBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        homeWebView.loadUrl(url)
    }

    private fun enterTikTokMode() {
        val hostname = Uri.parse(homeWebView.url).host ?: ""
        if (!SiteConfig.matches(hostname)) {
            Toast.makeText(this, "当前网站暂不支持抖音模式", Toast.LENGTH_SHORT).show()
            return
        }
        homeWebView.evaluateJavascript(SiteConfig.collectLinksJs(hostname), null)
    }

    private fun showVideoFeed(items: List<VideoItem>) {
        if (items.isEmpty()) {
            Toast.makeText(this, "当前页面没有找到可用的视频链接！", Toast.LENGTH_SHORT).show()
            return
        }
        pagerAdapter = VideoPagerAdapter(items)
        videoPager.adapter = pagerAdapter
        browseLayer.visibility = View.GONE
        tiktokModeBtn.visibility = View.GONE
        videoPager.visibility = View.VISIBLE
        closeTiktokBtn.visibility = View.VISIBLE
        videoPager.post { pagerAdapter?.onPageSelected(videoPager.currentItem) }
    }

    private fun exitTikTokMode() {
        pagerAdapter?.pauseAll()
        videoPager.visibility = View.GONE
        closeTiktokBtn.visibility = View.GONE
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
