package com.example.dancijiyi.data

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 词库导入：支持 .txt / .csv 文本，每行一个单词。
 * 每行可以是：
 *   word,释义
 *   word,音标,释义
 *   word<Tab>释义
 *   word 释义（英文后接中文）
 *   word|音标|释义
 */
object WordImporter {

    data class Result(val parsed: Int, val added: Int)

    fun parse(text: String): List<Word> {
        val cleaned = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<Word>()
        var skippedHeader = false

        for (line in lines) {
            if (!skippedHeader && isHeader(line)) {
                skippedHeader = true
                continue
            }
            skippedHeader = true
            val fields = splitLine(line)
            if (fields.isEmpty()) continue

            val word = fields.first().trim()
            if (!word.matches(Regex("^[A-Za-z][A-Za-z' -]*$"))) continue

            var phonetic = ""
            var meaning = ""
            if (fields.size >= 3) {
                if (looksPhonetic(fields[1])) {
                    phonetic = fields[1]
                    meaning = fields.drop(2).joinToString("；")
                } else {
                    meaning = fields.drop(1).joinToString("；")
                }
            } else if (fields.size == 2) {
                if (looksPhonetic(fields[1]) && !containsChinese(fields[1])) {
                    phonetic = fields[1]
                } else {
                    meaning = fields[1]
                }
            }
            if (meaning.isBlank()) continue
            result.add(Word(word = word, phonetic = phonetic, meaning = meaning))
        }
        return result
    }

    fun importInto(repo: WordRepository, bookId: Long, text: String): Result {
        val words = parse(text)
        val added = repo.addWords(bookId, words)
        return Result(words.size, added)
    }

    /** 读取文本文件，UTF-8 失败时回退 GB18030（Excel 导出的 CSV 常见编码） */
    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        return decode(bytes)
    }

    fun decode(bytes: ByteArray): String {
        return try {
            val text = String(bytes, Charset.forName("UTF-8"))
            if (text.contains('\uFFFD')) String(bytes, Charset.forName("GB18030")) else text
        } catch (e: Exception) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    private fun splitLine(line: String): List<String> {
        val seps = listOf("\t", "|", "，", ",", "；", ";")
        for (sep in seps) {
            if (line.contains(sep)) {
                return line.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        val firstChinese = line.indexOfFirst { it.code in 0x4E00..0x9FFF }
        if (firstChinese > 0) {
            return listOf(line.substring(0, firstChinese).trim(), line.substring(firstChinese).trim())
        }
        return listOf(line.trim())
    }

    private fun looksPhonetic(s: String): Boolean {
        if (s.contains('/') || s.contains('[') || s.contains('ˈ') || s.contains('ˌ') || s.contains('ː')) return true
        return false
    }

    private fun containsChinese(s: String): Boolean =
        s.any { it.code in 0x4E00..0x9FFF }

    private fun isHeader(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("word,") || lower.startsWith("word\t") ||
            (line.contains("单词") && line.contains("释义"))
    }
}
