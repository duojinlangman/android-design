package com.example.dancijiyi.ui.quiz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.data.QuizQuestion
import com.example.dancijiyi.data.ReviewGrade
import com.example.dancijiyi.data.Word
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.example.dancijiyi.ui.toast

/** 单词测试模式：看英文选中文，答错的词被打回第一阶段 */
class QuizActivity : AppCompatActivity() {

    private val repo get() = DanciApp.repository

    private var questions: List<QuizQuestion> = emptyList()
    private var index = 0
    private var correctCount = 0
    private var answered = false
    private val wrongWords = mutableListOf<Word>()

    private lateinit var tvProgressText: TextView
    private lateinit var tvScoreLive: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvQuizWord: TextView
    private lateinit var tvQuizPhonetic: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var resultPanel: View
    private lateinit var quizContent: View
    private lateinit var optionViews: List<TextView>

    private var questionCount = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        questionCount = intent.getIntExtra(EXTRA_COUNT, 10)

        tvProgressText = findViewById(R.id.tvProgressText)
        tvScoreLive = findViewById(R.id.tvScoreLive)
        progress = findViewById(R.id.progressQuiz)
        tvQuizWord = findViewById(R.id.tvQuizWord)
        tvQuizPhonetic = findViewById(R.id.tvQuizPhonetic)
        tvFeedback = findViewById(R.id.tvFeedback)
        btnNext = findViewById(R.id.btnNext)
        resultPanel = findViewById(R.id.resultPanel)
        quizContent = findViewById(R.id.quizContent)
        optionViews = listOf(
            findViewById(R.id.option0),
            findViewById(R.id.option1),
            findViewById(R.id.option2),
            findViewById(R.id.option3)
        )

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }
        optionViews.forEachIndexed { i, view ->
            view.setOnClickListener { answer(i) }
        }
        btnNext.setOnClickListener {
            if (index >= questions.lastIndex) showResult() else {
                index++
                showCurrent()
            }
        }

        startRound()
    }

    private fun startRound() {
        questions = repo.quizQuestions(questionCount, preferDue = true)
        if (questions.isEmpty()) {
            toast("词库里还没有足够的单词，先去添加吧")
            finish()
            return
        }
        index = 0
        correctCount = 0
        wrongWords.clear()
        quizContent.visibility = View.VISIBLE
        resultPanel.visibility = View.GONE
        progress.max = questions.size
        showCurrent()
    }

    private fun showCurrent() {
        val q = questions[index]
        answered = false
        tvProgressText.text = "第 ${index + 1} / ${questions.size} 题"
        tvScoreLive.text = "答对 $correctCount"
        progress.setProgressCompat(index + 1, true)
        tvQuizWord.text = q.word.word
        tvQuizPhonetic.text = if (q.word.phonetic.isBlank()) "美式音标待补充" else q.word.phonetic
        tvFeedback.visibility = View.GONE
        btnNext.isEnabled = false
        btnNext.text = if (index >= questions.lastIndex) "查看结果" else "下一题"

        optionViews.forEachIndexed { i, view ->
            if (i < q.options.size) {
                view.visibility = View.VISIBLE
                view.text = q.options[i]
                view.setBackgroundResource(R.drawable.bg_option)
                view.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                view.isEnabled = true
            } else {
                view.visibility = View.GONE
            }
        }
    }

    private fun answer(selected: Int) {
        if (answered) return
        answered = true
        val q = questions[index]
        val correct = selected == q.correctIndex

        optionViews.forEachIndexed { i, view ->
            view.isEnabled = false
            when {
                i == q.correctIndex -> {
                    view.setBackgroundResource(R.drawable.bg_option_correct)
                    view.setTextColor(ContextCompat.getColor(this, R.color.success))
                }
                i == selected -> {
                    view.setBackgroundResource(R.drawable.bg_option_wrong)
                    view.setTextColor(ContextCompat.getColor(this, R.color.danger))
                }
            }
        }

        if (correct) {
            correctCount++
            tvFeedback.text = "回答正确，这个词进入下一阶段"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.success))
            repo.recordReview(q.word.id, ReviewGrade.KNOWN, "quiz")
        } else {
            wrongWords.add(q.word)
            tvFeedback.text = "答错了，正确答案：${q.word.meaning}。这个词会回到第一阶段"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.danger))
            repo.recordReview(q.word.id, ReviewGrade.FORGOT, "quiz")
        }
        tvFeedback.visibility = View.VISIBLE
        tvScoreLive.text = "答对 $correctCount"
        btnNext.isEnabled = true
    }

    private fun showResult() {
        quizContent.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE

        val total = questions.size
        val accuracy = correctCount * 100 / total
        findViewById<TextView>(R.id.tvResultScore).text = "$accuracy%"
        findViewById<TextView>(R.id.tvResultDetail).text =
            "共 $total 题，答对 $correctCount 题，答错 ${wrongWords.size} 题"

        val wrongList = findViewById<TextView>(R.id.tvWrongList)
        if (wrongWords.isEmpty()) {
            wrongList.text = "全部答对，这批单词掌握得不错。可以去词库扩充新词了。"
        } else {
            wrongList.text = buildString {
                append("需要再看一遍的词：\n")
                wrongWords.take(10).forEach { w ->
                    append("· ${w.word} ${w.phonetic}  ${w.meaning}\n")
                }
                if (wrongWords.size > 10) append("…共 ${wrongWords.size} 个")
            }
        }

        findViewById<MaterialButton>(R.id.btnAgain).setOnClickListener { startRound() }
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_COUNT = "extra_quiz_count"

        fun start(context: Context, count: Int) {
            context.startActivity(
                Intent(context, QuizActivity::class.java).putExtra(EXTRA_COUNT, count)
            )
        }
    }
}
