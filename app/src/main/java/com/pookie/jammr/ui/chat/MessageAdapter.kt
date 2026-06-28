package com.pookie.jammr.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pookie.jammr.R
import com.pookie.jammr.data.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the message list, including:
 * - Reply quote preview (if this message was sent as a reply to another one)
 * - A single reaction emoji badge (if anyone has reacted)
 * - Timestamp inside the bubble (HH:mm)
 *
 * Long-pressing a bubble triggers [onMessageLongPress] so the Fragment can
 * show the reaction/reply popup anchored to that bubble.
 */
class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: String,
    private val onMessageLongPress: (message: Message, anchorView: View) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun formatTime(timestamp: Long): String =
        timeFormatter.format(Date(timestamp))

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sentContainer: View = view.findViewById(R.id.sentContainer)
        val sentBubble: View = view.findViewById(R.id.sentBubble)
        val tvSent: TextView = view.findViewById(R.id.tvSent)
        val tvSentTime: TextView = view.findViewById(R.id.tvSentTime)
        val sentReplyQuote: View = view.findViewById(R.id.sentReplyQuote)
        val tvSentReplySender: TextView = view.findViewById(R.id.tvSentReplySender)
        val tvSentReplyText: TextView = view.findViewById(R.id.tvSentReplyText)
        val tvSentReaction: TextView = view.findViewById(R.id.tvSentReaction)

        val receivedContainer: View = view.findViewById(R.id.receivedContainer)
        val receivedBubble: View = view.findViewById(R.id.receivedBubble)
        val tvReceived: TextView = view.findViewById(R.id.tvReceived)
        val tvReceivedTime: TextView = view.findViewById(R.id.tvReceivedTime)
        val receivedReplyQuote: View = view.findViewById(R.id.receivedReplyQuote)
        val tvReceivedReplySender: TextView = view.findViewById(R.id.tvReceivedReplySender)
        val tvReceivedReplyText: TextView = view.findViewById(R.id.tvReceivedReplyText)
        val tvReceivedReaction: TextView = view.findViewById(R.id.tvReceivedReaction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isSent = message.senderId == currentUserId

        // Picks the FIRST reaction found to show as the single badge.
        val reactionEmoji = message.reactions.values.firstOrNull()

        if (isSent) {
            holder.sentContainer.visibility = View.VISIBLE
            holder.receivedContainer.visibility = View.GONE

            holder.tvSent.text = message.text
            holder.tvSentTime.text = formatTime(message.timestamp)

            if (message.replyToText != null) {
                holder.sentReplyQuote.visibility = View.VISIBLE
                holder.tvSentReplySender.text =
                    if (message.replyToSenderId == currentUserId) "You" else "Them"
                holder.tvSentReplyText.text = message.replyToText
            } else {
                holder.sentReplyQuote.visibility = View.GONE
            }

            if (reactionEmoji != null) {
                holder.tvSentReaction.visibility = View.VISIBLE
                holder.tvSentReaction.text = reactionEmoji
            } else {
                holder.tvSentReaction.visibility = View.GONE
            }

            holder.sentBubble.setOnLongClickListener {
                onMessageLongPress(message, holder.sentBubble)
                true
            }
        } else {
            holder.receivedContainer.visibility = View.VISIBLE
            holder.sentContainer.visibility = View.GONE

            holder.tvReceived.text = message.text
            holder.tvReceivedTime.text = formatTime(message.timestamp)

            if (message.replyToText != null) {
                holder.receivedReplyQuote.visibility = View.VISIBLE
                holder.tvReceivedReplySender.text =
                    if (message.replyToSenderId == currentUserId) "You" else "Them"
                holder.tvReceivedReplyText.text = message.replyToText
            } else {
                holder.receivedReplyQuote.visibility = View.GONE
            }

            if (reactionEmoji != null) {
                holder.tvReceivedReaction.visibility = View.VISIBLE
                holder.tvReceivedReaction.text = reactionEmoji
            } else {
                holder.tvReceivedReaction.visibility = View.GONE
            }

            holder.receivedBubble.setOnLongClickListener {
                onMessageLongPress(message, holder.receivedBubble)
                true
            }
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}