package com.dy.tiktokmode

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    // Spinner order: built-in engines then "自定义".
    private val engineKeys = SearchEngines.BUILT_IN.map { it.key } + "custom"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "设置"
        prefs = Prefs(this)

        val spinner = findViewById<Spinner>(R.id.searchEngineSpinner)
        val labels = SearchEngines.BUILT_IN.map { it.label } + "自定义"
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(engineKeys.indexOf(prefs.searchEngineKey).coerceAtLeast(0))

        val customSearchUrl = findViewById<EditText>(R.id.customSearchUrl).apply { setText(prefs.customSearchUrl) }
        val customUa = findViewById<EditText>(R.id.customUa).apply { setText(prefs.customUa) }
        val homeTitle = findViewById<EditText>(R.id.homeTitle).apply { setText(prefs.homepageTitle) }
        val homeUrl = findViewById<EditText>(R.id.homeUrl).apply { setText(prefs.homepageUrl) }
        val homeBg = findViewById<EditText>(R.id.homeBg).apply { setText(prefs.homepageBackground) }
        val customCss = findViewById<EditText>(R.id.customCss).apply { setText(prefs.customCss) }
        val userScript = findViewById<EditText>(R.id.userScript).apply { setText(prefs.userScript) }
        val dnt = findViewById<Switch>(R.id.dntSwitch).apply { isChecked = prefs.doNotTrack }

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            prefs.searchEngineKey = engineKeys[spinner.selectedItemPosition]
            prefs.customSearchUrl = customSearchUrl.text.toString().trim()
            prefs.customUa = customUa.text.toString().trim()
            prefs.homepageTitle = homeTitle.text.toString().trim().ifBlank { "DY 浏览器" }
            prefs.homepageUrl = homeUrl.text.toString().trim().ifBlank { Prefs.HOME_URL }
            prefs.homepageBackground = homeBg.text.toString().trim()
            prefs.customCss = customCss.text.toString()
            prefs.userScript = userScript.text.toString()
            prefs.doNotTrack = dnt.isChecked
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
