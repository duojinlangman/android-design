package com.example.dancijiyi.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.ui.AddWordDialog
import com.example.dancijiyi.ui.WordDetailDialog
import com.example.dancijiyi.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 某个词库里的单词列表：搜索、标记重点、删除、查看详情 */
class WordListActivity : AppCompatActivity() {

    private val repo get() = DanciApp.repository

    private var bookId: Long = 0
    private var bookName: String = ""

    private lateinit var etSearch: EditText
    private lateinit var rvWords: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: WordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0)
        bookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "词库"

        findViewById<TextView>(R.id.tvTitle).text = bookName
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            AddWordDialog.show(this, bookId) { load(etSearch.text.toString()) }
        }

        etSearch = findViewById(R.id.etSearch)
        rvWords = findViewById(R.id.rvWords)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = WordAdapter(
            onClick = { word -> WordDetailDialog.show(this, word) { load(etSearch.text.toString()) } },
            onStar = { word ->
                repo.setStarred(word.id, !word.starred)
                load(etSearch.text.toString())
            },
            onDelete = { word -> confirmDelete(word.word, word.id) }
        )
        rvWords.layoutManager = LinearLayoutManager(this)
        rvWords.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                load(s?.toString()?.trim() ?: "")
            }
        })

        load("")
    }

    private fun load(keyword: String) {
        val words = repo.words(bookId, keyword)
        adapter.submit(words)
        tvEmpty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = if (keyword.isBlank()) "这里还没有单词，点右上角 + 添加"
        else "没有找到「$keyword」"
    }

    private fun confirmDelete(word: String, id: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除 $word？")
            .setMessage("删除后这个词的复习记录也会一并清除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                repo.deleteWord(id)
                toast("已删除")
                load(etSearch.text.toString())
            }
            .show()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "extra_book_id"
        private const val EXTRA_BOOK_NAME = "extra_book_name"

        fun start(context: Context, bookId: Long, bookName: String) {
            context.startActivity(
                Intent(context, WordListActivity::class.java)
                    .putExtra(EXTRA_BOOK_ID, bookId)
                    .putExtra(EXTRA_BOOK_NAME, bookName)
            )
        }
    }
}
