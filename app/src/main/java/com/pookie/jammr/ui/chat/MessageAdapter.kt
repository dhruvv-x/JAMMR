package com.pookie.jammr.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pookie.jammr.R
import com.pookie.jammr.data.model.Message

class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSent: TextView = view.findViewById(R.id.tvSent)
        val tvReceived: TextView = view.findViewById(R.id.tvReceived)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isSent = message.senderId == currentUserId

        if (isSent) {
            holder.tvSent.visibility = View.VISIBLE
            holder.tvReceived.visibility = View.GONE
            holder.tvSent.text = message.text
        } else {
            holder.tvReceived.visibility = View.VISIBLE
            holder.tvSent.visibility = View.GONE
            holder.tvReceived.text = message.text
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}