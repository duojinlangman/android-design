package com.example.dancijiyi.data.audio

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 免费在线词典接口（Free Dictionary API，免注册、免 key）。
 *
 * 一个单词返回 JSON 数组，每一项里的 phonetics[] 含有 text（音标）和 audio（mp3 直链）。
 * 同源通常会给 -us.mp3（美式）和 -uk.mp3（英式），这里优先美式，符合本 App 选词习惯。
 */
object PronunciationApi {

    private const val ENDPOINT = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    /**
     * 有道词典发音直链：type=2 美音、type=1 英音。
     * 直接返回 audio/mpeg，不用先查 JSON，国内访问稳定，是最省事的主音源。
     */
    fun youdaoAudioUrl(word: String): String {
        val key = word.trim().lowercase().replace(" ", "%20")
        return "https://dict.youdao.com/dictvoice?audio=$key&type=2"
    }

    /**
     * 查某个单词的发音音频直链。
     * 查不到（生僻词、拼写不规范、断网）时返回 null，由调用方退回系统 TTS，
     * 不抛异常，避免发音失败把学习流程打断。
     */
    fun lookupAudioUrl(word: String): String? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        val url = URL(ENDPOINT + URLEncoder.encode(key, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "dancijiyi-android")
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            var fallback: String? = null
            for (i in 0 until arr.length()) {
                val phonetics = arr.optJSONObject(i)?.optJSONArray("phonetics") ?: continue
                for (j in 0 until phonetics.length()) {
                    val audio = phonetics.optJSONObject(j)?.optString("audio").orEmpty().trim()
                    if (audio.isEmpty() || !audio.startsWith("http")) continue
                    // 美式优先，其余作为备选
                    if (audio.contains("-us.mp3")) return audio
                    if (fallback == null) fallback = audio
                }
            }
            return fallback
        } catch (e: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 顺带取一份接口音标，用于词库里音标缺失的单词自动补全 */
    fun lookupPhonetic(word: String): String? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        val conn = (URL(ENDPOINT + URLEncoder.encode(key, "UTF-8")).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "dancijiyi-android")
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val obj: JSONObject = arr.optJSONObject(i) ?: continue
                val top = obj.optString("phonetic").trim()
                if (top.isNotEmpty()) return top
                val phonetics = obj.optJSONArray("phonetics") ?: continue
                for (j in 0 until phonetics.length()) {
                    val text = phonetics.optJSONObject(j)?.optString("text").orEmpty().trim()
                    if (text.isNotEmpty()) return text
                }
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }
}
