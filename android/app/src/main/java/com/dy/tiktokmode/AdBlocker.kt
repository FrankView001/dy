package com.dy.tiktokmode

import android.content.Context
import java.io.BufferedReader

/**
 * Hostname-based ad/popunder blocker. Loads a suffix list from
 * assets/ad_hosts.txt once and answers shouldBlock() for each request host.
 * Suffix matching means "ads.exoclick.com" is blocked by the entry
 * "exoclick.com". Results are memoised per host to keep interception cheap.
 */
object AdBlocker {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var builtInEnabled: Boolean = true

    private val blockedSuffixes = HashSet<String>()
    private val extraSuffixes = HashSet<String>()
    private val decisionCache = HashMap<String, Boolean>()
    private var loaded = false

    /** Append additional blocked-hostname suffixes loaded from rule subscriptions. */
    @Synchronized
    fun addSuffixes(suffixes: Collection<String>) {
        if (suffixes.isEmpty()) return
        suffixes.forEach { extraSuffixes.add(it.trim().lowercase()) }
        decisionCache.clear()
    }

    @Synchronized
    fun clearExtraSuffixes() {
        extraSuffixes.clear()
        decisionCache.clear()
    }

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        try {
            context.assets.open("ad_hosts.txt").bufferedReader().use { reader: BufferedReader ->
                reader.forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        blockedSuffixes.add(line.lowercase())
                    }
                }
            }
        } catch (e: Exception) {
            // No asset / read failure: blocker simply stays empty (no-op).
        }
        loaded = true
    }

    fun shouldBlock(host: String?): Boolean {
        if (!enabled || host.isNullOrEmpty()) return false
        val h = host.lowercase()
        decisionCache[h]?.let { return it }

        val pool = HashSet<String>()
        if (builtInEnabled) pool.addAll(blockedSuffixes)
        pool.addAll(extraSuffixes)
        if (pool.isEmpty()) { decisionCache[h] = false; return false }

        var blocked = pool.contains(h)
        if (!blocked) {
            var idx = h.indexOf('.')
            while (idx != -1 && idx + 1 < h.length) {
                if (pool.contains(h.substring(idx + 1))) { blocked = true; break }
                idx = h.indexOf('.', idx + 1)
            }
        }
        decisionCache[h] = blocked
        return blocked
    }
}
