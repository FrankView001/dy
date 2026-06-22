package com.dy.tiktokmode

data class VideoItem(val title: String, val detailUrl: String)

/**
 * Per-site rules plus the JS that turns an ordinary detail page into a clean
 * fullscreen player.
 *
 * Why re-parent instead of hiding chrome: the previous approach hid every
 * `body > *`, but the <video> lives deep inside one of those containers, and
 * `display:none` on an ancestor can't be overridden by the child - so the
 * video never rendered. Here we instead MOVE the <video> node into our own
 * fullscreen stage element (keeping the same node, so the site's hls.js player
 * keeps controlling it) and hide everything else.
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

    /**
     * Injected once per detail page. Repeatedly tries to locate a <video> (in
     * the main doc or any same-origin iframe), moves it into a fullscreen stage,
     * hides the rest of the page, kills popups, and autoplays muted.
     */
    val STYLE_AND_AUTOPLAY_JS = """
        (function() {
            if (window.__tkInstalled) { window.__tkApply && window.__tkApply(); return; }
            window.__tkInstalled = true;

            // Neutralise popunders/new-window ad hijacks that these sites fire.
            try {
                window.open = function() { return null; };
                window.alert = function() {};
                document.addEventListener('click', function(e){
                    var a = e.target && e.target.closest && e.target.closest('a[target=_blank]');
                    if (a) { a.removeAttribute('target'); }
                }, true);
            } catch (e) {}

            function styleDoc(doc) {
                try {
                    var v = doc.querySelector('video');
                    if (!v) return false;
                    var stage = doc.getElementById('__tk_stage');
                    if (!stage) {
                        stage = doc.createElement('div');
                        stage.id = '__tk_stage';
                        stage.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:#000;z-index:2147483647;display:flex;align-items:center;justify-content:center;margin:0;padding:0;';
                        (doc.documentElement || doc.body).appendChild(stage);
                    }
                    if (v.parentNode !== stage) stage.appendChild(v);
                    v.style.cssText = 'width:100%;height:100%;object-fit:contain;background:#000;';
                    v.setAttribute('playsinline', '');
                    v.setAttribute('webkit-playsinline', '');
                    if (!window.__tkUnmuted) { v.muted = true; }
                    v.play().catch(function(){});
                    var st = doc.getElementById('__tk_style');
                    if (!st) {
                        st = doc.createElement('style');
                        st.id = '__tk_style';
                        st.textContent = 'html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;}body>*:not(#__tk_stage){display:none!important;}';
                        (doc.head || doc.documentElement).appendChild(st);
                    }
                    return true;
                } catch (e) { return false; }
            }

            window.__tkApply = function() {
                var found = styleDoc(document);
                var frames = document.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                    try {
                        var d = frames[i].contentDocument;
                        if (d && styleDoc(d)) {
                            frames[i].style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;border:0!important;z-index:2147483647!important;background:#000!important;';
                            found = true;
                        }
                    } catch (e) {}
                }
                return found;
            };

            var tries = 0;
            var iv = setInterval(function() {
                tries++;
                var ok = window.__tkApply();
                if (ok && tries > 6) { clearInterval(iv); }
                if (tries > 80) { clearInterval(iv); }
            }, 250);
        })();
    """

    // Control helpers also reach into same-origin iframes.
    private val FOR_EACH_VIDEO = """
        function(fn){
            var v=document.querySelector('video'); if(v) fn(v);
            var fr=document.querySelectorAll('iframe');
            for(var i=0;i<fr.length;i++){try{var d=fr[i].contentDocument;if(d){var iv=d.querySelector('video');if(iv)fn(iv);}}catch(e){}}
        }
    """

    val UNMUTE_PLAY_JS = "(function(){window.__tkUnmuted=true;($FOR_EACH_VIDEO)(function(v){v.muted=false;v.play().catch(function(){});});})();"
    val MUTE_PLAY_JS = "(function(){($FOR_EACH_VIDEO)(function(v){if(!window.__tkUnmuted)v.muted=true;v.play().catch(function(){});});})();"
    val PAUSE_JS = "(function(){($FOR_EACH_VIDEO)(function(v){v.pause();});})();"
    val PLAY_JS = "(function(){($FOR_EACH_VIDEO)(function(v){v.play().catch(function(){});});})();"
}
