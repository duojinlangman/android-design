package com.example.dancijiyi.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.R
import com.example.dancijiyi.data.Word
import com.example.dancijiyi.data.audio.PronState
import com.example.dancijiyi.data.audio.SpeechPlayer

class WordAdapter(
    private val onClick: (Word) -> Unit,
    private val onStar: (Word) -> Unit,
    private val onDelete: (Word) -> Unit
) : RecyclerView.Adapter<WordAdapter.WordHolder>() {

    private val items = mutableListOf<Word>()
    private var player: SpeechPlayer? = null

    fun submit(list: List<Word>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 页面销毁时释放播放器 */
    fun release() {
        player?.shutdown()
        player = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordHolder {
        if (player == null) player = SpeechPlayer(parent.context)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_word, parent, false)
        return WordHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: WordHolder, position: Int) {
        val word = items[position]
        holder.tvWord.text = word.word
        holder.tvPhonetic.text = word.phonetic
        holder.tvPhonetic.visibility = if (word.phonetic.isBlank()) View.GONE else View.VISIBLE
        holder.tvMeaning.text = word.meaning
        holder.tvStageTag.text = when {
            word.mastered -> "已掌握"
            word.stage == 0 -> "新词"
            else -> "第 ${word.stage} 阶段"
        }
        val starredColor = ContextCompat.getColor(holder.itemView.context, R.color.accent)
        val mutedColor = ContextCompat.getColor(holder.itemView.context, R.color.text_tertiary)
        holder.btnStar.setColorFilter(if (word.starred) starredColor else mutedColor)

        holder.itemView.setOnClickListener { onClick(word) }
        holder.btnSpeak.setOnClickListener {
            player?.play(word.word) { state ->
                holder.btnSpeak.setColorFilter(
                    if (state == PronState.DONE) mutedColor
                    else ContextCompat.getColor(holder.itemView.context, R.color.brand_mid)
                )
            }
        }
        holder.btnStar.setOnClickListener { onStar(word) }
        holder.btnDelete.setOnClickListener { onDelete(word) }
    }

    class WordHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val tvPhonetic: TextView = view.findViewById(R.id.tvPhonetic)
        val tvMeaning: TextView = view.findViewById(R.id.tvMeaning)
        val tvStageTag: TextView = view.findViewById(R.id.tvStageTag)
        val btnStar: ImageButton = view.findViewById(R.id.btnStar)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnSpeak: ImageButton = view.findViewById(R.id.btnSpeak)
    }
}
