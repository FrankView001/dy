package com.dy.tiktokmode

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * X-Frame-Options/CSP frame-ancestors only block a resource from rendering
 * *inside an iframe*. Top-level navigations (each detail page we load) are
 * never affected by it. The header only matters for iframes a player embeds
 * internally (e.g. a video CDN's own player frame), since from that CDN's
 * point of view our app is a third-party embedder. We strip it there by
 * fetching the resource ourselves and re-emitting the response, since the
 * stock WebViewClient never exposes real headers if you just call super().
 */
class HeaderStrippingWebViewClient(
    private val onPageLoaded: ((String) -> Unit)? = null
) : WebViewClient() {

    private val client = OkHttpClient.Builder().followRedirects(true).build()
    private val strippedHeaders = setOf("x-frame-options", "content-security-policy")

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // Only proxy GET requests for sub-frames; let everything else (main
        // document, POSTs, media streams) go through the native fast path.
        if (request.isForMainFrame || request.method != "GET") return null

        return try {
            val builder = Request.Builder().url(request.url.toString())
            request.requestHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            val response = client.newCall(builder.build()).execute()
            val body = response.body ?: return null
            val mediaType = body.contentType()
            val mimeType = mediaType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream"
            val charset = mediaType?.charset()?.name() ?: "utf-8"

            val filtered = LinkedHashMap<String, String>()
            response.headers.forEach { (name, value) ->
                if (!strippedHeaders.contains(name.lowercase())) filtered[name] = value
            }

            WebResourceResponse(
                mimeType,
                charset,
                response.code,
                response.message.ifEmpty { "OK" },
                filtered,
                body.byteStream()
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageLoaded?.invoke(url)
    }
}
