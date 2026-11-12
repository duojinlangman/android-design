package com.example.dancijiyi.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.R
import com.example.dancijiyi.data.WordBook
import com.google.android.material.progressindicator.LinearProgressIndicator

class BookAdapter(
    private val onClick: (WordBook) -> Unit,
    private val onLongClick: (WordBook) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookHolder>() {

    private val items = mutableListOf<WordBook>()

    fun submit(list: List<WordBook>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BookHolder, position: Int) {
        val book = items[position]
        holder.tvBookIcon.text = book.name.take(1)
        holder.tvBookName.text = book.name
        holder.tvBookCount.text = "${book.wordCount} 词"
        holder.progressBook.max = 100
        holder.progressBook.setProgressCompat(book.progress, false)
        holder.tvBookSub.text =
            if (book.isSystem) "待复习 ${book.dueCount} 词 · 已掌握 ${book.masteredCount} 词"
            else "已掌握 ${book.masteredCount} / ${book.wordCount} 词 · 待复习 ${book.dueCount}"
        holder.itemView.setOnClickListener { onClick(book) }
        holder.itemView.setOnLongClickListener {
            onLongClick(book)
            true
        }
    }

    class BookHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBookIcon: TextView = view.findViewById(R.id.tvBookIcon)
        val tvBookName: TextView = view.findViewById(R.id.tvBookName)
        val tvBookCount: TextView = view.findViewById(R.id.tvBookCount)
        val tvBookSub: TextView = view.findViewById(R.id.tvBookSub)
        val progressBook: LinearProgressIndicator = view.findViewById(R.id.progressBook)
    }
}
