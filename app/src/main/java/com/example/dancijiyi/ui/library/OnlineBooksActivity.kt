package com.example.dancijiyi.ui.library

import android.content.Context
import android.content.Intent
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
import com.example.dancijiyi.data.online.UpdateResult
import com.example.dancijiyi.ui.toast
import com.google.android.material.button.MaterialButton

/** 在线词库页：拉取远端清单、逐本下载/更新到本地词库 */
class OnlineBooksActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var btnCheck: MaterialButton
    private lateinit var rvOnline: RecyclerView
    private lateinit var adapter: OnlineBookAdapter

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_books)

        tvStatus = findViewById(R.id.tvStatus)
        pbLoading = findViewById(R.id.pbLoading)
        btnCheck = findViewById(R.id.btnCheck)
        rvOnline = findViewById(R.id.rvOnline)

        adapter = OnlineBookAdapter { book -> download(book) }
        rvOnline.layoutManager = LinearLayoutManager(this)
        rvOnline.adapter = adapter

        btnCheck.setOnClickListener { loadManifest() }
        loadManifest()
    }

    // ------------------------------------------------------------ 清单

    private fun loadManifest() {
        setLoading(true)
        tvStatus.text = "正在连接更新服务器…"
        Thread {
            var error: String? = null
            var books: List<OnlineBook>? = null
            try {
                books = OnlineCatalog.fetchManifest()
            } catch (e: Exception) {
                error = e.message ?: "网络异常，请检查模拟器/手机是否联网"
            }
            main.post {
                setLoading(false)
                if (books == null) {
                    tvStatus.text = "检查失败：$error"
                    toast("检查失败：$error", long = true)
                    return@post
                }
                adapter.submit(books)
                tvStatus.text = "共 ${books.size} 本在线词库，点击右侧按钮下载到本地"
            }
        }.start()
    }

    // ------------------------------------------------------------ 下载

    private fun download(book: OnlineBook) {
        setLoading(true)
        adapter.setDownloading(book.id, true)
        tvStatus.text = "正在下载《${book.name}》…"
        Thread {
            var error: String? = null
            var result: UpdateResult? = null
            try {
                result = OnlineUpdater.download(this, DanciApp.repository, book)
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            }
            main.post {
                setLoading(false)
                adapter.setDownloading(book.id, false)
                if (result == null) {
                    tvStatus.text = "《${book.name}》下载失败：$error"
                    toast("下载失败：$error", long = true)
                    return@post
                }
                val message = if (result.added > 0) {
                    "《${book.name}》新增 ${result.added} 个单词（跳过重复 ${result.skipped} 个）"
                } else {
                    "《${book.name}》没有新词，已是最新"
                }
                tvStatus.text = message
                toast(message, long = true)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnCheck.isEnabled = !loading
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
                if (localVersion > 0) " · 已下载 v$localVersion" else ""
            holder.tvNote.text = book.note
            holder.btnAction.text = if (downloading.contains(book.id)) "下载中…" else "下载"
            holder.btnAction.isEnabled = !downloading.contains(book.id)
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
