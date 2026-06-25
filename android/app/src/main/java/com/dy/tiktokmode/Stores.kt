package com.dy.tiktokmode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SiteEntry(
    val title: String,
    val url: String,
    val time: Long = System.currentTimeMillis(),
    val folderId: String = "",
    val pinnedHome: Boolean = false
)

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
        if (arr.length() > 0) {
            val last = arr.getJSONObject(arr.length() - 1)
            if (last.optString("url") == url) return
        }
        arr.put(JSONObject().put("title", title).put("url", url).put("time", System.currentTimeMillis()))
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

    /** Time-window in millis from now; pass Long.MAX_VALUE for "all". */
    fun clear(windowMs: Long = Long.MAX_VALUE) {
        if (windowMs == Long.MAX_VALUE) { writeArray(JSONArray()); return }
        val cutoff = System.currentTimeMillis() - windowMs
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("time") < cutoff) out.put(o)
        }
        writeArray(out)
    }

    fun removeUrls(urls: Set<String>) {
        if (urls.isEmpty()) return
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!urls.contains(o.optString("url"))) out.put(o)
        }
        writeArray(out)
    }
}

class BookmarkStore(context: Context) : JsonListStore(context, "bookmarks.json") {

    fun add(title: String, url: String, folderId: String = "", pinnedHome: Boolean = false) {
        if (url.isBlank()) return
        val arr = readArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("url") == url) {
                arr.getJSONObject(i)
                    .put("title", title)
                    .put("folderId", folderId)
                    .put("pinnedHome", pinnedHome)
                writeArray(arr)
                return
            }
        }
        arr.put(
            JSONObject()
                .put("title", title)
                .put("url", url)
                .put("folderId", folderId)
                .put("pinnedHome", pinnedHome)
                .put("time", System.currentTimeMillis())
        )
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

    fun removeUrls(urls: Set<String>) {
        if (urls.isEmpty()) return
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!urls.contains(o.optString("url"))) out.put(o)
        }
        writeArray(out)
    }

    fun moveToFolder(urls: Set<String>, folderId: String) {
        if (urls.isEmpty()) return
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (urls.contains(o.optString("url"))) o.put("folderId", folderId)
        }
        writeArray(arr)
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
            list.add(
                SiteEntry(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    time = o.optLong("time"),
                    folderId = o.optString("folderId"),
                    pinnedHome = o.optBoolean("pinnedHome", false)
                )
            )
        }
        return list
    }

    fun inFolder(folderId: String): List<SiteEntry> = all().filter { it.folderId == folderId }

    fun homeShortcuts(): List<SiteEntry> = all().filter { it.pinnedHome }

    fun clear() = writeArray(JSONArray())
}

data class BookmarkFolder(val id: String, val name: String, val time: Long = System.currentTimeMillis())

class BookmarkFolderStore(context: Context) : JsonListStore(context, "bookmark_folders.json") {

    fun add(name: String): String {
        val id = "f_" + System.currentTimeMillis().toString(36)
        val arr = readArray()
        arr.put(JSONObject().put("id", id).put("name", name).put("time", System.currentTimeMillis()))
        writeArray(arr)
        return id
    }

    fun remove(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        writeArray(out)
    }

    fun rename(id: String, newName: String) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) { o.put("name", newName); break }
        }
        writeArray(arr)
    }

    fun all(): List<BookmarkFolder> {
        val arr = readArray()
        val list = ArrayList<BookmarkFolder>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(BookmarkFolder(o.optString("id"), o.optString("name"), o.optLong("time")))
        }
        return list
    }
}

data class DownloadEntry(
    val id: Long,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val mimeType: String,
    val status: String,
    val time: Long
)

class DownloadStore(context: Context) : JsonListStore(context, "downloads.json") {

