package com.dy.tiktokmode

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Modal "add to bookmark" dialog with folder picker + home pin. */
object BookmarkDialog {

    fun show(
        activity: Activity,
        bookmarks: BookmarkStore,
        folders: BookmarkFolderStore,
        defaultTitle: String,
        defaultUrl: String,
        onSaved: () -> Unit = {}
    ) {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        fun label(text: String) = TextView(activity).apply {
            this.text = text
            setTextColor(0xFFB5B5BC.toInt())
            textSize = 12f
            setPadding(0, dp(10), 0, dp(4))
        }

        fun input(hint: String, value: String) = EditText(activity).apply {
            setText(value)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF6E6E76.toInt())
        }

        val titleInput = input("标题", defaultTitle)
        val urlInput = input("链接", defaultUrl).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        val folderList = folders.all()
        val folderLabels = mutableListOf("根目录")
        val folderIds = mutableListOf("")
        folderList.forEach { folderLabels.add(it.name); folderIds.add(it.id) }

        val folderSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, folderLabels)
            setSelection(0)
        }

        val newFolderBtn = Button(activity).apply {
            text = "+ 新建文件夹"
            setBackgroundColor(0)
            setTextColor(0xFFFE2C55.toInt())
            textSize = 12f
            setOnClickListener {
                val edit = EditText(activity).apply { hint = "文件夹名称" }
                AlertDialog.Builder(activity)
                    .setTitle("新建文件夹")
                    .setView(edit)
                    .setPositiveButton("创建") { _, _ ->
                        val name = edit.text.toString().trim()
                        if (name.isNotEmpty()) {
                            val id = folders.add(name)
                            folderLabels.add(name); folderIds.add(id)
                            @Suppress("UNCHECKED_CAST")
                            (folderSpinner.adapter as ArrayAdapter<String>).notifyDataSetChanged()
                            folderSpinner.setSelection(folderLabels.size - 1)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        val pinRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(2))
        }
        pinRow.addView(TextView(activity).apply {
            text = "添加到主页收藏"
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val pinSwitch = Switch(activity).apply { isChecked = false }
        pinRow.addView(pinSwitch)

        root.addView(label("标题"))
        root.addView(titleInput)
        root.addView(label("链接"))
        root.addView(urlInput)
        root.addView(label("根目录"))
        root.addView(folderSpinner)
        root.addView(newFolderBtn)
        root.addView(pinRow)

        AlertDialog.Builder(activity)
            .setTitle("添加书签")
            .setView(root)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val t = titleInput.text.toString().ifBlank { urlInput.text.toString() }
                val u = urlInput.text.toString().trim()
                if (u.isBlank()) {
                    Toast.makeText(activity, "链接不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val folderId = folderIds[folderSpinner.selectedItemPosition.coerceIn(0, folderIds.size - 1)]
                bookmarks.add(t, u, folderId, pinSwitch.isChecked)
                Toast.makeText(activity, "已添加书签", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .show()
    }
}
