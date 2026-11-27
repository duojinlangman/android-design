package com.example.dancijiyi.data.image

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 图形记忆法的配图来源。
 *
 * 找图顺序：
 *   1. 有道词典配图（jsonapi 的 pic_dict.pic[].image）—— 词典官方配图，和词义最贴
 *   2. Wikimedia Commons 搜图 —— 有道没配图时补位，具体名词命中率高
 * 两源都没有就返回 null，由界面提示"这个词暂时没有配图"，绝不硬凑无关图片。
 */
object ImageFinder {

    private const val TAG = "Mnemonic"

    fun findUrl(word: String): String? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        youdaoPic(key)?.let { return it }
        return wikimediaPic(key)
    }

    /** 有道词典配图：一次请求拿到图片直链 */
    private fun youdaoPic(word: String): String? {
        val url = "https://dict.youdao.com/jsonapi?q=" + URLEncoder.encode(word, "UTF-8")
        val body = get(url) ?: return null
        return try {
            val root = JSONObject(body)
            val picDict = root.optJSONObject("pic_dict") ?: return null
            val arr = picDict.optJSONArray("pic") ?: return null
            for (i in 0 until arr.length()) {
                val img = arr.optJSONObject(i)?.optString("image").orEmpty().trim()
                if (img.startsWith("http")) return img
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "youdao parse failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Wikimedia Commons 搜图。
     * 只接受标题里含该词干的结果，避免"adequate"搜到无关人物照这类噪声。
     */
    private fun wikimediaPic(word: String): String? {
        val q = URLEncoder.encode("filetype:bitmap $word", "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrsearch=$q&gsrlimit=5&gsrnamespace=6&prop=imageinfo" +
            "&iiprop=url&iiurlwidth=800&format=json"
        val body = get(url) ?: return null
        return try {
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
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
    }

    private fun get(url: String): String? {
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
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed from ${conn.url.host}: ${e.javaClass.simpleName}")
            null
        } finally {
            conn.disconnect()
        }
    }
}
