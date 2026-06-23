package com.dy.tiktokmode

import android.net.Uri

/** Built-in search engines plus a user-defined custom template. */
data class SearchEngine(val key: String, val label: String, val template: String)

object SearchEngines {
    val BUILT_IN = listOf(
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd=%s")
    )

    fun byKey(key: String): SearchEngine? = BUILT_IN.firstOrNull { it.key == key }

    /**
     * Matches a bare host/host:port/host-with-path string, e.g. "baidu.com",
     * "192.168.1.1", "localhost:8080/x". Requires a letter-led TLD (or numeric
     * IPv4) so plain numbers/decimals like "3.14" fall through to search instead.
     */
    private val HOST_PATTERN = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|[\\w-]+(\\.[\\w-]+)*\\.[a-zA-Z]{2,})(:\\d+)?(/\\S*)?$"
    )

    /**
     * Turn raw omnibox text into a navigable URL: an explicit URL/host is loaded
     * directly, anything else becomes a search query on the active engine.
     */
    fun resolve(input: String, prefs: Prefs): String {
        val text = input.trim()
        if (text.isEmpty()) return Prefs.HOME_URL
        if (text.startsWith("about:") || text.startsWith("view-source:") ||
            text.startsWith("file:") || text.startsWith("data:")
        ) return text

        val looksLikeUrl = text.startsWith("http://") || text.startsWith("https://") ||
            (!text.contains(" ") && HOST_PATTERN.matches(text))
        if (looksLikeUrl) {
            return if (text.startsWith("http")) text else "https://$text"
        }

        val template = if (prefs.searchEngineKey == "custom") {
            prefs.customSearchUrl
        } else {
            byKey(prefs.searchEngineKey)?.template ?: BUILT_IN[0].template
        }
        return template.replace("%s", Uri.encode(text))
    }
}
