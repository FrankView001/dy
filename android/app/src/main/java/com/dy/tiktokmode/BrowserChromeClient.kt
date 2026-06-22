package com.dy.tiktokmode

import android.net.Uri
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Handles progress, title updates and HTML5 fullscreen video (custom view), plus
 * file-upload chooser delegation back to the activity.
 */
class BrowserChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String?) -> Unit,
    private val onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    private val onHideCustomView: () -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>) -> Boolean
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        onTitle(title)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomView()
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Deny device permission escalation by default (camera/mic).
        request.deny()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = onFileChooser(filePathCallback)
}
