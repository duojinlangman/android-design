package com.example.dancijiyi.data.online

import android.content.Context
import com.example.dancijiyi.data.WordRepository

/** 一次下载/更新的结果，用来给用户一句明确的反馈 */
data class UpdateResult(
    val bookName: String,
    val version: Int,
    val total: Int,
    val added: Int
) {
    val skipped: Int get() = total - added
}

/**
 * 把远端词库落到本地数据库：
 * 第一次下载时新建一个同名词库，之后再更新就继续往这个词库里补词，
 * 这样用户已掌握的单词不会因为更新而丢失。
 */
object OnlineUpdater {

    fun download(context: Context, repo: WordRepository, book: OnlineBook): UpdateResult {
        val words = OnlineCatalog.fetchWords(book)

        var bookId = OnlineStore.bookId(context, book.id)
        val bookStillExists = repo.books().any { it.id == bookId }
        if (bookId <= 0 || !bookStillExists) {
            bookId = repo.createBook(book.name)
            OnlineStore.setBookId(context, book.id, bookId)
        }

        // addWords 自带全局去重：已存在的拼写会跳过，只补新词
        val added = repo.addWords(bookId, words)
        OnlineStore.setVersion(context, book.id, book.version)
        return UpdateResult(
            bookName = book.name,
            version = book.version,
            total = words.size,
            added = added
        )
    }
}
