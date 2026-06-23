package com.dy.tiktokmode

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Download & merge ad-blocking subscription rules (hosts file or AdBlock Plus). */
object AdSubscription {

    /**
     * Synchronously download and parse each subscription URL, then push the
     * combined host set into [AdBlocker]. Returns the number of unique hosts
     * pulled in (0 if every URL failed). Call from a background thread.
     */
    fun updateBlocking(context: Context, urls: List<String>): Int {
        val hosts = LinkedHashSet<String>()
        urls.forEach { rawUrl ->
            val url = rawUrl.trim()
            if (url.isBlank() || url.startsWith("#")) return@forEach
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", MainActivity.MOBILE_UA)
                conn.inputStream.bufferedReader().use { r ->
                    r.lineSequence().forEach { raw ->
                        parseLine(raw)?.let { hosts.add(it) }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Skip this URL; one bad URL shouldn't fail the whole update.
            }
        }
        if (hosts.isNotEmpty()) AdBlocker.replaceSubscriptionRules(context, hosts)
        return hosts.size
    }

    private fun parseLine(raw: String): String? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return null
        return when {
            line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") ->
                line.substringAfter(' ').substringBefore(' ').trim().lowercase()
            line.startsWith("||") -> {
                val h = line.substring(2).substringBefore('^').substringBefore('/').trim()
                if (h.contains('*')) null else h.lowercase()
            }
            !line.contains(' ') && line.contains('.') -> line.lowercase()
            else -> null
        }
    }
}
