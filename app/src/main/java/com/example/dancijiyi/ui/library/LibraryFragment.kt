package com.example.dancijiyi.ui.library

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.data.WordBook
import com.example.dancijiyi.data.WordImporter
import com.example.dancijiyi.ui.AddWordDialog
import com.example.dancijiyi.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val repo get() = DanciApp.repository

    private lateinit var rvBooks: RecyclerView
    private lateinit var adapter: BookAdapter

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handlePickedFile(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvBooks = view.findViewById(R.id.rvBooks)
        adapter = BookAdapter(
            onClick = { book -> WordListActivity.start(requireContext(), book.id, book.name) },
            onLongClick = { book -> showBookOptions(book) }
        )
        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnImport).setOnClickListener { showImportDialog() }
        view.findViewById<MaterialButton>(R.id.btnAddWord).setOnClickListener {
            AddWordDialog.show(requireContext(), repo.newWordBookId()) { refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(repo.books())
    }

    // ------------------------------------------------------------ 导入

    private fun showImportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入词库")
            .setMessage(
                "支持 .txt / .csv 文本，每行一个单词：\n\n" +
                    "abandon,放弃\n" +
                    "abandon,/əˈbændən/,放弃\n" +
                    "abandon\t/əˈbændən/\t放弃\n\n" +
                    "音标列可省略；重复的词会自动跳过。"
            )
            .setPositiveButton("选择文件") { _, _ -> pickFile.launch(arrayOf("*/*")) }
            .setNeutralButton("导入示例") { _, _ -> importSample() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handlePickedFile(uri: Uri) {
        val fileName = queryFileName(uri) ?: "导入词库"
        val text = try {
            WordImporter.readText(requireContext(), uri)
        } catch (e: Exception) {
            requireContext().toast("文件读取失败：${e.message}")
            return
        }
        val parsed = WordImporter.parse(text)
        if (parsed.isEmpty()) {
            requireContext().toast("没有识别到有效单词，请检查每行格式")
            return
        }
        val suggestedName = fileName.substringBeforeLast(".").ifBlank { "导入词库" }
        chooseTargetBook(parsed, suggestedName)
    }

    private fun importSample() {
        val text = try {
            requireContext().assets.open("import_sample.csv").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            requireContext().toast("示例词库读取失败")
            return
        }
        val parsed = WordImporter.parse(text)
        if (parsed.isEmpty()) {
            requireContext().toast("示例词库为空")
            return
        }
        val bookId = repo.createBook("示例词库")
        val result = repo.addWords(bookId, parsed)
        requireContext().toast("已导入 ${result} 个单词到「示例词库」")
        refresh()
    }

    private fun chooseTargetBook(words: List<com.example.dancijiyi.data.Word>, suggestedName: String) {
        val books = repo.books().filter { !it.isSystem }
        val options = mutableListOf("加入生词本")
        options.addAll(books.map { "加入《${it.name}》" })
        options.add("新建词库")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入 ${words.size} 个单词到")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> doImport(repo.newWordBookId(), words)
                    options.lastIndex -> askNewBookName(suggestedName, words)
                    else -> doImport(books[which - 1].id, words)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun askNewBookName(suggested: String, words: List<com.example.dancijiyi.data.Word>) {
        val input = EditText(requireContext()).apply {
            setText(suggested)
            hint = "词库名称"
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建词库")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建并导入") { _, _ ->
                val name = input.text.toString().trim().ifBlank { suggested }
                val bookId = repo.createBook(name)
                doImport(bookId, words)
            }
            .show()
    }

    private fun doImport(bookId: Long, words: List<com.example.dancijiyi.data.Word>) {
        val added = repo.addWords(bookId, words)
        val skipped = words.size - added
        val message = if (skipped > 0) "已导入 $added 个单词，跳过重复 $skipped 个"
        else "已导入 $added 个单词"
        requireContext().toast(message)
        refresh()
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && c.moveToFirst()) c.getString(index) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showBookOptions(book: WordBook) {
        if (book.isSystem) {
            requireContext().toast("生词本是系统词库，不能重命名或删除")
            return
        }
        val options = arrayOf("重命名", "删除词库")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.name)
            .setItems(options) { _, which ->
                if (which == 0) renameBook(book) else confirmDeleteBook(book)
            }
            .show()
    }

    private fun renameBook(book: WordBook) {
        val input = EditText(requireContext()).apply {
            setText(book.name)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重命名词库")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.renameBook(book.id, name)
                    refresh()
                }
            }
            .show()
    }

    private fun confirmDeleteBook(book: WordBook) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除《${book.name}》？")
            .setMessage("词库里的 ${book.wordCount} 个单词和复习记录都会被删除，无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                repo.deleteBook(book.id)
                requireContext().toast("已删除")
                refresh()
            }
            .show()
    }
}
