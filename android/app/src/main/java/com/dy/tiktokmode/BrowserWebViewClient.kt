package com.dy.tiktokmode

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The browser tab's request/lifecycle handler. Folds together: ad/tracker
 * blocking, media resource sniffing, X-Frame-Options/CSP stripping for
 * sub-frames, and injection of night-mode CSS, user CSS and a user script.
 */
class BrowserWebViewClient(
    private val prefs: Prefs,
    private val onStarted: (url: String) -> Unit,
    private val onFinished: (url: String, title: String?) -> Unit
) : WebViewClient() {

    var tab: BrowserTab? = null

    private val client = OkHttpClient.Builder().followRedirects(true).build()
    private val strippedHeaders = setOf("x-frame-options", "content-security-policy")

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        tab?.sniffer?.consider(url)

        if (prefs.adBlockEnabled && AdBlocker.shouldBlock(request.url.host)) {
            return HeaderStrippingWebViewClient.emptyResponse()
        }

        if (request.isForMainFrame || request.method != "GET") return null

        return try {
            val builder = Request.Builder().url(url)
            request.requestHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            if (prefs.doNotTrack) builder.header("DNT", "1")
            val response = client.newCall(builder.build()).execute()
            val body = response.body ?: return null
            val mediaType = body.contentType()
            val mime = mediaType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream"
            val charset = mediaType?.charset()?.name() ?: "utf-8"
            val filtered = LinkedHashMap<String, String>()
            response.headers.forEach { (name, value) ->
                if (!strippedHeaders.contains(name.lowercase())) filtered[name] = value
            }
            WebResourceResponse(mime, charset, response.code, response.message.ifEmpty { "OK" }, filtered, body.byteStream())
        } catch (e: Exception) {
            null
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectCss(view)
        onStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectCss(view)
        injectUserScript(view)
        onFinished(url, view.title)
    }

    private fun injectCss(view: WebView) {
        val sb = StringBuilder()
        if (prefs.nightMode) sb.append(NIGHT_CSS)
        if (prefs.customCss.isNotBlank()) sb.append(prefs.customCss)
        if (sb.isEmpty()) return
        val css = sb.toString().replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
        view.evaluateJavascript(
            """(function(){var id='__dy_css';var s=document.getElementById(id);
               if(!s){s=document.createElement('style');s.id=id;
               (document.head||document.documentElement).appendChild(s);}
               s.textContent=`$css`;})();""", null
        )
    }

    private fun injectUserScript(view: WebView) {
        val script = prefs.userScript
        if (script.isBlank()) return
        view.evaluateJavascript("(function(){try{$script}catch(e){console.log(e)}})();", null)
    }

    companion object {
        // Dependency-free dark mode: invert the page, then re-invert media so
        // photos/video keep their real colours.
        private const val NIGHT_CSS =
            "html{filter:invert(1) hue-rotate(180deg)!important;background:#0d0d0d!important;}" +
            "img,video,picture,canvas,iframe,svg,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)!important;}"
    }
}
