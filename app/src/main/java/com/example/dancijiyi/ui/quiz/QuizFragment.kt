package com.example.dancijiyi.ui.quiz

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.ui.toast
import com.google.android.material.button.MaterialButton

class QuizFragment : Fragment(R.layout.fragment_quiz) {

    private val repo get() = DanciApp.repository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.btnQuiz10).setOnClickListener {
            startQuiz(10)
        }
        view.findViewById<MaterialButton>(R.id.btnQuiz20).setOnClickListener {
            startQuiz(20)
        }
    }

    override fun onResume() {
        super.onResume()
        val stats = repo.dashboard()
        view?.findViewById<TextView>(R.id.tvTotalAnswers)?.text = stats.totalReviews.toString()
        view?.findViewById<TextView>(R.id.tvTotalAccuracy)?.text = "${stats.overallAccuracy}%"
        view?.findViewById<TextView>(R.id.tvDueNow)?.text = stats.dueNow.toString()
    }

    private fun startQuiz(count: Int) {
        if (repo.allWords().size < 4) {
            requireContext().toast("词库里的单词太少，至少要 4 个才能开始测试")
            return
        }
        QuizActivity.start(requireContext(), count)
    }
}
