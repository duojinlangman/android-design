package com.example.dancijiyi.data.audio

import android.content.Context

/** 发音相关偏好：是否进入新单词自动朗读一次 */
object PronPrefs {

    private const val SP_NAME = "settings"
    private const val KEY_AUTO = "auto_pronounce"

    fun isAutoEnabled(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)

    /** 切换并返回切换后的状态 */
    fun toggleAuto(context: Context): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val next = !sp.getBoolean(KEY_AUTO, true)
        sp.edit().putBoolean(KEY_AUTO, next).apply()
        return next
    }

    fun setAuto(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO, enabled).apply()
    }
}
