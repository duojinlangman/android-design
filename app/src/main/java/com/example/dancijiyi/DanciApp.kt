package com.example.dancijiyi

import android.app.Application
import com.example.dancijiyi.data.WordRepository

class DanciApp : Application() {

    lateinit var repo: WordRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = WordRepository(this)
        // 启动时先把数据库建好并写入内置词库，避免首屏读到半初始化的数据
        repo.books()
    }

    companion object {
        private lateinit var instance: DanciApp
        val repository: WordRepository get() = instance.repo
    }
}