    fun add(entry: DownloadEntry) {
        val arr = readArray()
        arr.put(
            JSONObject()
                .put("id", entry.id)
                .put("name", entry.fileName)
                .put("url", entry.url)
                .put("size", entry.sizeBytes)
                .put("mime", entry.mimeType)
                .put("status", entry.status)
                .put("time", entry.time)
        )
        writeArray(arr)
    }

    fun all(): List<DownloadEntry> {
        val arr = readArray()
        val out = ArrayList<DownloadEntry>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(
                DownloadEntry(
                    o.optLong("id"),
                    o.optString("name"),
                    o.optString("url"),
                    o.optLong("size"),
                    o.optString("mime"),
                    o.optString("status", "complete"),
                    o.optLong("time")
                )
            )
        }
        return out
    }

    fun removeIds(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!ids.contains(o.optLong("id"))) out.put(o)
        }
        writeArray(out)
    }

    fun clear() = writeArray(JSONArray())
}

data class Userscript(
    val id: String,
    var name: String,
    var enabled: Boolean,
    var runAt: String,
    var matches: List<String>,
    var excludes: List<String>,
    var source: String,
    var lastUpdated: Long,
    var downloadUrl: String
) {
    fun matchesUrl(url: String): Boolean {
        if (matches.isEmpty()) return false
        if (excludes.any { wildcardMatch(it, url) }) return false
        return matches.any { wildcardMatch(it, url) }
    }

    companion object {
        const val RUN_DOC_START = "document-start"
        const val RUN_DOC_END = "document-end"
        const val RUN_DOC_IDLE = "document-idle"

        fun wildcardMatch(pattern: String, url: String): Boolean {
            if (pattern.isBlank()) return false
            // Translate Tampermonkey style * wildcards into a regex.
            val regex = buildString {
                append('^')
                for (ch in pattern) {
                    when (ch) {
                        '*' -> append(".*")
                        '.', '+', '?', '(', ')', '[', ']', '{', '}', '|', '\\', '^', '$' -> append('\\').append(ch)
                        else -> append(ch)
                    }
                }
                append('$')
            }
            return try { Regex(regex).containsMatchIn(url) } catch (e: Exception) { false }
        }
    }
}

class UserscriptStore(context: Context) : JsonListStore(context, "userscripts.json") {

    fun add(source: String, downloadUrl: String = ""): Userscript {
        val meta = parseMeta(source)
        val script = Userscript(
            id = "u_" + System.currentTimeMillis().toString(36),
            name = meta["name"]?.firstOrNull()?.ifBlank { null } ?: "未命名脚本",
            enabled = true,
            runAt = meta["run-at"]?.firstOrNull() ?: Userscript.RUN_DOC_END,
            matches = (meta["match"] ?: emptyList()).ifEmpty { listOf("*://*/*") },
            excludes = meta["exclude"] ?: emptyList(),
            source = source,
            lastUpdated = System.currentTimeMillis(),
            downloadUrl = downloadUrl
        )
        val arr = readArray()
        arr.put(toJson(script))
        writeArray(arr)
        return script
    }

