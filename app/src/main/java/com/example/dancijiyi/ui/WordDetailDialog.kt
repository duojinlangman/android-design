package com.example.dancijiyi.ui

import android.content.Context
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.data.Word
import com.example.dancijiyi.ui.study.StudyActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 单词详情弹窗：看完整信息，也能单独复习 / 标记掌握 / 重置进度 */
object WordDetailDialog {

    fun show(context: Context, word: Word, onChanged: () -> Unit) {
        val repo = DanciApp.repository
        val message = buildString {
            append("美式音标：${if (word.phonetic.isBlank()) "待补充" else word.phonetic}\n")
            append("释义：${word.meaning}\n")
            append("复习阶段：${word.stageText}\n")
            append("下次：${word.nextReviewText}\n")
            append("记忆记录：记住 ${word.correctCount} 次 / 忘记 ${word.wrongCount} 次")
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(word.word)
            .setMessage(message)
            .setPositiveButton("单独复习") { _, _ ->
                StudyActivity.start(context, longArrayOf(word.id), "单词巩固")
            }
            .setNegativeButton("关闭") { _, _ -> }
            .setNeutralButton(if (word.mastered) "取消掌握" else "标记掌握") { _, _ ->
                repo.setMastered(word.id, !word.mastered)
                onChanged()
            }
            .show()
    }
}
