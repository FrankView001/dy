package com.dy.tiktokmode

import android.webkit.WebView

/** A single browser tab: its WebView plus per-tab state. */
class BrowserTab(val webView: WebView, val incognito: Boolean) {
    var title: String = "新标签页"
    var currentUrl: String = Prefs.HOME_URL
    val sniffer = ResourceSniffer()
    val id: Long = nextId++

    companion object {
        private var nextId = 1L
    }
}

/**
 * Owns the list of tabs and which one is active. The hosting activity supplies
 * a factory to build a configured WebView and handles attaching/detaching the
 * active WebView to the on-screen container.
 */
class TabManager(private val factory: (incognito: Boolean) -> WebView) {
    val tabs = mutableListOf<BrowserTab>()
    var currentIndex = -1
        private set

    val current: BrowserTab?
        get() = tabs.getOrNull(currentIndex)

    fun newTab(incognito: Boolean): BrowserTab {
        val tab = BrowserTab(factory(incognito), incognito)
        tabs.add(tab)
        currentIndex = tabs.size - 1
        return tab
    }

    fun select(index: Int): BrowserTab? {
        if (index in tabs.indices) {
            currentIndex = index
            return tabs[index]
        }
        return null
    }

    fun selectTab(tab: BrowserTab): BrowserTab? = select(tabs.indexOf(tab))

    /** Returns the tab that should become active after the close, or null if none. */
    fun close(tab: BrowserTab): BrowserTab? {
        val idx = tabs.indexOf(tab)
        if (idx == -1) return current
        try {
            tab.webView.stopLoading()
            tab.webView.destroy()
        } catch (e: Exception) { /* ignore */ }
        tabs.removeAt(idx)
        currentIndex = when {
            tabs.isEmpty() -> -1
            idx <= currentIndex -> (currentIndex - 1).coerceAtLeast(0)
            else -> currentIndex
        }
        return current
    }

    fun count(): Int = tabs.size
}
