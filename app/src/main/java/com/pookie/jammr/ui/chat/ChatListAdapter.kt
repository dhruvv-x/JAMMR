package com.pookie.jammr.ui.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.ChatPreview

class ChatListAdapter(
    private var chats: List<ChatPreview>,
    private val onChatClick: (ChatPreview) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvOtherUserName: TextView = itemView.findViewById(R.id.tvOtherUserName)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvUnreadBadge: TextView = itemView.findViewById(R.id.tvUnreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_preview, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]

        holder.tvOtherUserName.text = chat.otherUserName
        holder.tvLastMessage.text = chat.lastMessage.ifBlank { "Say hi 👋" }
        holder.tvTimestamp.text = if (chat.lastMessageTimestamp > 0) {
            DateUtils.getRelativeTimeSpanString(
                chat.lastMessageTimestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        } else {
            ""
        }

        // Unread badge
        if (chat.unreadCount > 0) {
            holder.tvUnreadBadge.visibility = View.VISIBLE
            holder.tvUnreadBadge.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
        } else {
            holder.tvUnreadBadge.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(chat.otherUserPhotoUrl)
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .transform(CircleCrop())
            .into(holder.ivAvatar)

        holder.itemView.setOnClickListener { onChatClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    fun updateChats(newChats: List<ChatPreview>) {
        chats = newChats
        notifyDataSetChanged()
    }
}