package com.dy.tiktokmode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** "document-start" runs at onPageStarted; "document-end"/"document-idle" run at onPageFinished. */
data class UserScript(
    val id: String,
    val name: String,
    val code: String,
    val runAt: String = "document-end",
    val matches: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val enabled: Boolean = true,
    val updateUrl: String = ""
)

class UserScriptStore(context: Context) : JsonListStore(context, "user_scripts.json") {

    fun all(): List<UserScript> {
        val arr = readArray()
        val list = ArrayList<UserScript>(arr.length())
        for (i in 0 until arr.length()) list.add(fromJson(arr.getJSONObject(i)))
        return list
    }

    fun add(script: UserScript) {
        val arr = readArray()
        arr.put(toJson(script))
        writeArray(arr)
    }

    fun update(script: UserScript) {
        val arr = readArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == script.id) {
                arr.put(i, toJson(script))
                writeArray(arr)
                return
            }
        }
    }

    fun remove(id: String) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") != id) out.put(arr.getJSONObject(i))
        }
        writeArray(out)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        all().find { it.id == id }?.let { update(it.copy(enabled = enabled)) }
    }

    /** Scripts (enabled, matching [url], not excluded) for the given run timing. */
    fun activeFor(url: String, runAt: String): List<UserScript> =
        all().filter { it.enabled && it.runAt == runAt && matchesUrl(it, url) }

    private fun matchesUrl(script: UserScript, url: String): Boolean {
        if (script.excludes.any { globMatch(it, url) }) return false
        if (script.matches.isEmpty()) return true
        return script.matches.any { globMatch(it, url) }
    }

    private fun globMatch(pattern: String, url: String): Boolean {
        val regex = pattern.split("*").joinToString(".*") { Regex.escape(it) }
        return try { Regex("^$regex$").matches(url) } catch (_: Exception) { false }
    }

    private fun toJson(s: UserScript): JSONObject = JSONObject()
        .put("id", s.id).put("name", s.name).put("code", s.code).put("runAt", s.runAt)
        .put("matches", JSONArray(s.matches)).put("excludes", JSONArray(s.excludes))
        .put("enabled", s.enabled).put("updateUrl", s.updateUrl)

    private fun fromJson(o: JSONObject): UserScript {
        fun strList(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        return UserScript(
            id = o.optString("id"),
            name = o.optString("name"),
            code = o.optString("code"),
            runAt = o.optString("runAt", "document-end"),
            matches = strList("matches"),
            excludes = strList("excludes"),
            enabled = o.optBoolean("enabled", true),
            updateUrl = o.optString("updateUrl")
        )
    }
}
