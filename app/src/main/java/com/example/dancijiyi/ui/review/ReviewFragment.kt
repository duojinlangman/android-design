package com.example.dancijiyi.ui.review

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.R
import com.example.dancijiyi.data.Word
import com.google.android.material.button.MaterialButton
import androidx.fragment.app.Fragment
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.ui.WordDetailDialog
import com.example.dancijiyi.ui.study.StudyActivity
import com.example.dancijiyi.ui.toast

class ReviewFragment : Fragment(R.layout.fragment_review) {

    private val repo get() = DanciApp.repository

    private lateinit var tvDueCount: TextView
    private lateinit var tvReviewedToday: TextView
    private lateinit var tvMastered: TextView
    private lateinit var btnStartReview: MaterialButton
    private lateinit var rvQueue: RecyclerView
    private lateinit var emptyView: View
    private lateinit var tvQueueLabel: TextView

    private lateinit var adapter: QueueAdapter
    private var dueIds: LongArray = longArrayOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvDueCount = view.findViewById(R.id.tvDueCount)
        tvReviewedToday = view.findViewById(R.id.tvReviewedToday)
        tvMastered = view.findViewById(R.id.tvMastered)
        btnStartReview = view.findViewById(R.id.btnStartReview)
        rvQueue = view.findViewById(R.id.rvQueue)
        emptyView = view.findViewById(R.id.emptyView)
        tvQueueLabel = view.findViewById(R.id.tvQueueLabel)

        adapter = QueueAdapter { word ->
            WordDetailDialog.show(requireContext(), word) { refresh() }
        }
        rvQueue.layoutManager = LinearLayoutManager(requireContext())
        rvQueue.adapter = adapter

        btnStartReview.setOnClickListener { startStudy() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val stats = repo.dashboard()
        val due = repo.dueWords(50)
        val upcoming = repo.upcomingWords(24, 30)
        dueIds = due.map { it.id }.toLongArray()

        tvDueCount.text = stats.dueNow.toString()
        tvReviewedToday.text = stats.todayReviewed.toString()
        tvMastered.text = stats.masteredWords.toString()
        btnStartReview.text =
            if (due.isEmpty()) "暂无到期单词" else "开始复习 ${due.size} 个单词"
        btnStartReview.isEnabled = due.isNotEmpty()

        val items = due + upcoming.filter { up -> due.none { it.id == up.id } }
        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rvQueue.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        tvQueueLabel.text = when {
            due.isEmpty() && upcoming.isEmpty() -> "排队中的单词"
            else -> "排队中的单词 · 到期 ${due.size} 个，24 小时内还有 ${upcoming.size} 个"
        }
    }

    private fun startStudy() {
        if (dueIds.isEmpty()) {
            requireContext().toast("现在没有到期的单词")
            return
        }
        StudyActivity.start(requireContext(), dueIds, "复习队列")
    }
}
