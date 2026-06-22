package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView

class VideoPagerAdapter(private val items: List<VideoItem>) :
    RecyclerView.Adapter<VideoPagerAdapter.ViewHolder>() {

    private val liveHolders = mutableSetOf<ViewHolder>()
    private var currentPosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val webView: WebView = view.findViewById(R.id.itemWebView)
        val loadingText: View = view.findViewById(R.id.loadingText)
        val titleText: android.widget.TextView = view.findViewById(R.id.titleText)
        val tapLayer: View = view.findViewById(R.id.tapLayer)
        var isMuted = true
        var isPaused = false
        var boundUrl: String? = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_page, parent, false)
        val holder = ViewHolder(view)

        holder.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        holder.webView.webViewClient = HeaderStrippingWebViewClient { url ->
            holder.webView.evaluateJavascript(SiteConfig.STYLE_AND_AUTOPLAY_JS, null)
            holder.loadingText.post { holder.loadingText.visibility = View.GONE }
        }

        holder.tapLayer.setOnClickListener {
            if (holder.isMuted) {
                holder.isMuted = false
                holder.isPaused = false
                holder.webView.evaluateJavascript(SiteConfig.UNMUTE_PLAY_JS, null)
            } else {
                holder.isPaused = !holder.isPaused
                holder.webView.evaluateJavascript(
                    if (holder.isPaused) SiteConfig.PAUSE_JS else SiteConfig.PLAY_JS,
                    null
                )
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleText.text = "${item.title}\n(轻触屏幕开启声音/暂停)"
        holder.loadingText.visibility = View.VISIBLE
        holder.isMuted = true
        holder.isPaused = false
        if (holder.boundUrl != item.detailUrl) {
            holder.boundUrl = item.detailUrl
            holder.webView.loadUrl(item.detailUrl)
        }
        liveHolders.add(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        liveHolders.remove(holder)
    }

    override fun getItemCount(): Int = items.size

    /** Called by the host when the swiped-to page changes: mute-pause everything else, autoplay current. */
    fun onPageSelected(position: Int) {
        currentPosition = position
        liveHolders.forEach { holder ->
            val isCurrent = holder.bindingAdapterPosition == position
            if (isCurrent) {
                holder.webView.evaluateJavascript(SiteConfig.MUTE_PLAY_JS, null)
            } else {
                holder.webView.evaluateJavascript(SiteConfig.PAUSE_JS, null)
            }
        }
    }

    /** Called when leaving the swipe feed entirely: pause all to free resources/silence audio. */
    fun pauseAll() {
        liveHolders.forEach { it.webView.evaluateJavascript(SiteConfig.PAUSE_JS, null) }
    }
}
