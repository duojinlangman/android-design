package com.example.dancijiyi.data.online

import android.util.Base64
import com.example.dancijiyi.data.Word
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 远端词库里的一本书（对应 manifest.json 的 books 数组一项） */
data class OnlineBook(
    val id: String,
    val name: String,
    val version: Int,
    val count: Int,
    val file: String,
    val note: String = ""
)

/**
 * 在线词库目录。
 *
 * 词库不放在自家服务器上，直接读 GitHub 仓库的 online_books 目录：
 *   online_books/manifest.json  -> 有哪些词库、各自版本号与词数
 *   online_books/xxx.json       -> 具体词条数组
 * 走 api.github.com 的 contents 接口（返回 base64 内容），无需额外账号或后端。
 */
object OnlineCatalog {

    private const val OWNER = "duojinlangman"
    private const val REPO = "android-design"
    private const val BRANCH = "master"
    private const val DIR = "online_books"

    /** 拉取词库清单；网络或格式异常时抛出 IOException，由调用方提示用户 */
    fun fetchManifest(): List<OnlineBook> {
        val root = JSONObject(fetchDecoded("manifest.json"))
        val arr = root.optJSONArray("books") ?: JSONArray()
        val list = mutableListOf<OnlineBook>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val file = o.optString("file").trim()
            if (id.isEmpty() || file.isEmpty()) continue
            list.add(
                OnlineBook(
                    id = id,
                    name = o.optString("name", id),
                    version = o.optInt("version", 1),
                    count = o.optInt("count", 0),
                    file = file,
                    note = o.optString("note", "")
                )
            )
        }
        if (list.isEmpty()) throw IOException("词库清单为空")
        return list
    }

    /** 拉取某本书的全部词条，字段缺失或释义为空的行会被跳过 */
    fun fetchWords(book: OnlineBook): List<Word> {
        val arr = JSONArray(fetchDecoded(book.file))
        val list = mutableListOf<Word>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val word = o.optString("word").trim()
            val meaning = o.optString("meaning").trim()
            if (word.isEmpty() || meaning.isEmpty()) continue
            list.add(
                Word(
                    word = word,
                    phonetic = o.optString("phonetic"),
                    meaning = meaning,
                    example = o.optString("example")
                )
            )
        }
        if (list.isEmpty()) throw IOException("《${book.name}》没有解析到词条")
        return list
    }

    /**
     * 取 contents 接口里某个文件的内容：响应里的 content 是 base64，
     * 解码后才是真正的 JSON 文本。
     */
    private fun fetchDecoded(path: String): String {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/contents/$DIR/$path?ref=$BRANCH")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 20_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "dancijiyi-android")
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("更新服务器返回 $code，请稍后重试")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val content = obj.optString("content")
                .replace("\n", "")
                .replace("\r", "")
            if (content.isEmpty()) throw IOException("词库文件内容为空")
            return String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
