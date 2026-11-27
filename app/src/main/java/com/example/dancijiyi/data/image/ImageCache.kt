package com.example.dancijiyi.data.image

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 配图本地缓存。
 *
 * 每个词的图存在 cacheDir/mnemonics/<word>.img，同一张图只联网下一次，
 * 之后离线也能看。同时记一个 <word>.none 空标记，避免反复为没图的词发请求。
 */
object ImageCache {

    private const val TAG = "Mnemonic"
    private const val DIR_NAME = "mnemonics"

    fun cachedFile(context: Context, word: String): File? {
        val f = File(dir(context), fileName(word))
        return if (f.exists() && f.length() > 2000) f else null
    }

    /** 之前查过且确认没有配图（避免每次点提示都重新联网） */
    fun knownMissing(context: Context, word: String): Boolean =
        File(dir(context), fileName(word) + ".none").exists()

    fun markMissing(context: Context, word: String) {
        try {
            File(dir(context), fileName(word) + ".none").createNewFile()
        } catch (e: Exception) {
            // 标记写失败不影响主流程
        }
    }

    /** 有缓存直接用；没有则联网找图并下载。失败返回 null */
    fun ensure(context: Context, word: String, forceRefresh: Boolean = false): File? {
        if (!forceRefresh) cachedFile(context, word)?.let { return it }
        if (!forceRefresh && knownMissing(context, word)) return null

        val remote = ImageFinder.findUrl(word)
        if (remote == null) {
            markMissing(context, word)
            Log.w(TAG, "no picture for $word")
            return null
        }
        val target = File(dir(context), fileName(word))
        val tmp = File(target.absolutePath + ".part")
        val conn = (URL(remote).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) dancijiyi")
        }
        try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "download ${conn.responseCode} from ${conn.url.host}")
                return null
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 16 * 1024) }
            }
            if (!looksLikeImage(tmp)) {
                Log.w(TAG, "not an image (${tmp.length()}B) from ${conn.url.host}")
                tmp.delete()
                markMissing(context, word)
                return null
            }
            if (!tmp.renameTo(target)) {
                if (target.exists() && target.length() > 2000) {
                    tmp.delete()
                    return target
                }
                tmp.delete()
                return null
            }
            // 拿到图了，清掉之前的"无图"标记
            File(dir(context), fileName(word) + ".none").delete()
            return target
        } catch (e: Exception) {
            Log.w(TAG, "download failed: ${e.javaClass.simpleName} ${e.message}")
            tmp.delete()
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 校验文件头：JPEG(FFD8) / PNG(89504E47) / WebP(RIFF....WEBP) / GIF(GIF8) */
    private fun looksLikeImage(file: File): Boolean {
        if (file.length() < 2000) return false
        val head = ByteArray(12)
        val n = FileInputStream(file).use { it.read(head) }
        if (n < 4) return false
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xD8) return true
        if (b0 == 0x89 && b1 == 0x50) return true
        if (b0 == 0x47 && b1 == 0x49 && (head[2].toInt() and 0xFF) == 0x46) return true
        if (n >= 12 && b0 == 0x52 && b1 == 0x49) return true
        return false
    }

    fun clear(context: Context): Long {
        var freed = 0L
        dir(context).listFiles()?.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    fun usage(context: Context): Pair<Int, Long> {
        val files = dir(context).listFiles() ?: return 0 to 0L
        var count = 0
        var bytes = 0L
        files.forEach {
            if (!it.name.endsWith(".none") && !it.name.endsWith(".part")) count++
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
        return if (safe.isEmpty()) "unknown.img" else "$safe.img"
    }
}
