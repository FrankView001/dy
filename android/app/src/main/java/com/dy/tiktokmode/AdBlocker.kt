package com.dy.tiktokmode

import android.content.Context
import java.io.BufferedReader
import java.io.File

/**
 * Hostname-based ad/popunder blocker. Loads a suffix list from
 * assets/ad_hosts.txt once and answers shouldBlock() for each request host.
 * Suffix matching means "ads.exoclick.com" is blocked by the entry
 * "exoclick.com". Results are memoised per host to keep interception cheap.
 */
object AdBlocker {

    @Volatile
    var enabled: Boolean = true

    private val blockedSuffixes = HashSet<String>()
    private val decisionCache = HashMap<String, Boolean>()
    private var loaded = false

    /** Cached subscription rules persisted across launches. */
    private fun subFile(context: Context): File =
        File(context.filesDir, "ad_subscription_hosts.txt")

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        try {
            context.assets.open("ad_hosts.txt").bufferedReader().use { reader: BufferedReader ->
                reader.forEachLine { raw ->
                    addRule(raw)
                }
            }
        } catch (e: Exception) {
            // No asset / read failure: blocker simply stays empty (no-op).
        }
        // Load any previously cached subscription rules.
        try {
            val f = subFile(context)
            if (f.exists()) {
                f.bufferedReader().use { it.forEachLine { line -> addRule(line) } }
            }
        } catch (_: Exception) {}
        loaded = true
    }

    /** Replace cached subscription rule set with [hosts] and persist them. */
    @Synchronized
    fun replaceSubscriptionRules(context: Context, hosts: Collection<String>) {
        try {
            subFile(context).bufferedWriter().use { w ->
                hosts.forEach { h ->
                    val clean = h.trim().lowercase()
                    if (clean.isNotEmpty()) { w.write(clean); w.newLine() }
                }
            }
        } catch (_: Exception) {}
        hosts.forEach { addRule(it) }
        decisionCache.clear()
    }

    private fun addRule(raw: String) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return
        // Hosts-format: "0.0.0.0 ads.example.com" or "127.0.0.1 ads.example.com"
        val host = when {
            line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") ->
                line.substringAfter(' ').substringBefore(' ').trim()
            // ABP-style: "||example.com^"
            line.startsWith("||") ->
                line.substring(2).substringBefore('^').substringBefore('/').trim()
            // Bare hostname.
            !line.contains(' ') && line.contains('.') -> line
            else -> return
        }
        if (host.isNotEmpty() && !host.contains('*')) {
            blockedSuffixes.add(host.lowercase())
        }
    }

    fun ruleCount(): Int = blockedSuffixes.size

    fun shouldBlock(host: String?): Boolean {
        if (!enabled || host.isNullOrEmpty() || blockedSuffixes.isEmpty()) return false
        val h = host.lowercase()
        decisionCache[h]?.let { return it }

        var blocked = false
        if (blockedSuffixes.contains(h)) {
            blocked = true
        } else {
            // Walk parent domains: a.b.exoclick.com -> b.exoclick.com -> exoclick.com
            var idx = h.indexOf('.')
            while (idx != -1 && idx + 1 < h.length) {
                if (blockedSuffixes.contains(h.substring(idx + 1))) {
                    blocked = true
                    break
                }
                idx = h.indexOf('.', idx + 1)
            }
        }
        decisionCache[h] = blocked
        return blocked
    }
}
