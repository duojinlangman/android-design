package com.example.dancijiyi.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                is_system INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE words(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL,
                word TEXT NOT NULL,
                phonetic TEXT NOT NULL DEFAULT '',
                meaning TEXT NOT NULL,
                example TEXT NOT NULL DEFAULT '',
                stage INTEGER NOT NULL DEFAULT 0,
                next_review_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                last_review_at INTEGER NOT NULL DEFAULT 0,
                correct_count INTEGER NOT NULL DEFAULT 0,
                wrong_count INTEGER NOT NULL DEFAULT 0,
                mastered INTEGER NOT NULL DEFAULT 0,
                starred INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_word_book ON words(book_id, word)")
        db.execSQL("CREATE INDEX idx_word_next ON words(next_review_at)")
        db.execSQL(
            """
            CREATE TABLE logs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                mode TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_log_ts ON logs(ts)")

        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 数据结构有变动时重建，词库由内置词库 + 用户生词本重新生成
        db.execSQL("DROP TABLE IF EXISTS logs")
        db.execSQL("DROP TABLE IF EXISTS words")
        db.execSQL("DROP TABLE IF EXISTS books")
        onCreate(db)
    }

    /** 首次安装：创建生词本与两个内置词库 */
    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        // 生词本（系统词库，id 固定为 1）
        db.execSQL(
            "INSERT INTO books(name, is_system, created_at) VALUES(?,1,?)",
            arrayOf<Any>("生词本", now)
        )
        BuiltInWords.books.forEachIndexed { index, (name, _) ->
            val values = android.content.ContentValues().apply {
                put("name", name)
                put("is_system", 0)
                put("created_at", now)
            }
            val bookId = db.insert("books", null, values)
            BuiltInWords.wordsOf(index).forEach { w ->
                val cv = android.content.ContentValues().apply {
                    put("book_id", bookId)
                    put("word", w.word)
                    put("phonetic", w.phonetic)
                    put("meaning", w.meaning)
                    put("example", w.example)
                    put("stage", 0)
                    // 内置词库直接进入待复习状态（时间为 0 表示"从未安排，立即可复习"）
                    put("next_review_at", 0L)
                    // 内置词库不算"今日新增"，置 0 以便统计只看用户自己的学习行为
                    put("created_at", 0L)
                }
                db.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    companion object {
        const val DB_NAME = "dancijiyi.db"
        const val VERSION = 1
    }
}
