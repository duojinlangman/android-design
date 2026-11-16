package com.example.dancijiyi.ui

import android.content.Context
import com.example.dancijiyi.data.ReviewQuota
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 「每天复习多少个单词」选择弹窗：10 / 20 / 50 / 100 */
object ReviewQuotaDialog {

    fun show(context: Context, onChange: () -> Unit) {
        val options = ReviewQuota.options
        val labels = options.map { "$it 个" }.toTypedArray()
        val current = ReviewQuota.get(context)
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        var picked = currentIndex

        MaterialAlertDialogBuilder(context)
            .setTitle("每天复习多少个单词")
            .setSingleChoiceItems(labels, currentIndex) { _, which -> picked = which }
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                ReviewQuota.set(context, options[picked])
                onChange()
            }
            .show()
    }
}
