package com.pookie.jammr.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.ChatPreview

/**
 * Simple list of chats shown inside the "Forward to..." dialog.
 * Tapping a row forwards the pending message into that chat.
 */
class ForwardChatAdapter(
    private val chats: List<ChatPreview>,
    private val onChatSelected: (ChatPreview) -> Unit
) : RecyclerView.Adapter<ForwardChatAdapter.ForwardViewHolder>() {

    inner class ForwardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvForwardChatName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForwardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_forward_chat_item, parent, false)
        return ForwardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForwardViewHolder, position: Int) {
        val chat = chats[position]
        holder.tvName.text = chat.otherUserName
        holder.itemView.setOnClickListener { onChatSelected(chat) }
    }

    override fun getItemCount() = chats.size
}