    fun update(script: Userscript) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == script.id) {
                arr.put(i, toJson(script))
                writeArray(arr); return
            }
        }
    }

    fun remove(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        writeArray(out)
    }

    fun all(): List<Userscript> {
        val arr = readArray()
        val list = ArrayList<Userscript>(arr.length())
        for (i in 0 until arr.length()) list.add(fromJson(arr.getJSONObject(i)))
        return list
    }

    fun byId(id: String): Userscript? = all().firstOrNull { it.id == id }

    private fun toJson(s: Userscript) = JSONObject()
        .put("id", s.id).put("name", s.name).put("enabled", s.enabled)
        .put("runAt", s.runAt)
        .put("matches", JSONArray(s.matches))
        .put("excludes", JSONArray(s.excludes))
        .put("source", s.source)
        .put("lastUpdated", s.lastUpdated)
        .put("downloadUrl", s.downloadUrl)

    private fun fromJson(o: JSONObject) = Userscript(
        id = o.optString("id"),
        name = o.optString("name"),
        enabled = o.optBoolean("enabled", true),
        runAt = o.optString("runAt", Userscript.RUN_DOC_END),
        matches = jsonArrayToList(o.optJSONArray("matches")),
        excludes = jsonArrayToList(o.optJSONArray("excludes")),
        source = o.optString("source"),
        lastUpdated = o.optLong("lastUpdated"),
        downloadUrl = o.optString("downloadUrl")
    )

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }

    companion object {
        private val META_LINE = Regex("""//\s*@(\S+)\s+(.+)""")

        fun parseMeta(source: String): Map<String, List<String>> {
            val map = HashMap<String, MutableList<String>>()
            var inHeader = false
            for (raw in source.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("// ==UserScript==")) { inHeader = true; continue }
                if (line.startsWith("// ==/UserScript==")) break
                if (!inHeader) continue
                val m = META_LINE.matchEntire(line) ?: continue
                map.getOrPut(m.groupValues[1].lowercase()) { mutableListOf() }
                    .add(m.groupValues[2].trim())
            }
            return map
        }
    }
}

data class AdRule(val id: String, val selector: String, val domain: String, val time: Long)

class AdRuleStore(context: Context) : JsonListStore(context, "ad_rules.json") {
    fun add(selector: String, domain: String): AdRule {
        val rule = AdRule("r_" + System.currentTimeMillis().toString(36), selector, domain, System.currentTimeMillis())
        val arr = readArray()
        arr.put(JSONObject().put("id", rule.id).put("selector", rule.selector).put("domain", rule.domain).put("time", rule.time))
        writeArray(arr)
        return rule
    }

    fun remove(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        writeArray(out)
    }

    fun all(): List<AdRule> {
        val arr = readArray()
        val list = ArrayList<AdRule>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(AdRule(o.optString("id"), o.optString("selector"), o.optString("domain"), o.optLong("time")))
        }
        return list
    }

    fun forDomain(domain: String): List<AdRule> =
        all().filter { it.domain.isBlank() || it.domain == domain || domain.endsWith(".${it.domain}") }
}

data class RuleSubscription(val id: String, val name: String, val url: String, var enabled: Boolean)

class RuleSubscriptionStore(context: Context) : JsonListStore(context, "rule_subscriptions.json") {
    fun add(name: String, url: String): RuleSubscription {
        val sub = RuleSubscription("s_" + System.currentTimeMillis().toString(36), name, url, true)
        val arr = readArray()
        arr.put(JSONObject().put("id", sub.id).put("name", sub.name).put("url", sub.url).put("enabled", true))
        writeArray(arr)
        return sub
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) { o.put("enabled", enabled); break }
        }
        writeArray(arr)
    }

    fun remove(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        writeArray(out)
    }

    fun all(): List<RuleSubscription> {
        val arr = readArray()
        val list = ArrayList<RuleSubscription>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(RuleSubscription(o.optString("id"), o.optString("name"), o.optString("url"), o.optBoolean("enabled", true)))
        }
        return list
    }
}

data class OfflinePage(val path: String, val title: String, val url: String, val time: Long)

class OfflineStore(context: Context) : JsonListStore(context, "offline.json") {
    fun add(path: String, title: String, url: String) {
        val arr = readArray()
        arr.put(JSONObject().put("path", path).put("title", title).put("url", url).put("time", System.currentTimeMillis()))
        writeArray(arr)
    }

    fun all(): List<OfflinePage> {
        val arr = readArray()
        val list = ArrayList<OfflinePage>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            list.add(OfflinePage(o.optString("path"), o.optString("title"), o.optString("url"), o.optLong("time")))
        }
        return list
    }

    fun removePaths(paths: Set<String>) {
        if (paths.isEmpty()) return
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!paths.contains(o.optString("path"))) out.put(o)
        }
        writeArray(out)
    }
}
