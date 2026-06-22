package com.dy.tiktokmode

data class VideoItem(val title: String, val detailUrl: String)

/**
 * Per-site rules: how to collect video links from a listing/detail page,
 * and how to strip chrome + fullscreen the <video> once a detail page loads.
 * Ported from tiktok-mode.js's collectVideoLinks()/style block, but run as a
 * top-level navigation per item instead of an iframe, so no postMessage relay
 * is needed - the injected JS has direct same-document access to the video.
 */
object SiteConfig {

    fun matches(hostname: String): Boolean =
        hostname.contains("missav") || hostname.contains("baixiaotangtop")

    fun isMissAv(hostname: String): Boolean = hostname.contains("missav")

    /** Injected into the listing page; reports back via Android.onItems(json). */
    fun collectLinksJs(hostname: String): String {
        return if (isMissAv(hostname)) {
            """
            (function() {
                var items = [];
                if (location.pathname.length > 3) {
                    items.push({ title: (document.title || '当前视频').replace(' - MissAV.ai', ''), detailUrl: location.href });
                }
                var links = Array.prototype.slice.call(document.querySelectorAll('a')).filter(function(a) {
                    return /^https?:\/\/[^\/]+\/([a-z]{2}\/)?[a-zA-Z0-9]+[-_][a-zA-Z0-9-_]+(\?.*)?$/i.test(a.href);
                });
                var seen = {};
                var unique = [];
                links.forEach(function(a) {
                    var key = a.href.split('?')[0];
                    if (!seen[key]) { seen[key] = true; unique.push(a); }
                });
                unique.forEach(function(a) {
                    if (a.href.split('?')[0] === location.href.split('?')[0]) return;
                    var title = a.innerText.trim();
                    if (!title) { var img = a.querySelector('img'); if (img) title = img.alt || title; }
                    if (!title) { var parts = a.href.split('?')[0].split('/'); title = parts[parts.length - 1]; }
                    items.push({ title: title || '推荐视频', detailUrl: a.href });
                });
                Android.onItems(JSON.stringify(items));
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                var items = [];
                var isDetail = location.pathname.indexOf('/voddetail/') !== -1 || location.pathname.indexOf('/vodplay/') !== -1;
                var links;
                if (isDetail) {
                    links = Array.prototype.slice.call(document.querySelectorAll('a[href*="/vodplay/"]'));
                } else {
                    links = Array.prototype.slice.call(document.querySelectorAll('a[href*="/voddetail/"]'));
                }
                var seen = {};
                links.forEach(function(a) {
                    if (seen[a.href]) return;
                    seen[a.href] = true;
                    var title = a.title || a.innerText.trim();
                    if (!title) { var img = a.querySelector('img'); if (img) title = img.alt || title; }
                    if (title) items.push({ title: title, detailUrl: a.href });
                });
                Android.onItems(JSON.stringify(items));
            })();
            """.trimIndent()
        }
    }

    /** Injected into each detail page once loaded: hide chrome, fullscreen the video, autoplay muted. */
    const val STYLE_AND_AUTOPLAY_JS = """
        (function() {
            var style = document.createElement('style');
            style.textContent = [
                'html, body { background:#000 !important; margin:0 !important; padding:0 !important; overflow:hidden !important; }',
                'body > *:not(script):not(style) { display:none !important; }',
                '.plyr, video, .max-w-7xl, .player-container, .MacPlayer { display:block !important; opacity:1 !important; visibility:visible !important; }',
                'video { position:fixed !important; top:0 !important; left:0 !important; width:100vw !important; height:100vh !important; object-fit:contain !important; background:#000 !important; z-index:999999 !important; }'
            ].join('\n');
            document.head.appendChild(style);

            var tries = 0;
            var iv = setInterval(function() {
                var v = document.querySelector('video');
                tries++;
                if (v) {
                    v.muted = true;
                    v.play().catch(function() {});
                    clearInterval(iv);
                } else if (tries > 40) {
                    clearInterval(iv);
                }
            }, 250);
        })();
    """

    const val UNMUTE_PLAY_JS = "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.play().catch(function(){});}})();"
    const val MUTE_PLAY_JS = "(function(){var v=document.querySelector('video');if(v){v.muted=true;v.play().catch(function(){});}})();"
    const val PAUSE_JS = "(function(){var v=document.querySelector('video');if(v){v.pause();}})();"
    const val PLAY_JS = "(function(){var v=document.querySelector('video');if(v){v.play().catch(function(){});}})();"
}
