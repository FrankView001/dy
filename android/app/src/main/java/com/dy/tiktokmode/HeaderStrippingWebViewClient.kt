package com.dy.tiktokmode

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Combines two request-level behaviours that a plain WebView can't do on its own:
 *
 *  1. Ad/popunder blocking - requests to known ad hosts return an empty 200 so
 *     they never load (see [AdBlocker]).
 *  2. Frame-restriction stripping - X-Frame-Options / CSP frame-ancestors only
 *     block a resource from rendering inside an iframe. The detail pages we open
 *     are top-level navigations so they're unaffected, but a site's own player
 *     may embed a CDN iframe that the CDN refuses to be framed by a third party.
 *     For sub-frame GETs we re-fetch via OkHttp and drop those headers.
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
        val host = request.url.host
        if (AdBlocker.shouldBlock(host)) {
            return emptyResponse()
        }

        // Only proxy GET sub-frame requests to strip framing headers. The main
        // document, POSTs and media streams take the native fast path.
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

    companion object {
        fun emptyResponse(): WebResourceResponse =
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
