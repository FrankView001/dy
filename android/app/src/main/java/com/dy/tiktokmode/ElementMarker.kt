package com.dy.tiktokmode

import android.app.Activity
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Drives "mark ad" mode: injects a JS overlay into the current WebView that
 * tracks tapped elements, plus a native floating toolbar with ^/expand/shrink/save/cancel.
 */
class ElementMarker(
    private val activity: Activity,
    private val rootContainer: FrameLayout,
    private val rules: AdRuleStore,
    private val onExit: () -> Unit
) {
    private var webView: WebView? = null
    private var bar: View? = null
    private var atTop = false
    private var currentSelector: String = ""
    private var domain: String = ""

    fun start(wv: WebView) {
        if (bar != null) return
        webView = wv
        domain = try { Uri.parse(wv.url).host ?: "" } catch (e: Exception) { "" }
        wv.evaluateJavascript(INJECT_JS, null)
        wv.evaluateJavascript("window.__dyMarker && window.__dyMarker.setActive(true);", null)
        showBar()
    }

    fun stop() {
        webView?.evaluateJavascript("window.__dyMarker && window.__dyMarker.setActive(false);", null)
        bar?.let { rootContainer.removeView(it) }
        bar = null
        webView = null
        currentSelector = ""
        onExit()
    }

    fun onSelectionChanged(selector: String) {
        currentSelector = selector
        activity.runOnUiThread {
            (bar?.findViewWithTag<TextView>("selectorLabel"))?.text =
                if (selector.isBlank()) "请点击页面中要屏蔽的元素" else selector
        }
    }

    private fun showBar() {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1C1C1F.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        container.addView(TextView(activity).apply {
            tag = "selectorLabel"
            text = "请点击页面中要屏蔽的元素"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(dp(8), dp(2), dp(8), dp(6))
            maxLines = 2
        })
        val toolbar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(toolButton("^") { togglePosition() })
        toolbar.addView(toolButton("扩大") { webView?.evaluateJavascript("window.__dyMarker && window.__dyMarker.expand();", null) })
        toolbar.addView(toolButton("缩小") { webView?.evaluateJavascript("window.__dyMarker && window.__dyMarker.shrink();", null) })
        toolbar.addView(toolButton("保存") { saveRule() })
        toolbar.addView(toolButton("取消") { stop() })
        container.addView(toolbar)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = if (atTop) Gravity.TOP else Gravity.BOTTOM }
        rootContainer.addView(container, lp)
        bar = container
    }

    private fun togglePosition() {
        atTop = !atTop
        bar?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            lp.gravity = if (atTop) Gravity.TOP else Gravity.BOTTOM
            it.layoutParams = lp
        }
    }

    private fun toolButton(label: String, onClick: () -> Unit): Button {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }
        return Button(activity).apply {
            text = label
            background = null
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minimumWidth = dp(60)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun saveRule() {
        if (currentSelector.isBlank()) {
            Toast.makeText(activity, "请先选择要屏蔽的元素", Toast.LENGTH_SHORT).show(); return
        }
        rules.add(currentSelector, domain)
        webView?.evaluateJavascript(
            "window.__dyMarker && window.__dyMarker.hide(${escapeJs(currentSelector)});", null
        )
        Toast.makeText(activity, "已保存屏蔽规则", Toast.LENGTH_SHORT).show()
        stop()
    }

    private fun escapeJs(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("'", "\\'")
        return "'$escaped'"
    }

    companion object {
        const val INJECT_JS = """
(function(){
  if(window.__dyMarker) return;
  var current=null, origOutline='';
  function highlight(el){
    if(current) current.style.outline=origOutline;
    current=el;
    if(el){ origOutline=el.style.outline||''; el.style.outline='2px solid #FE2C55'; el.style.outlineOffset='-2px'; }
  }
  function cssSelectorFor(el){
    if(!el||el===document) return '';
    if(el.id) return '#'+el.id.replace(/([^a-zA-Z0-9_-])/g,'\\$1');
    var parts=[], depth=0;
    while(el && el.nodeType===1 && el!==document.body && depth<8){
      var sel=el.tagName.toLowerCase();
      if(el.className && typeof el.className==='string'){
        var cls=el.className.trim().split(/\s+/).filter(function(c){return c && !c.includes(':');}).slice(0,2).map(function(c){return '.'+c.replace(/([^a-zA-Z0-9_-])/g,'\\$1');}).join('');
        sel+=cls;
      }
      if(el.parentNode){
        var sibs=Array.prototype.filter.call(el.parentNode.children,function(c){return c.tagName===el.tagName;});
        if(sibs.length>1) sel+=':nth-of-type('+(sibs.indexOf(el)+1)+')';
      }
      parts.unshift(sel);
      el=el.parentNode; depth++;
    }
    return parts.join('>');
  }
  function onTap(e){
    if(!window.__dyMarkerActive) return;
    e.preventDefault(); e.stopPropagation();
    highlight(e.target);
    try{ Android.markerSelectionChanged(cssSelectorFor(e.target)); }catch(_){}
    return false;
  }
  document.addEventListener('click', onTap, true);
  document.addEventListener('touchstart', onTap, true);
  window.__dyMarker={
    setActive:function(v){ window.__dyMarkerActive=v; if(!v) highlight(null); },
    expand:function(){
      if(current && current.parentNode && current.parentNode!==document && current.parentNode!==document.documentElement) highlight(current.parentNode);
      try{ Android.markerSelectionChanged(current?cssSelectorFor(current):''); }catch(_){}
    },
    shrink:function(){
      if(current && current.firstElementChild) highlight(current.firstElementChild);
      try{ Android.markerSelectionChanged(current?cssSelectorFor(current):''); }catch(_){}
    },
    hide:function(sel){ try{ document.querySelectorAll(sel).forEach(function(e){e.style.display='none';}); }catch(e){} },
    clear:function(){ highlight(null); }
  };
})();
"""
    }
}
