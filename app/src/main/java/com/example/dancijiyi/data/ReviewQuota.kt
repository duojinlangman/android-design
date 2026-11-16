package com.example.dancijiyi.data

import android.content.Context

/**
 * 每天复习多少个单词（用户可选 10 / 20 / 50 / 100，默认 50）
 * 存在独立 SharedPreferences 里，和词库数据库解耦，改数值不会影响单词数据。
 */
object ReviewQuota {

    private const val PREF_NAME = "settings"
    private const val KEY_BATCH_SIZE = "review_batch_size"
    const val DEFAULT = 50

    val options = intArrayOf(10, 20, 50, 100)

    fun get(context: Context): Int {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = sp.getInt(KEY_BATCH_SIZE, DEFAULT)
        return if (value in options) value else DEFAULT
    }

    fun set(context: Context, size: Int) {
        if (size !in options) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BATCH_SIZE, size)
            .apply()
    }

    fun label(context: Context): String = "${get(context)} 个"
}
