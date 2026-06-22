package com.dy.tiktokmode

import android.content.Context
import android.content.SharedPreferences

/** Central settings store backed by SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    var adBlockEnabled: Boolean
        get() = sp.getBoolean("ad_block", true)
        set(v) = sp.edit().putBoolean("ad_block", v).apply()

    var nightMode: Boolean
        get() = sp.getBoolean("night_mode", false)
        set(v) = sp.edit().putBoolean("night_mode", v).apply()

    var noImageMode: Boolean
        get() = sp.getBoolean("no_image", false)
        set(v) = sp.edit().putBoolean("no_image", v).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop_mode", false)
        set(v) = sp.edit().putBoolean("desktop_mode", v).apply()

    var doNotTrack: Boolean
        get() = sp.getBoolean("dnt", true)
        set(v) = sp.edit().putBoolean("dnt", v).apply()

    /** "" = engine default UA, otherwise a custom UA override. */
    var customUa: String
        get() = sp.getString("custom_ua", "") ?: ""
        set(v) = sp.edit().putString("custom_ua", v).apply()

    var searchEngineKey: String
        get() = sp.getString("search_engine", "google") ?: "google"
        set(v) = sp.edit().putString("search_engine", v).apply()

    /** Custom search template, must contain %s. Used when searchEngineKey == "custom". */
    var customSearchUrl: String
        get() = sp.getString("custom_search_url", "https://www.google.com/search?q=%s") ?: ""
        set(v) = sp.edit().putString("custom_search_url", v).apply()

    var homepageUrl: String
        get() = sp.getString("homepage", HOME_URL) ?: HOME_URL
        set(v) = sp.edit().putString("homepage", v).apply()

    /** Optional homepage background image URI / URL; "" = default gradient. */
    var homepageBackground: String
        get() = sp.getString("home_bg", "") ?: ""
        set(v) = sp.edit().putString("home_bg", v).apply()

    var homepageTitle: String
        get() = sp.getString("home_title", "DY 浏览器") ?: "DY 浏览器"
        set(v) = sp.edit().putString("home_title", v).apply()

    /** User-supplied CSS injected into every page (UI customization). */
    var customCss: String
        get() = sp.getString("custom_css", "") ?: ""
        set(v) = sp.edit().putString("custom_css", v).apply()

    /** User-supplied JS (a lightweight Tampermonkey-style global script). */
    var userScript: String
        get() = sp.getString("user_script", "") ?: ""
        set(v) = sp.edit().putString("user_script", v).apply()

    companion object {
        const val HOME_URL = "about:home"
    }
}
