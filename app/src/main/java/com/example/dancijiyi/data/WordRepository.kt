package com.example.dancijiyi.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class WordRepository(context: Context) {

    private val helper = AppDb(context)

    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---------------------------------------------------------------- 词库

    fun books(): List<WordBook> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<WordBook>()
        db.rawQuery(
            """
            SELECT b.id, b.name, b.is_system, b.created_at,
                   (SELECT COUNT(*) FROM words w WHERE w.book_id = b.id) AS total,
                   (SELECT COUNT(*) FROM words w WHERE w.book_id = b.id AND w.mastered = 0 AND w.next_review_at <= ?) AS due,
                   (SELECT COUNT(*) FROM words w WHERE w.book_id = b.id AND w.mastered = 1) AS mastered
            FROM books b
            ORDER BY b.is_system DESC, b.id ASC
            """.trimIndent(),
            arrayOf(now.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    WordBook(
                        id = c.getLong(0),
                        name = c.getString(1),
                        isSystem = c.getInt(2) == 1,
                        createdAt = c.getLong(3),
                        wordCount = c.getInt(4),
                        dueCount = c.getInt(5),
                        masteredCount = c.getInt(6)
                    )
                )
            }
        }
        return list
    }

    fun createBook(name: String): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("is_system", 0)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("books", null, cv)
    }

    fun newWordBookId(): Long {
        db.rawQuery("SELECT id FROM books WHERE is_system = 1 LIMIT 1", null).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val cv = ContentValues().apply {
            put("name", "生词本")
            put("is_system", 1)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("books", null, cv)
    }

    fun renameBook(id: Long, name: String) {
        val cv = ContentValues().apply { put("name", name) }
        db.update("books", cv, "id = ? AND is_system = 0", arrayOf(id.toString()))
    }

    fun deleteBook(id: Long) {
        db.beginTransaction()
        try {
            db.delete("logs", "word_id IN (SELECT id FROM words WHERE book_id = ?)", arrayOf(id.toString()))
            db.delete("words", "book_id = ?", arrayOf(id.toString()))
            db.delete("books", "id = ? AND is_system = 0", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------------------------------------------------------- 单词

    fun words(bookId: Long, keyword: String = ""): List<Word> {
        val args: Array<String>
        val where: String
        if (keyword.isBlank()) {
            where = "book_id = ?"
            args = arrayOf(bookId.toString())
        } else {
            where = "book_id = ? AND (word LIKE ? OR meaning LIKE ?)"
            args = arrayOf(bookId.toString(), "%$keyword%", "%$keyword%")
        }
        val list = mutableListOf<Word>()
        db.rawQuery(
            "SELECT * FROM words WHERE $where ORDER BY word COLLATE NOCASE ASC",
            args
        ).use { c ->
            while (c.moveToNext()) list.add(c.toWord())
        }
        return list
    }

    fun allWords(): List<Word> {
        val list = mutableListOf<Word>()
        db.rawQuery("SELECT * FROM words ORDER BY stage ASC, id ASC", null).use { c ->
            while (c.moveToNext()) list.add(c.toWord())
        }
        return list
    }

    /** 按拼写在整个词库中查词（忽略大小写），用于跨词库去重 */
    fun findWordByText(text: String): Word? {
        db.rawQuery(
            "SELECT * FROM words WHERE word = ? COLLATE NOCASE LIMIT 1",
            arrayOf(text.trim())
        ).use { c ->
            if (c.moveToFirst()) return c.toWord()
        }
        return null
    }

    /** 某个单词当前所在的词库名，找不到返回 null */
    fun bookNameOfWord(wordId: Long): String? {
        db.rawQuery(
            "SELECT b.name FROM books b JOIN words w ON w.book_id = b.id WHERE w.id = ?",
            arrayOf(wordId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    fun word(id: Long): Word? {
        db.rawQuery("SELECT * FROM words WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) return c.toWord()
        }
        return null
    }

    /** 到期待复习的单词 */
    fun dueWords(limit: Int = 0): List<Word> {
        val list = mutableListOf<Word>()
        val limitSql = if (limit > 0) " LIMIT $limit" else ""
        db.rawQuery(
            "SELECT * FROM words WHERE mastered = 0 AND next_review_at <= ? " +
                "ORDER BY next_review_at ASC, stage ASC$limitSql",
            arrayOf(System.currentTimeMillis().toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(c.toWord())
            }
        }
        return list
    }

    /** 接下来一段时间内要复习的单词 */
    fun upcomingWords(hours: Long = 24, limit: Int = 0): List<Word> {
        val now = System.currentTimeMillis()
        val end = now + hours * 3600_000L
        val list = mutableListOf<Word>()
        val limitSql = if (limit > 0) " LIMIT $limit" else ""
        db.rawQuery(
            "SELECT * FROM words WHERE mastered = 0 AND next_review_at > ? AND next_review_at <= ? " +
                "ORDER BY next_review_at ASC$limitSql",
            arrayOf(now.toString(), end.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(c.toWord())
            }
        }
        return list
    }

    fun addWord(
        bookId: Long,
        word: String,
        phonetic: String,
        meaning: String,
        example: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        // 全局去重：同一个拼写只保留一条记录，避免复习队列里出现重复卡片
        if (findWordByText(word) != null) return -1L
        val cv = ContentValues().apply {
            put("book_id", bookId)
            put("word", word.trim())
            put("phonetic", normalizePhonetic(phonetic))
            put("meaning", meaning.trim())
            put("example", example.trim())
            put("stage", 0)
            put("next_review_at", now)
            put("created_at", now)
        }
        return db.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** 批量导入，返回成功导入的条数（已存在同名单词的词会被跳过） */
    fun addWords(bookId: Long, words: List<Word>): Int {
        var count = 0
        val existing = mutableSetOf<String>()
        db.rawQuery("SELECT word FROM words", null).use { c ->
            while (c.moveToNext()) existing.add(c.getString(0).lowercase())
        }
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            words.forEach { w ->
                if (w.word.isBlank() || w.meaning.isBlank()) return@forEach
                val key = w.word.trim().lowercase()
                if (!existing.add(key)) return@forEach
                val cv = ContentValues().apply {
                    put("book_id", bookId)
                    put("word", w.word.trim())
                    put("phonetic", normalizePhonetic(w.phonetic))
                    put("meaning", w.meaning.trim())
                    put("example", w.example.trim())
                    put("stage", 0)
                    put("next_review_at", now)
                    put("created_at", now)
                }
                val id = db.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (id > 0) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun deleteWord(id: Long) {
        db.delete("logs", "word_id = ?", arrayOf(id.toString()))
        db.delete("words", "id = ?", arrayOf(id.toString()))
    }

    fun updateWord(id: Long, word: String, phonetic: String, meaning: String) {
        val cv = ContentValues().apply {
            put("word", word.trim())
            put("phonetic", normalizePhonetic(phonetic))
            put("meaning", meaning.trim())
        }
        db.update("words", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setStarred(id: Long, starred: Boolean) {
        val cv = ContentValues().apply { put("starred", if (starred) 1 else 0) }
        db.update("words", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setMastered(id: Long, mastered: Boolean) {
        val cv = ContentValues().apply {
            put("mastered", if (mastered) 1 else 0)
            put("stage", if (mastered) 9 else 0)
            put("next_review_at", if (mastered) System.currentTimeMillis() + ReviewPlan.MASTER_INTERVAL
            else System.currentTimeMillis())
        }
        db.update("words", cv, "id = ?", arrayOf(id.toString()))
    }

    fun resetProgress(id: Long) {
        val cv = ContentValues().apply {
            put("stage", 0)
            put("mastered", 0)
            put("correct_count", 0)
            put("wrong_count", 0)
            put("last_review_at", 0)
            put("next_review_at", System.currentTimeMillis())
        }
        db.update("words", cv, "id = ?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- 复习调度

    /** 记录一次复习，按艾宾浩斯阶段推进；返回更新后的单词 */
    fun recordReview(wordId: Long, grade: ReviewGrade, mode: String): Word? {
        val current = word(wordId) ?: return null
        val (nextStage, nextTime) = ReviewPlan.nextTimeFor(current.stage, grade)
        val isMastered = nextStage == Int.MAX_VALUE
        val cv = ContentValues().apply {
            put("stage", if (isMastered) 9 else nextStage)
            put("next_review_at", nextTime)
            put("last_review_at", System.currentTimeMillis())
            put("mastered", if (isMastered) 1 else 0)
            if (grade == ReviewGrade.FORGOT) {
                put("wrong_count", current.wrongCount + 1)
            } else {
                put("correct_count", current.correctCount + 1)
            }
        }
        db.update("words", cv, "id = ?", arrayOf(wordId.toString()))

        val log = ContentValues().apply {
            put("word_id", wordId)
            put("ts", System.currentTimeMillis())
            put("correct", if (grade == ReviewGrade.FORGOT) 0 else 1)
            put("mode", mode)
        }
        db.insert("logs", null, log)
        return word(wordId)
    }

    // ---------------------------------------------------------------- 统计数据

    fun dashboard(): DashboardStats {
        val now = System.currentTimeMillis()
        val todayStart = dayStart(now)

        val total = count("SELECT COUNT(*) FROM words")
        val mastered = count("SELECT COUNT(*) FROM words WHERE mastered = 1")
        val learning = count("SELECT COUNT(*) FROM words WHERE mastered = 0 AND stage > 0")
        val newWords = count("SELECT COUNT(*) FROM words WHERE mastered = 0 AND stage = 0")
        val due = count("SELECT COUNT(*) FROM words WHERE mastered = 0 AND next_review_at <= $now")
        val todayLearned = count("SELECT COUNT(*) FROM words WHERE created_at >= $todayStart")
        val todayReviewed = count("SELECT COUNT(*) FROM logs WHERE ts >= $todayStart")
        val todayCorrect = count("SELECT COUNT(*) FROM logs WHERE ts >= $todayStart AND correct = 1")
        val totalReviews = count("SELECT COUNT(*) FROM logs")
        val totalCorrect = count("SELECT COUNT(*) FROM logs WHERE correct = 1")

        val last7 = mutableListOf<DayPoint>()
        for (i in 6 downTo 0) {
            val start = dayStart(now - i * DAY_MS)
            val end = start + DAY_MS
            val reviewed = count("SELECT COUNT(*) FROM logs WHERE ts >= $start AND ts < $end")
            val learned = count("SELECT COUNT(*) FROM words WHERE created_at >= $start AND created_at < $end")
            val correct = count("SELECT COUNT(*) FROM logs WHERE ts >= $start AND ts < $end AND correct = 1")
            last7.add(DayPoint(start, reviewed, learned, correct, weekdayLabel(start)))
        }

        return DashboardStats(
            totalWords = total,
            masteredWords = mastered,
            learningWords = learning,
            newWords = newWords,
            dueNow = due,
            todayLearned = todayLearned,
            todayReviewed = todayReviewed,
            todayCorrect = todayCorrect,
            totalReviews = totalReviews,
            totalCorrect = totalCorrect,
            streakDays = streakDays(now),
            last7 = last7
        )
    }

    private fun streakDays(now: Long): Int {
        var streak = 0
        var cursorDay = dayStart(now)
        for (i in 0..365) {
            val start = cursorDay - i * DAY_MS
            val end = start + DAY_MS
            val active = count(
                "SELECT (SELECT COUNT(*) FROM logs WHERE ts >= $start AND ts < $end) + " +
                    "(SELECT COUNT(*) FROM words WHERE created_at >= $start AND created_at < $end)"
            )
            if (active > 0) {
                streak++
            } else if (i > 0) {
                break
            } else {
                // 今天还没学习，不打断昨天开始的连续记录
                continue
            }
        }
        return streak
    }

    private fun count(sql: String): Int {
        db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) return c.getInt(0) }
        return 0
    }

    // ---------------------------------------------------------------- 测试

    fun quizQuestions(count: Int, preferDue: Boolean = true): List<QuizQuestion> {
        val all = allWords().filter { it.meaning.isNotBlank() }
        if (all.size < 2) return emptyList()

        val candidates = mutableListOf<Word>()
        if (preferDue) candidates.addAll(dueWords().filter { it.meaning.isNotBlank() })
        if (candidates.size < count) {
            val others = all.filter { w -> candidates.none { it.id == w.id } }.shuffled()
            candidates.addAll(others.take(count - candidates.size))
        }

        return candidates.shuffled().take(count).mapNotNull { w ->
            val distractors = all.filter { it.id != w.id && it.meaning != w.meaning }
                .map { it.meaning }
                .distinct()
                .shuffled()
                .take(3)
            if (distractors.size < 2) return@mapNotNull null
            val options = (distractors + w.meaning).shuffled()
            QuizQuestion(w, options, options.indexOf(w.meaning))
        }
    }

    // ---------------------------------------------------------------- 工具

    private fun Cursor.toWord(): Word = Word(
        id = getLong(getColumnIndexOrThrow("id")),
        bookId = getLong(getColumnIndexOrThrow("book_id")),
        word = getString(getColumnIndexOrThrow("word")),
        phonetic = getString(getColumnIndexOrThrow("phonetic")),
        meaning = getString(getColumnIndexOrThrow("meaning")),
        example = getString(getColumnIndexOrThrow("example")),
        stage = getInt(getColumnIndexOrThrow("stage")),
        nextReviewAt = getLong(getColumnIndexOrThrow("next_review_at")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        lastReviewAt = getLong(getColumnIndexOrThrow("last_review_at")),
        correctCount = getInt(getColumnIndexOrThrow("correct_count")),
        wrongCount = getInt(getColumnIndexOrThrow("wrong_count")),
        mastered = getInt(getColumnIndexOrThrow("mastered")) == 1,
        starred = getInt(getColumnIndexOrThrow("starred")) == 1
    )

    companion object {
        private const val DAY_MS = 24L * 3600_000L

        fun dayStart(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun weekdayLabel(dayStart: Long): String {
            val fmt = SimpleDateFormat("M/d", Locale.CHINA)
            return fmt.format(Date(dayStart))
        }

        /** 音标统一用 /.../ 包裹 */
        fun normalizePhonetic(raw: String): String {
            val p = raw.trim()
            if (p.isEmpty()) return ""
            if (p.startsWith("/") && p.endsWith("/")) return p
            if (p.startsWith("[") && p.endsWith("]")) return "/${p.trim('[', ']')}/"
            return "/$p/"
        }

        fun randomOf(list: List<Word>, n: Int): List<Word> =
            if (list.size <= n) list else list.shuffled(Random(System.nanoTime())).take(n)
    }
}
