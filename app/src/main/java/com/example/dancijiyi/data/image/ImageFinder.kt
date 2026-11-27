package com.example.dancijiyi.data.image

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 找图结果。
 *
 * 必须把「查询成功但确实没有图」和「网络没通、压根没查成」分开：
 * 前者可以记一条 .none 标记以后不再请求，后者绝不能记，
 * 否则一次断网就会让某个词永久显示"没有配图"。
 */
sealed class PictureLookup {
    data class Found(val url: String) : PictureLookup()
    object NotFound : PictureLookup()
    object Offline : PictureLookup()
}

/**
 * 图形记忆法的配图来源。
 *
 * 找图顺序：
 *   1. 有道词典配图（jsonapi 的 pic_dict.pic[].image）—— 词典官方配图，和词义最贴
 *   2. Wikimedia Commons 搜图 —— 有道没配图时补位，具体名词命中率高
 * 两源都查过且都没有才返回 NotFound，绝不硬凑无关图片。
 */
object ImageFinder {

    private const val TAG = "Mnemonic"

    fun find(word: String): PictureLookup {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return PictureLookup.NotFound

        var anyQuerySucceeded = false

        when (val a = youdaoPic(key)) {
            is Source -> {
                if (a.networkOk) anyQuerySucceeded = true
                a.url?.let { return PictureLookup.Found(it) }
            }
            else -> Unit
        }
        when (val b = wikimediaPic(key)) {
            is Source -> {
                if (b.networkOk) anyQuerySucceeded = true
                b.url?.let { return PictureLookup.Found(it) }
            }
            else -> Unit
        }
        return if (anyQuerySucceeded) PictureLookup.NotFound else PictureLookup.Offline
    }

    /** 单个图源的查询结果：url 为空时用 networkOk 区分"没图"和"没连上" */
    private class Source(val url: String?, val networkOk: Boolean)

    /** 有道词典配图：一次请求拿到图片直链 */
    private fun youdaoPic(word: String): Source {
        val url = "https://dict.youdao.com/jsonapi?q=" + URLEncoder.encode(word, "UTF-8")
        val fetch = get(url)
        if (!fetch.ok) return Source(null, false)
        val body = fetch.body ?: return Source(null, true)
        val found = try {
            val root = JSONObject(body)
            val picDict = root.optJSONObject("pic_dict") ?: return Source(null, true)
            val arr = picDict.optJSONArray("pic") ?: return Source(null, true)
            var hit: String? = null
            for (i in 0 until arr.length()) {
                val img = arr.optJSONObject(i)?.optString("image").orEmpty().trim()
                if (img.startsWith("http")) {
                    hit = img
                    break
                }
            }
            hit
        } catch (e: Exception) {
            Log.w(TAG, "youdao parse failed: ${e.javaClass.simpleName}")
            null
        }
        return Source(found, true)
    }

    /**
     * Wikimedia Commons 搜图。
     * 只接受标题里含该词干的结果，避免"adequate"搜到无关人物照这类噪声。
     */
    private fun wikimediaPic(word: String): Source {
        val q = URLEncoder.encode("filetype:bitmap $word", "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrsearch=$q&gsrlimit=5&gsrnamespace=6&prop=imageinfo" +
            "&iiprop=url&iiurlwidth=800&format=json"
        val fetch = get(url)
        if (!fetch.ok) return Source(null, false)
        val body = fetch.body ?: return Source(null, true)
        val found = try {
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                ?: return Source(null, true)
            val stem = word.take(5).lowercase()
            var best: String? = null
            val it = pages.keys()
            while (it.hasNext()) {
                val page = pages.optJSONObject(it.next()) ?: continue
                val title = page.optString("title").lowercase()
                if (!title.contains(stem)) continue
                val info = page.optJSONArray("imageinfo") ?: continue
                val thumb = info.optJSONObject(0)?.optString("thumburl").orEmpty().trim()
                if (thumb.startsWith("http")) {
                    best = thumb
                    break
                }
            }
            best
        } catch (e: Exception) {
            Log.w(TAG, "wikimedia parse failed: ${e.javaClass.simpleName}")
            null
        }
        return Source(found, true)
    }

    private class Fetch(val body: String?, val ok: Boolean)

    private fun get(url: String): Fetch {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "dancijiyi-android/1.0 (vocabulary app)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "http ${conn.responseCode} from ${conn.url.host}")
                Fetch(null, false)
            } else {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Fetch(text, true)
            }
        } catch (e: Exception) {
            // 解析失败、DNS 解析不了、超时都落在这里，一律当作"没连上"
            Log.w(TAG, "request failed from ${conn.url.host}: ${e.javaClass.simpleName} ${e.message}")
            Fetch(null, false)
        } finally {
            conn.disconnect()
        }
    }
}
