package com.example.dancijiyi.data.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 发音音频本地缓存。
 *
 * 每个单词的 mp3 存在 cacheDir/prons/<word>.mp3，同一个词只听一次网络，
 * 之后（含断网、离线）都能直接播放。文件名做了字符清洗，避免路径穿越。
 *
 * 音源顺序：
 *   1. 有道发音直链（type=2 美音，直接返回 mp3，国内可直连）
 *   2. Free Dictionary API 返回的 mp3 直链（备选，多给一份 -us.mp3）
 */
object AudioCache {

    private const val TAG = "Pron"
    private const val DIR_NAME = "prons"

    /** 命中缓存返回文件，否则 null */
    fun cachedFile(context: Context, word: String): File? {
        val f = File(dir(context), fileName(word))
        return if (f.exists() && f.length() > 0) f else null
    }

    /** 有缓存直接用；没有则联网下载。失败返回 null（调用方退回 TTS） */
    fun ensure(context: Context, word: String): File? {
        cachedFile(context, word)?.let { return it }

        val target = File(dir(context), fileName(word))

        // 主音源：有道
        download(PronunciationApi.youdaoAudioUrl(word), target)?.let { return it }

        // 备选音源：Free Dictionary API
        val remote = PronunciationApi.lookupAudioUrl(word)
        if (remote == null) {
            Log.w(TAG, "no audio url for $word")
            return null
        }
        return download(remote, target)
    }

    private fun download(url: String, target: File): File? {
        val tmp = File(target.absolutePath + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) dancijiyi")
        }
        try {
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "download $code from ${hostOf(url)}")
                return null
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 8 * 1024) }
            }
            if (!looksLikeAudio(tmp)) {
                Log.w(TAG, "not audio (${tmp.length()}B) from ${hostOf(url)}")
                tmp.delete()
                return null
            }
            if (!tmp.renameTo(target)) {
                // 目标被占用（正在播同一个词）时复用已有文件
                if (target.exists() && target.length() > 0) {
                    tmp.delete()
                    return target
                }
                tmp.delete()
                return null
            }
            return target
        } catch (e: Exception) {
            Log.w(TAG, "download failed from ${hostOf(url)}: ${e.javaClass.simpleName} ${e.message}")
            tmp.delete()
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 粗校验：够长且开头是 MP3 文件头（ID3 或帧同步 0xFFEx），避免把错误页当音频播 */
    private fun looksLikeAudio(file: File): Boolean {
        if (file.length() < 1200) return false
        val head = ByteArray(3)
        FileInputStream(file).use { it.read(head) }
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        val b2 = head[2].toInt() and 0xFF
        if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return true // "ID3"
        return b0 == 0xFF && (b1 and 0xE0) == 0xE0
    }

    private fun hostOf(url: String): String = try {
        URL(url).host
    } catch (e: Exception) {
        url
    }

    /** 清空缓存，返回释放的字节数 */
    fun clear(context: Context): Long {
        val dir = dir(context)
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            freed += file.length()
            file.delete()
        }
        return freed
    }

    /** 已缓存的单词数 + 占用空间，供设置页展示 */
    fun usage(context: Context): Pair<Int, Long> {
        val files = dir(context).listFiles() ?: return 0 to 0L
        var bytes = 0L
        var count = 0
        files.forEach {
            if (it.name.endsWith(".mp3")) count++
            bytes += it.length()
        }
        return count to bytes
    }

    private fun dir(context: Context): File {
        val d = File(context.cacheDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileName(word: String): String {
        val safe = word.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return if (safe.isEmpty()) "unknown.mp3" else "$safe.mp3"
    }
}
