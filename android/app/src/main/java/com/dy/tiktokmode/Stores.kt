package com.dy.tiktokmode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SiteEntry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

/** Tiny JSON-file backed list store. Avoids a DB dependency for simple lists. */
private abstract class JsonListStore(context: Context, fileName: String) {
    protected val file = File(context.filesDir, fileName)

    protected fun readArray(): JSONArray =
        try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }

    protected fun writeArray(arr: JSONArray) {
        try {
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // best effort
        }
    }
}

class HistoryStore(context: Context) : JsonListStore(context, "history.json") {
    fun add(title: String, url: String) {
        if (url.isBlank() || url == Prefs.HOME_URL || url.startsWith("about:")) return
        val arr = readArray()
        // De-dupe consecutive same-url entries.
        if (arr.length() > 0) {
            val last = arr.getJSONObject(arr.length() - 1)
            if (last.optString("url") == url) return
        }
        arr.put(JSONObject().put("title", title).put("url", url).put("time", System.currentTimeMillis()))
        // Cap history size.
        while (arr.length() > 1000) arr.remove(0)
        writeArray(arr)
    }

    fun all(): List<SiteEntry> {
        val arr = readArray()
        val list = ArrayList<SiteEntry>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            list.add(SiteEntry(o.optString("title"), o.optString("url"), o.optLong("time")))
        }
        return list
    }

    fun clear() = writeArray(JSONArray())
}

class BookmarkStore(context: Context) : JsonListStore(context, "bookmarks.json") {
    fun add(title: String, url: String) {
        if (url.isBlank()) return
        val arr = readArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("url") == url) return // already bookmarked
        }
        arr.put(JSONObject().put("title", title).put("url", url))
        writeArray(arr)
    }

    fun remove(url: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("url") != url) out.put(o)
        }
        writeArray(out)
    }

    fun isBookmarked(url: String): Boolean {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("url") == url) return true
        }
        return false
    }

    fun all(): List<SiteEntry> {
        val arr = readArray()
        val list = ArrayList<SiteEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(SiteEntry(o.optString("title"), o.optString("url")))
        }
        return list
    }

    fun clear() = writeArray(JSONArray())
}
