package com.example.dancijiyi.ui.library

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dancijiyi.DanciApp
import com.example.dancijiyi.R
import com.example.dancijiyi.data.online.OnlineBook
import com.example.dancijiyi.data.online.OnlineCatalog
import com.example.dancijiyi.data.online.OnlineStore
import com.example.dancijiyi.data.online.OnlineUpdater
import com.example.dancijiyi.ui.toast
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 在线词库页：拉取远端清单、逐本或批量下载/更新到本地词库 */
class OnlineBooksActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var btnCheck: MaterialButton
    private lateinit var btnUpdateAll: MaterialButton
    private lateinit var rvOnline: RecyclerView
    private lateinit var adapter: OnlineBookAdapter

    private val main = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_books)

        tvStatus = findViewById(R.id.tvStatus)
        pbLoading = findViewById(R.id.pbLoading)
        btnCheck = findViewById(R.id.btnCheck)
        btnUpdateAll = findViewById(R.id.btnUpdateAll)
        rvOnline = findViewById(R.id.rvOnline)

        adapter = OnlineBookAdapter(
            onDownload = { book -> download(listOf(book)) }
        )
        rvOnline.layoutManager = LinearLayoutManager(this)
        rvOnline.adapter = adapter

        btnCheck.setOnClickListener { loadManifest() }
        btnUpdateAll.setOnClickListener { updateAll() }
        loadManifest()
    }

    // ------------------------------------------------------------ 清单

    private fun loadManifest() {
        setBusy(true, "正在连接更新服务器…")
        Thread {
            var error: String? = null
            var books: List<OnlineBook>? = null
            try {
                books = OnlineCatalog.fetchManifest()
            } catch (e: Exception) {
                error = e.message ?: "网络异常，请检查是否联网"
            }
            main.post {
                setBusy(false)
                if (books == null) {
                    tvStatus.text = "检查失败：$error"
                    toast("检查失败：$error", long = true)
                    return@post
                }
                OnlineStore.setLastCheckAt(this, System.currentTimeMillis())
                adapter.submit(books)
                refreshSummary(books)
            }
        }.start()
    }

    /** 底部一行汇总：几本、几本可更新、上次检查时间 */
    private fun refreshSummary(books: List<OnlineBook>) {
        val pending = books.count { OnlineStore.version(this, it.id) < it.version }
        val last = lastCheckText()
        tvStatus.text = buildString {
            append("共 ${books.size} 本在线词库")
            append(if (pending > 0) " · 有 $pending 本可下载或更新" else " · 全部已是最新")
            if (last.isNotEmpty()) append(" · 上次检查 $last")
        }
        btnUpdateAll.isEnabled = !busy && pending > 0
    }

    private fun lastCheckText(): String {
        val at = OnlineStore.lastCheckAt(this)
        if (at <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    }

    // ------------------------------------------------------------ 下载

    private fun download(books: List<OnlineBook>) {
        if (books.isEmpty() || busy) return
        setBusy(true, "正在下载…")
        books.forEach { adapter.setDownloading(it.id, true) }
        Thread {
            var error: String? = null
            var added = 0
            var skipped = 0
            var done = 0
            try {
                books.forEach { book ->
                    val result = OnlineUpdater.download(this, DanciApp.repository, book)
                    added += result.added
                    skipped += result.skipped
                    done++
                }
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            }
            main.post {
                setBusy(false)
                books.forEach { adapter.setDownloading(it.id, false) }
                adapter.notifyDataSetChanged()
                if (error != null) {
                    tvStatus.text = "下载失败：$error"
                    toast("下载失败：$error", long = true)
                    return@post
                }
                if (books.size == 1) {
                    val message = if (added > 0) {
                        "《${books[0].name}》新增 $added 个单词，跳过重复 $skipped 个"
                    } else {
                        "《${books[0].name}》没有新词，已是最新"
                    }
                    tvStatus.text = message
                    toast(message, long = true)
                } else {
                    val message = "已更新 $done 本词库，共新增 $added 个单词，跳过重复 $skipped 个"
                    tvStatus.text = message
                    toast(message, long = true)
                }
                refreshSummary(adapter.currentList())
            }
        }.start()
    }

    private fun updateAll() {
        val pending = adapter.currentList().filter {
            OnlineStore.version(this, it.id) < it.version
        }
        if (pending.isEmpty()) {
            toast("所有词库都是最新的")
            return
        }
        download(pending)
    }

    private fun setBusy(value: Boolean, hint: String? = null) {
        busy = value
        pbLoading.visibility = if (value) View.VISIBLE else View.GONE
        btnCheck.isEnabled = !value
        btnUpdateAll.isEnabled = !value
        if (hint != null) tvStatus.text = hint
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OnlineBooksActivity::class.java))
        }
    }

    // ------------------------------------------------------------ 列表

    private inner class OnlineBookAdapter(
        private val onDownload: (OnlineBook) -> Unit
    ) : RecyclerView.Adapter<OnlineBookAdapter.Holder>() {

        private val items = mutableListOf<OnlineBook>()
        private val downloading = mutableSetOf<String>()

        fun submit(list: List<OnlineBook>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun currentList(): List<OnlineBook> = items.toList()

        fun setDownloading(id: String, value: Boolean) {
            if (value) downloading.add(id) else downloading.remove(id)
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) notifyItemChanged(index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_online_book, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val book = items[position]
            val localVersion = OnlineStore.version(holder.itemView.context, book.id)
            holder.tvName.text = book.name
            holder.tvMeta.text = "${book.count} 词 · 版本 ${book.version}" +
                if (localVersion > 0) " · 已下载 v$localVersion" else " · 未下载"
            holder.tvNote.text = book.note

            val ctx = holder.itemView.context
            val activeBg = ColorStateList.valueOf(ctx.getColor(R.color.brand_mid))
            val idleBg = ColorStateList.valueOf(ctx.getColor(R.color.brand_light))
            val isDownloading = downloading.contains(book.id)
            when {
                isDownloading -> {
                    holder.btnAction.text = "更新中…"
                    holder.btnAction.isEnabled = false
                    holder.btnAction.backgroundTintList = idleBg
                    holder.btnAction.setTextColor(ctx.getColor(R.color.brand_dark))
                }
                localVersion >= book.version -> {
                    holder.btnAction.text = "已是最新"
                    holder.btnAction.isEnabled = false
                    holder.btnAction.backgroundTintList = idleBg
                    holder.btnAction.setTextColor(ctx.getColor(R.color.brand_dark))
                }
                localVersion > 0 -> {
                    holder.btnAction.text = "更新到 v${book.version}"
                    holder.btnAction.isEnabled = true
                    holder.btnAction.backgroundTintList = activeBg
                    holder.btnAction.setTextColor(ctx.getColor(R.color.text_black))
                }
                else -> {
                    holder.btnAction.text = "下载"
                    holder.btnAction.isEnabled = true
                    holder.btnAction.backgroundTintList = activeBg
                    holder.btnAction.setTextColor(ctx.getColor(R.color.text_black))
                }
            }
            holder.btnAction.setOnClickListener { onDownload(book) }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvMeta: TextView = view.findViewById(R.id.tvMeta)
            val tvNote: TextView = view.findViewById(R.id.tvNote)
            val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
        }
    }
}
