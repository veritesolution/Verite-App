package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatAdapter(private val messages: List<ChatMessage>, private val database: AppDatabase) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ORYN = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ORYN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_oryn, parent, false)
            OrynViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(message)
        } else if (holder is OrynViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        private var avatarJob: kotlinx.coroutines.Job? = null

        fun bind(message: ChatMessage) {
            tvMessage.text = message.text

            // Cancel previous avatar-load coroutine to prevent leak
            avatarJob?.cancel()
            avatarJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use first() instead of collect() — we only need one emission
                    val user = database.userDao().getUser().kotlinx.coroutines.flow.firstOrNull()
                    user?.profileImagePath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                ivAvatar.clearColorFilter()
                                ivAvatar.load(file) {
                                    transformations(CircleCropTransformation())
                                }
                            }
                        }
                    }
                } catch (_: Exception) { /* ViewHolder recycled */ }
            }
        }
    }

    inner class OrynViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvOrynMessage)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.text
        }
    }
}
