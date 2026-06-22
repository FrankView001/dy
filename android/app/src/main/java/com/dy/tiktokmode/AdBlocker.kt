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

    private val blockedSuffixes = HashSet<String>()
    private val decisionCache = HashMap<String, Boolean>()
    private var loaded = false

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
