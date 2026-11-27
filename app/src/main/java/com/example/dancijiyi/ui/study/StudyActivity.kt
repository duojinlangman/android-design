package com.example.dancijiyi.ui.study

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.data.ReviewGrade
import com.example.dancijiyi.data.Word
import com.example.dancijiyi.data.audio.PronPrefs
import com.example.dancijiyi.data.audio.PronState
import com.example.dancijiyi.data.audio.SpeechPlayer
import com.example.dancijiyi.data.image.ImageCache
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.Executors

/** 卡片式复习：看单词 → 揭晓释义 → 自评（忘了 / 有点模糊 / 记住了） */
class StudyActivity : AppCompatActivity() {

    private val repo get() = DanciApp.repository

    private lateinit var words: MutableList<Word>
    private var index = 0
    private var knownCount = 0
    private var answeredCount = 0
    private var revealed = false
    private var answered = false

    private lateinit var cardWord: LinearLayout
    private lateinit var tvCounter: TextView
    private lateinit var tvLiveAccuracy: TextView
    private lateinit var tvStagePill: TextView
    private lateinit var tvWord: TextView
    private lateinit var tvPhonetic: TextView
    private lateinit var tvMeaning: TextView
    private lateinit var tvExample: TextView
    private lateinit var tvTapHint: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnForgot: MaterialButton
    private lateinit var btnVague: MaterialButton
    private lateinit var btnKnown: MaterialButton
    private lateinit var btnSpeak: ImageView
    private lateinit var btnHint: TextView
    private lateinit var cardHint: MaterialCardView
    private lateinit var ivMnemonic: ImageView
    private lateinit var tvHintLoading: TextView
    private lateinit var tvHintState: TextView

    private lateinit var speaker: SpeechPlayer

    /** 配图后台线程；hintToken 用于丢弃换词后迟到的结果，避免图文串位 */
    private val imageIo = Executors.newSingleThreadExecutor()
    private var hintToken = 0
    private var hintWord: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        speaker = SpeechPlayer(this)

        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: longArrayOf()
        words = mutableListOf<Word>()
        for (id in ids) {
            repo.word(id)?.let { words.add(it) }
        }
        if (words.isEmpty()) {
            finish()
            return
        }

        cardWord = findViewById(R.id.cardWord)
        tvCounter = findViewById(R.id.tvCounter)
        tvLiveAccuracy = findViewById(R.id.tvLiveAccuracy)
        tvStagePill = findViewById(R.id.tvStagePill)
        tvWord = findViewById(R.id.tvWord)
        tvPhonetic = findViewById(R.id.tvPhonetic)
        tvMeaning = findViewById(R.id.tvMeaning)
        tvExample = findViewById(R.id.tvExample)
        tvTapHint = findViewById(R.id.tvTapHint)
        progress = findViewById(R.id.progressStudy)
        btnForgot = findViewById(R.id.btnForgot)
        btnVague = findViewById(R.id.btnVague)
        btnKnown = findViewById(R.id.btnKnown)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnHint = findViewById(R.id.btnHint)
        cardHint = findViewById(R.id.cardHint)
        ivMnemonic = findViewById(R.id.ivMnemonic)
        tvHintLoading = findViewById(R.id.tvHintLoading)
        tvHintState = findViewById(R.id.tvHintState)

        progress.max = words.size
        tvLiveAccuracy.text = "记住 0/0"

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { confirmExit() }
        btnSpeak.setOnClickListener { pronounce() }
        btnHint.setOnClickListener { toggleHint() }
        cardWord.setOnClickListener {
            if (!revealed) reveal() else if (answered) goNext()
        }
        btnForgot.setOnClickListener { grade(ReviewGrade.FORGOT) }
        btnVague.setOnClickListener { grade(ReviewGrade.VAGUE) }
        btnKnown.setOnClickListener { grade(ReviewGrade.KNOWN) }

