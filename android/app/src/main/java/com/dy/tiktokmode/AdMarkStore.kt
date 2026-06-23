package com.dy.tiktokmode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists user-marked ad CSS selectors per host. Mirrors Via's "标记广告":
 * the user long-presses → picks an element → we save a selector keyed by
 * its hostname, and re-inject `selector{display:none!important}` on every
 * navigation to that host.
 */
object AdMarkStore {

    private lateinit var file: File
    // host → set of CSS selectors
    private val cache: HashMap<String, LinkedHashSet<String>> = HashMap()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        file = File(context.filesDir, "ad_marks.json")
        try {
            if (file.exists()) {
                val obj = JSONObject(file.readText())
                obj.keys().forEach { host ->
                    val arr = obj.optJSONArray(host) ?: return@forEach
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    cache[host] = set
                }
            }
        } catch (_: Exception) {}
        loaded = true
    }

    /** All marked hosts with their selectors, for a global overview. */
    fun allMarks(): Map<String, List<String>> = cache.mapValues { it.value.toList() }

    fun selectorsFor(host: String?): List<String> {
        if (host.isNullOrBlank()) return emptyList()
        return cache[host]?.toList() ?: emptyList()
    }

    fun add(host: String?, selector: String) {
        if (host.isNullOrBlank() || selector.isBlank()) return
        val set = cache.getOrPut(host) { LinkedHashSet() }
        if (set.add(selector)) persist()
    }

    fun clear(host: String?) {
        if (host.isNullOrBlank()) return
        if (cache.remove(host) != null) persist()
    }

    fun clearAll() {
        cache.clear(); persist()
    }

    private fun persist() {
        try {
            val obj = JSONObject()
            cache.forEach { (host, set) ->
                val arr = JSONArray()
                set.forEach { arr.put(it) }
                obj.put(host, arr)
            }
            file.writeText(obj.toString())
        } catch (_: Exception) {}
    }
}
