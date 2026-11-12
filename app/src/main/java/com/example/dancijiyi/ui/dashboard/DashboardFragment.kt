package com.example.dancijiyi.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.ui.quiz.QuizActivity
import com.example.dancijiyi.ui.study.StudyActivity
import com.example.dancijiyi.ui.toast
import com.example.dancijiyi.ui.widget.RingProgressView
import com.example.dancijiyi.ui.widget.TrendChartView
import java.util.Calendar

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val repo get() = DanciApp.repository

    private lateinit var tvDate: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvHeaderHint: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvDueChip: TextView
    private lateinit var tvDue: TextView
    private lateinit var tvDueSub: TextView
    private lateinit var tvTodayReview: TextView
    private lateinit var tvTodayReviewSub: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvAccuracySub: TextView
    private lateinit var ringProgress: RingProgressView
    private lateinit var trendChart: TrendChartView
    private lateinit var tvTrendSummary: TextView
    private lateinit var tvMastered: TextView
    private lateinit var tvLearning: TextView
    private lateinit var tvNewCount: TextView
    private lateinit var tvTotalWords: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvDate = view.findViewById(R.id.tvDate)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvHeaderHint = view.findViewById(R.id.tvHeaderHint)
        tvStreak = view.findViewById(R.id.tvStreak)
        tvDueChip = view.findViewById(R.id.tvDueChip)
        tvDue = view.findViewById(R.id.tvDue)
        tvDueSub = view.findViewById(R.id.tvDueSub)
        tvTodayReview = view.findViewById(R.id.tvTodayReview)
        tvTodayReviewSub = view.findViewById(R.id.tvTodayReviewSub)
        tvAccuracy = view.findViewById(R.id.tvAccuracy)
        tvAccuracySub = view.findViewById(R.id.tvAccuracySub)
        ringProgress = view.findViewById(R.id.ringProgress)
        trendChart = view.findViewById(R.id.trendChart)
        tvTrendSummary = view.findViewById(R.id.tvTrendSummary)
        tvMastered = view.findViewById(R.id.tvMastered)
        tvLearning = view.findViewById(R.id.tvLearning)
        tvNewCount = view.findViewById(R.id.tvNewCount)
        tvTotalWords = view.findViewById(R.id.tvTotalWords)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when {
            hour < 6 -> "夜深了，早点休息"
            hour < 12 -> "早上好，先背几个词"
            hour < 18 -> "下午好，继续积累"
            else -> "晚上好，巩固今天的词"
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartReview)
            .setOnClickListener { startReview() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartQuiz)
            .setOnClickListener { QuizActivity.start(requireContext(), 10) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val stats = repo.dashboard()
        val cal = Calendar.getInstance()
        val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        tvDate.text = "${cal.get(Calendar.MONTH) + 1} 月 ${cal.get(Calendar.DAY_OF_MONTH)} 日 · 星期$week"

        tvHeaderHint.text =
            "今日新增 ${stats.todayLearned} · 已复习 ${stats.todayReviewed} · 正确率 ${stats.todayAccuracy}%"
        tvStreak.text = "连续打卡 ${stats.streakDays} 天"
        tvDueChip.text = "待复习 ${stats.dueNow} 词"

        tvDue.text = stats.dueNow.toString()
        tvDueSub.text = "到期需巩固"
        tvTodayReview.text = stats.todayReviewed.toString()
        tvTodayReviewSub.text = "今日新增 ${stats.todayLearned}"
        tvAccuracy.text = "${stats.todayAccuracy}%"
        tvAccuracySub.text = "累计 ${stats.overallAccuracy}%"

        ringProgress.percent = stats.masteredPercent
        tvMastered.text = "${stats.masteredWords} 词"
        tvLearning.text = "${stats.learningWords} 词"
        tvNewCount.text = "${stats.newWords} 词"
        tvTotalWords.text = "词库共 ${stats.totalWords} 词"

        trendChart.setData(stats.last7)
        val weekReviewed = stats.last7.sumOf { it.reviewed }
        tvTrendSummary.text = "共 $weekReviewed 次"
    }

    private fun startReview() {
        val due = repo.dueWords(50)
        if (due.isEmpty()) {
            requireContext().toast("现在没有到期单词，去词库加点新词吧")
            return
        }
        val title = "今天要巩固的词"
        StudyActivity.start(requireContext(), due.map { it.id }.toLongArray(), title)
    }
}
