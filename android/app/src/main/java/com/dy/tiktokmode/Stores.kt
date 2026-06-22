package com.dy.tiktokmode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SiteEntry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

/** Tiny JSON-file backed list store. Avoids a DB dependency for simple lists. */
abstract class JsonListStore(context: Context, fileName: String) {
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

/**
 * A bookmark / folder node. `folder == true` means [url] is unused and the node
 * groups children whose `parent` equals this node's id. Root nodes have parent = "".
 */
data class BookmarkEntry(
    val id: String,
    val title: String,
    val url: String,
    val parent: String,
    val folder: Boolean
)

class BookmarkStore(context: Context) : JsonListStore(context, "bookmarks.json") {

    fun add(title: String, url: String, parent: String = "") {
        if (url.isBlank()) return
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optBoolean("folder") && o.optString("url") == url) return
        }
        arr.put(
            JSONObject()
                .put("id", "b_" + System.nanoTime())
                .put("title", title)
                .put("url", url)
                .put("parent", parent)
                .put("folder", false)
        )
        writeArray(arr)
    }

    fun addFolder(name: String, parent: String = ""): String {
        val id = "f_" + System.nanoTime()
        val arr = readArray()
        arr.put(
            JSONObject()
                .put("id", id)
                .put("title", name)
                .put("url", "")
                .put("parent", parent)
                .put("folder", true)
        )
        writeArray(arr)
        return id
    }

    fun rename(id: String, newTitle: String) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                o.put("title", newTitle); writeArray(arr); return
            }
        }
    }

    fun move(id: String, newParent: String) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) { o.put("parent", newParent); writeArray(arr); return }
        }
    }

    fun removeById(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        writeArray(out)
    }

    /** Legacy remove-by-url for the omnibox-star toggle. */
    fun remove(url: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("folder") || o.optString("url") != url) out.put(o)
        }
        writeArray(out)
    }

    fun isBookmarked(url: String): Boolean {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optBoolean("folder") && o.optString("url") == url) return true
        }
        return false
    }

    /** All nodes (folders + bookmarks). For one folder's contents use [children]. */
    fun allEntries(): List<BookmarkEntry> {
        val arr = readArray()
        val list = ArrayList<BookmarkEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                BookmarkEntry(
                    id = o.optString("id", "b_$i"),
                    title = o.optString("title"),
                    url = o.optString("url"),
                    parent = o.optString("parent"),
                    folder = o.optBoolean("folder")
                )
            )
        }
        return list
    }

    fun children(parent: String): List<BookmarkEntry> =
        allEntries().filter { it.parent == parent }

    fun folders(): List<BookmarkEntry> = allEntries().filter { it.folder }

    /** Legacy flat list — sites only, ignores folders. Used by home-page chips. */
    fun all(): List<SiteEntry> =
        allEntries().filter { !it.folder }.map { SiteEntry(it.title, it.url) }

    fun clear() = writeArray(JSONArray())
}
