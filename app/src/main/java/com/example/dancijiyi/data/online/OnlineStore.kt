package com.example.dancijiyi.data.online

import android.content.Context

/**
 * 在线词库的本地记录：
 *  - 每本书已下载的版本号，用来判断“有没有新词”
 *  - 每本书对应的本地词库 id，更新时直接往同一个词库里加词
 *  - 上次检查更新的时间
 */
object OnlineStore {

    private const val PREF = "online_books"
    private const val KEY_LAST_CHECK = "last_check_at"

    private fun sp(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun version(context: Context, id: String): Int = sp(context).getInt("v_$id", 0)

    fun setVersion(context: Context, id: String, version: Int) {
        sp(context).edit().putInt("v_$id", version).apply()
    }

    fun bookId(context: Context, id: String): Long = sp(context).getLong("b_$id", -1L)

    fun setBookId(context: Context, id: String, bookId: Long) {
        sp(context).edit().putLong("b_$id", bookId).apply()
    }

    fun lastCheckAt(context: Context): Long = sp(context).getLong(KEY_LAST_CHECK, 0L)

    fun setLastCheckAt(context: Context, time: Long) {
        sp(context).edit().putLong(KEY_LAST_CHECK, time).apply()
    }
}
