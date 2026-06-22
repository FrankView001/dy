package com.dy.tiktokmode

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView

class VideoPagerAdapter(private val items: List<VideoItem>) :
    RecyclerView.Adapter<VideoPagerAdapter.ViewHolder>() {

    private val liveHolders = mutableSetOf<ViewHolder>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val webView: WebView = view.findViewById(R.id.itemWebView)
        val loadingSpinner: ProgressBar = view.findViewById(R.id.loadingSpinner)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val tapLayer: View = view.findViewById(R.id.tapLayer)
        val playIcon: ImageView = view.findViewById(R.id.playIcon)
        val muteBtn: ImageButton = view.findViewById(R.id.muteBtn)
        val reloadBtn: ImageButton = view.findViewById(R.id.reloadBtn)
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
            userAgentString = USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        holder.webView.webViewClient = HeaderStrippingWebViewClient {
            holder.webView.evaluateJavascript(SiteConfig.STYLE_AND_AUTOPLAY_JS, null)
            holder.loadingSpinner.post { holder.loadingSpinner.visibility = View.GONE }
        }

        holder.tapLayer.setOnClickListener {
            if (holder.isMuted) {
                holder.isMuted = false
                holder.isPaused = false
                holder.muteBtn.setImageResource(R.drawable.ic_volume_up)
                holder.playIcon.visibility = View.GONE
                holder.webView.evaluateJavascript(SiteConfig.UNMUTE_PLAY_JS, null)
            } else {
                holder.isPaused = !holder.isPaused
                if (holder.isPaused) {
                    holder.webView.evaluateJavascript(SiteConfig.PAUSE_JS, null)
                    holder.playIcon.visibility = View.VISIBLE
                } else {
                    holder.webView.evaluateJavascript(SiteConfig.PLAY_JS, null)
                    holder.playIcon.visibility = View.GONE
                }
            }
        }

        holder.muteBtn.setOnClickListener { holder.tapLayer.performClick() }

        holder.reloadBtn.setOnClickListener {
            holder.loadingSpinner.visibility = View.VISIBLE
            holder.webView.reload()
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleText.text = item.title
        holder.loadingSpinner.visibility = View.VISIBLE
        holder.playIcon.visibility = View.GONE
        holder.isMuted = true
        holder.isPaused = false
        holder.muteBtn.setImageResource(R.drawable.ic_volume_off)
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

    /** Mute-pause everything but the swiped-to page, which autoplays muted. */
    fun onPageSelected(position: Int) {
        liveHolders.forEach { holder ->
            if (holder.bindingAdapterPosition == position) {
                if (!holder.isPaused) {
                    holder.webView.evaluateJavascript(SiteConfig.MUTE_PLAY_JS, null)
                }
            } else {
                holder.webView.evaluateJavascript(SiteConfig.PAUSE_JS, null)
            }
        }
    }

    /** Pause everything when leaving the feed (silence audio / free decode). */
    fun pauseAll() {
        liveHolders.forEach { it.webView.evaluateJavascript(SiteConfig.PAUSE_JS, null) }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
