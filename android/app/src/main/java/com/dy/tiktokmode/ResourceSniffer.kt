package com.dy.tiktokmode

import android.net.Uri

/**
 * Collects media resource URLs (video/audio) seen during request interception
 * so the user can grab a stream that the page would otherwise hide.
 */
class ResourceSniffer {
    data class Resource(val url: String, val type: String)

    private val seen = LinkedHashSet<String>()
    private val list = ArrayList<Resource>()

    private val mediaExt = listOf(".m3u8", ".mp4", ".m4s", ".ts", ".mp3", ".m4a", ".flac", ".aac", ".webm", ".mov", ".mpd")

    @Synchronized
    fun consider(url: String?) {
        if (url.isNullOrEmpty()) return
        val path = try { Uri.parse(url).path?.lowercase() ?: "" } catch (e: Exception) { url.lowercase() }
        val ext = mediaExt.firstOrNull { path.contains(it) } ?: return
        if (seen.add(url)) {
            val type = when {
                ext == ".m3u8" || ext == ".mpd" || ext == ".ts" || ext == ".m4s" -> "stream"
                ext == ".mp3" || ext == ".m4a" || ext == ".flac" || ext == ".aac" -> "audio"
                else -> "video"
            }
            list.add(Resource(url, type))
            if (list.size > 200) { val r = list.removeAt(0); seen.remove(r.url) }
        }
    }

    @Synchronized
    fun snapshot(): List<Resource> = ArrayList(list)

    @Synchronized
    fun clear() { seen.clear(); list.clear() }
}
