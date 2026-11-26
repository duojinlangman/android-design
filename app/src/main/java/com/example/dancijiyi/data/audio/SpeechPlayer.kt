package com.example.dancijiyi.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** 播放状态，界面据此切换图标 */
enum class PronState { LOADING, PLAYING, DONE }

/**
 * 单词发音播放器，三级兜底：
 *   1. 本地缓存命中 → 直接播 mp3
 *   2. 没缓存 → 联网查 mp3 直链、下载入缓存再播
 *   3. 没有音频 / 断网 / 播放失败 → 系统 TTS 朗读（美式发音）
 *
 * 每次 play() 都会让上一次的请求失效（token 机制），快速翻词不会串音。
 */
class SpeechPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingTts: String? = null
    private var ttsCallback: ((PronState) -> Unit)? = null
    private var token = 0

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) return@TextToSpeech
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { ttsCallback?.invoke(PronState.PLAYING) }
                }

                override fun onDone(utteranceId: String?) {
                    main.post { ttsCallback?.invoke(PronState.DONE) }
                }

                override fun onError(utteranceId: String?) {
                    main.post { ttsCallback?.invoke(PronState.DONE) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { ttsCallback?.invoke(PronState.DONE) }
                }
            })
            main.post {
                pendingTts?.let { w ->
                    pendingTts = null
                    val cb = ttsCallback ?: {}
                    speakNow(w, cb)
                }
            }
        }
    }

    /** 播放一个单词；onState 在状态变化时回调（已在主线程） */
    fun play(word: String, onState: (PronState) -> Unit = {}) {
        val w = word.trim()
        if (w.isEmpty()) return
        token++
        val myToken = token
        releasePlayer()
        ttsCallback = onState

        AudioCache.cachedFile(appContext, w)?.let {
            playFile(it, w, onState)
            return
        }

        onState(PronState.LOADING)
        io.execute {
            val file = try {
                AudioCache.ensure(appContext, w)
            } catch (e: Exception) {
                null
            }
            main.post {
                if (myToken != token) return@post // 已经切到别的单词，丢弃这次结果
                if (file != null && file.length() > 0L) {
                    playFile(file, w, onState)
                } else {
                    speakNow(w, onState)
                }
            }
        }
    }

    /** 停止当前播放（翻页、退出时调用），不销毁播放器 */
    fun stop() {
        token++
        releasePlayer()
        tts?.stop()
        pendingTts = null
    }

    /** 彻底释放，Activity onDestroy 时调用 */
    fun shutdown() {
        stop()
        ttsCallback = null
        io.shutdownNow()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun playFile(file: File, word: String, onState: (PronState) -> Unit) {
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                it.start()
                onState(PronState.PLAYING)
            }
            mp.setOnCompletionListener {
                releasePlayer()
                onState(PronState.DONE)
            }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                speakNow(word, onState)
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            speakNow(word, onState)
        }
    }

    private fun speakNow(word: String, onState: (PronState) -> Unit) {
        val engine = tts
        if (engine == null || !ttsReady) {
            // TTS 引擎尚未就绪，挂起一次，初始化完成后补播
            pendingTts = word
            ttsCallback = onState
            onState(PronState.DONE)
            return
        }
        ttsCallback = onState
        onState(PronState.PLAYING)
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "di-$word")
    }

    private fun releasePlayer() {
        player?.let {
            try {
                it.reset()
                it.release()
            } catch (e: Exception) {
                // 已释放或状态异常，忽略
            }
        }
        player = null
    }
}
