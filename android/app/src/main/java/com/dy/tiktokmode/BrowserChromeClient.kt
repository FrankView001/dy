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
    private val progressCb: (Int) -> Unit,
    private val titleCb: (String?) -> Unit,
    private val showCustomViewCb: (View, WebChromeClient.CustomViewCallback) -> Unit,
    private val hideCustomViewCb: () -> Unit,
    private val fileChooserCb: (ValueCallback<Array<Uri>>) -> Boolean
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        progressCb(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        titleCb(title)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        showCustomViewCb(view, callback)
    }

    override fun onHideCustomView() {
        hideCustomViewCb()
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Deny device permission escalation by default (camera/mic).
        request.deny()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = fileChooserCb(filePathCallback)
}
