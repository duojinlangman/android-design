package com.example.dancijiyi.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 添加生词的弹窗 */
object AddWordDialog {

    fun show(context: Context, bookId: Long, onAdded: () -> Unit = {}) {
        val repo = DanciApp.repository
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_word, null)
        val etWord = view.findViewById<EditText>(R.id.etWord)
        val etPhonetic = view.findViewById<EditText>(R.id.etPhonetic)
        val etMeaning = view.findViewById<EditText>(R.id.etMeaning)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("添加单词")
            .setView(view)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val word = etWord.text.toString().trim()
                val meaning = etMeaning.text.toString().trim()
                if (word.isBlank()) {
                    etWord.error = "请输入英文单词"
                    return@setOnClickListener
                }
                if (!word.matches(Regex("^[A-Za-z][A-Za-z' -]*$"))) {
                    etWord.error = "只能输入英文字母"
                    return@setOnClickListener
                }
                if (meaning.isBlank()) {
                    etMeaning.error = "请输入中文释义"
                    return@setOnClickListener
                }
                val duplicate = repo.findWordByText(word)
                if (duplicate != null) {
                    val bookName = repo.bookNameOfWord(duplicate.id) ?: "其他词库"
                    context.toast("「$word」已在词库「$bookName」中，无需重复添加")
                    return@setOnClickListener
                }
                val id = repo.addWord(
                    bookId = bookId,
                    word = word,
                    phonetic = etPhonetic.text.toString(),
                    meaning = meaning
                )
                if (id <= 0) {
                    context.toast("这个词已经在词库里了")
                    return@setOnClickListener
                }
                context.toast("已添加，现在就可以复习")
                dialog.dismiss()
                onAdded()
            }
        }
        dialog.show()
    }
}
