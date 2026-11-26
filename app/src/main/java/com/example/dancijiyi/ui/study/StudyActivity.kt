package com.example.dancijiyi.ui.study

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator

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

    private lateinit var speaker: SpeechPlayer

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

        progress.max = words.size
        tvLiveAccuracy.text = "记住 0/0"

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { confirmExit() }
        btnSpeak.setOnClickListener { pronounce() }
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
