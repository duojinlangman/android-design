package com.example.dancijiyi.ui.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.R
import com.example.dancijiyi.data.Word

class QueueAdapter(
    private val onClick: (Word) -> Unit
) : RecyclerView.Adapter<QueueAdapter.WordHolder>() {

    private val items = mutableListOf<Word>()

    fun submit(list: List<Word>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue_word, parent, false)
        return WordHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: WordHolder, position: Int) {
        val word = items[position]
        holder.tvWord.text = word.word
        holder.tvPhonetic.text = word.phonetic
        holder.tvPhonetic.visibility = if (word.phonetic.isBlank()) View.GONE else View.VISIBLE
        holder.tvMeaning.text = word.meaning
        holder.tvStage.text = word.stageText
        holder.tvNext.text = when {
            word.mastered -> "已掌握"
            word.nextReviewAt <= System.currentTimeMillis() -> "现在到期"
            else -> word.nextReviewText
        }
        holder.itemView.setOnClickListener { onClick(word) }
    }

    class WordHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val tvPhonetic: TextView = view.findViewById(R.id.tvPhonetic)
        val tvMeaning: TextView = view.findViewById(R.id.tvMeaning)
        val tvStage: TextView = view.findViewById(R.id.tvStage)
        val tvNext: TextView = view.findViewById(R.id.tvNext)
    }
}