        showCurrent()
    }

    private fun showCurrent() {
        val word = words[index]
        answered = false
        revealed = false

        // 换词时停掉上一词还在播的音，并把喇叭恢复常态
        speaker.stop()
        setSpeakLoading(false)

        // 配图跟着换词重置，不保留上一张
        resetHint()

        tvCounter.text = "${index + 1} / ${words.size}"
        progress.setProgressCompat(index + 1, true)
        tvStagePill.text = word.stageText
        tvWord.text = word.word
        tvPhonetic.text = if (word.phonetic.isBlank()) "美式音标待补充" else word.phonetic
        tvMeaning.text = ""
        tvExample.visibility = View.GONE
        tvTapHint.text = "轻触卡片查看释义"
        tvTapHint.visibility = View.VISIBLE
        // 评价按钮进卡即可点：未揭晓释义时点评价会自动先揭晓，再记录评分
        setGradeButtonsEnabled(true)

        // 开了自动朗读就进卡先读一遍，用户也可以随时点喇叭重听
        if (PronPrefs.isAutoEnabled(this)) pronounce()
    }

    private fun reveal() {
        val word = words[index]
        revealed = true
        tvMeaning.text = word.meaning
        if (word.example.isNotBlank()) {
            tvExample.text = word.example
            tvExample.visibility = View.VISIBLE
        }
        tvTapHint.text = "记住了吗？选择下面的评价"
        setGradeButtonsEnabled(true)
    }

    /**
     * 播放当前单词发音。
     * 图标状态：加载中/播放中为品牌绿，结束或失败恢复纯黑，让用户知道点没点上。
     */
    private fun pronounce() {
        if (words.isEmpty()) return
        val word = words[index]
        speaker.play(word.word) { state ->
            if (isFinishing || isDestroyed) return@play
            setSpeakLoading(state != PronState.DONE)
        }
    }

    private fun setSpeakLoading(active: Boolean) {
        btnSpeak.imageTintList = ColorStateList.valueOf(
            getColor(if (active) R.color.brand_mid else R.color.text_black)
        )
        btnSpeak.alpha = if (active) 0.75f else 1f
    }

    /**
     * 图形记忆：点「提示」展开该词的联想配图。
     * 再点一次收起；换词自动清空，不会把上一张图留在屏幕上。
     */
    private fun toggleHint() {
        if (words.isEmpty()) return
        val word = words[index]

        if (cardHint.visibility == View.VISIBLE) {
            cardHint.visibility = View.GONE
            tvHintState.visibility = View.GONE
            btnHint.text = "提示"
            return
        }
        // 同一个词第二次点，图已经在内存里，直接展开不再联网
        if (hintWord == word.word && ivMnemonic.drawable != null) {
            cardHint.visibility = View.VISIBLE
            tvHintLoading.visibility = View.GONE
            btnHint.text = "收起提示"
            return
        }
        loadHint(word)
    }

    private fun loadHint(word: Word) {
        val my = ++hintToken
        hintWord = word.word
        btnHint.isEnabled = false
        btnHint.text = "找图中…"
        ivMnemonic.setImageDrawable(null)
        tvHintLoading.visibility = View.VISIBLE
        tvHintState.visibility = View.GONE
        cardHint.visibility = View.VISIBLE

        imageIo.execute {
            val file = ImageCache.ensure(this, word.word)
            runOnUiThread {
                if (isFinishing || isDestroyed || my != hintToken) return@runOnUiThread
                btnHint.isEnabled = true
                val bitmap = file?.let { decodeScaled(it) }
                if (bitmap != null) {
                    ivMnemonic.setImageBitmap(bitmap)
                    tvHintLoading.visibility = View.GONE
                    btnHint.text = "收起提示"
                } else {
                    // 没图就老实说没有，不塞无关图片误导记忆
                    cardHint.visibility = View.GONE
                    btnHint.text = "提示"
                    tvHintState.text = "「${word.word}」暂时没有配图，试着想一个用到它的场景来记"
                    tvHintState.visibility = View.VISIBLE
                }
            }
        }
    }

    /** 按宽度采样解码，避免大图把内存顶爆 */
    private fun decodeScaled(file: java.io.File): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1200) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun resetHint() {
        hintToken++
        hintWord = ""
        cardHint.visibility = View.GONE
        ivMnemonic.setImageDrawable(null)
        tvHintLoading.visibility = View.VISIBLE
        tvHintState.visibility = View.GONE
        btnHint.text = "提示"
        btnHint.isEnabled = true
    }

    private fun setGradeButtonsEnabled(enabled: Boolean) {
        btnForgot.isEnabled = enabled
        btnVague.isEnabled = enabled
        btnKnown.isEnabled = enabled
    }

    private fun grade(grade: ReviewGrade) {
        if (answered) return
        // 还没看到释义就先揭晓，避免误点评价直接改掉复习阶段
        if (!revealed) {
            reveal()
            return
        }
        val word = words[index]
        val updated = repo.recordReview(word.id, grade, "review")
        answered = true
        answeredCount++
        if (grade != ReviewGrade.FORGOT) knownCount++

        if (!revealed) {
            revealed = true
            tvMeaning.text = word.meaning
            if (word.example.isNotBlank()) {
                tvExample.text = word.example
                tvExample.visibility = View.VISIBLE
            }
        }
        tvStagePill.text = updated?.stageText ?: word.stageText
        tvTapHint.text = "下次：${updated?.nextReviewText ?: "稍后"} · 轻触继续"
        tvLiveAccuracy.text = "记住 $knownCount/$answeredCount"
        setGradeButtonsEnabled(false)
    }

    private fun goNext() {
        if (index >= words.lastIndex) {
            finishRound()
        } else {
            index++
            showCurrent()
        }
    }

    private fun finishRound() {
        val accuracy = if (answeredCount == 0) 0 else knownCount * 100 / answeredCount
        MaterialAlertDialogBuilder(this)
            .setTitle("本轮完成")
            .setMessage("巩固了 $answeredCount 个单词，记住 $knownCount 个，正确率 $accuracy%。\n下一批到期的单词会在复习队列里等你。")
            .setPositiveButton("完成") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun confirmExit() {
        if (answeredCount == 0) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("结束本轮？")
            .setMessage("已经完成 $answeredCount 个单词，进度会自动保存。")
            .setNegativeButton("继续") { _, _ -> }
            .setPositiveButton("结束") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
        imageIo.shutdownNow()
    }

    companion object {
        private const val EXTRA_IDS = "extra_study_ids"
        private const val EXTRA_TITLE = "extra_study_title"

        fun start(context: Context, ids: LongArray, title: String) {
            val intent = Intent(context, StudyActivity::class.java).apply {
                putExtra(EXTRA_IDS, ids)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }
}
