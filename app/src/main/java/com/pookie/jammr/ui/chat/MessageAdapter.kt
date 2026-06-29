package com.pookie.jammr.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.pookie.jammr.R
import com.pookie.jammr.data.model.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Renders the message list, including:
 * - Date separators ("Today", "Yesterday", "Dec 25") between days
 * - Reply quote preview (if this message was sent as a reply to another one)
 * - A single reaction emoji badge (if anyone has reacted)
 * - Timestamp inside the bubble (HH:mm)
 * - Photo/video media (image fills the bubble; video shows its thumbnail
 *   with a play icon overlay)
 *
 * Uses two view types: VIEW_TYPE_DATE_SEPARATOR and VIEW_TYPE_MESSAGE.
 * The adapter builds a flat list of [ListItem] (either a DateHeader or a
 * MessageItem) from the raw message list every time updateMessages() is called.
 *
 * Long-pressing a bubble triggers [onMessageLongPress] so the Fragment can
 * show the reaction/reply popup anchored to that bubble. Tapping a media
 * bubble triggers [onMediaClick] so the Fragment can open a full-screen viewer.
 */
class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: String,
    private val onMessageLongPress: (message: Message, anchorView: View) -> Unit,
    private val onMediaClick: (message: Message) -> Unit,
    private val onReplyQuoteTap: (replyToMessageId: String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── List item types ──────────────────────────────────────────────────────

    private sealed class ListItem {
        data class DateHeader(val label: String) : ListItem()
        data class MessageItem(val message: Message) : ListItem()
    }

    private var items: List<ListItem> = buildItems(messages)

    companion object {
        private const val VIEW_TYPE_DATE_SEPARATOR = 0
        private const val VIEW_TYPE_MESSAGE = 1
    }

    // ── Formatters ───────────────────────────────────────────────────────────

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

    private fun formatTime(timestamp: Long): String =
        timeFormatter.format(Date(timestamp))

    /**
     * Returns "Today", "Yesterday", or "MMM d" (e.g. "Jun 14") for a timestamp.
     */
    private fun formatDateLabel(timestamp: Long): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()

        return when {
            isSameDay(msgCal, todayCal) -> "Today"
            isYesterday(msgCal, todayCal) -> "Yesterday"
            else -> dateFormatter.format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(a: Calendar, b: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = b.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(a, yesterday)
    }

    // ── Build flat item list ─────────────────────────────────────────────────

    /**
     * Inserts a DateHeader before the first message of each new calendar day.
     */
    private fun buildItems(msgs: List<Message>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        var lastDayOfYear = -1
        var lastYear = -1

        for (msg in msgs) {
            val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
            val day = cal.get(Calendar.DAY_OF_YEAR)
            val year = cal.get(Calendar.YEAR)

            if (day != lastDayOfYear || year != lastYear) {
                result.add(ListItem.DateHeader(formatDateLabel(msg.timestamp)))
                lastDayOfYear = day
                lastYear = year
            }
            result.add(ListItem.MessageItem(msg))
        }
        return result
    }

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class DateSeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDateSeparator)
    }

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sentContainer: View = view.findViewById(R.id.sentContainer)
        val sentBubble: View = view.findViewById(R.id.sentBubble)
        val tvSent: TextView = view.findViewById(R.id.tvSent)
        val tvSentTime: TextView = view.findViewById(R.id.tvSentTime)
        val sentReplyQuote: View = view.findViewById(R.id.sentReplyQuote)
        val tvSentReplySender: TextView = view.findViewById(R.id.tvSentReplySender)
        val tvSentReplyText: TextView = view.findViewById(R.id.tvSentReplyText)
        val tvSentReaction: TextView = view.findViewById(R.id.tvSentReaction)
        val sentMediaFrame: FrameLayout = view.findViewById(R.id.sentMediaFrame)
        val ivSentMedia: ShapeableImageView = view.findViewById(R.id.ivSentMedia)
        val ivSentPlayIcon: ImageView = view.findViewById(R.id.ivSentPlayIcon)

        val receivedContainer: View = view.findViewById(R.id.receivedContainer)
        val receivedBubble: View = view.findViewById(R.id.receivedBubble)
        val tvReceived: TextView = view.findViewById(R.id.tvReceived)
        val tvReceivedTime: TextView = view.findViewById(R.id.tvReceivedTime)
        val receivedReplyQuote: View = view.findViewById(R.id.receivedReplyQuote)
        val tvReceivedReplySender: TextView = view.findViewById(R.id.tvReceivedReplySender)
        val tvReceivedReplyText: TextView = view.findViewById(R.id.tvReceivedReplyText)
        val tvReceivedReaction: TextView = view.findViewById(R.id.tvReceivedReaction)
        val receivedMediaFrame: FrameLayout = view.findViewById(R.id.receivedMediaFrame)
        val ivReceivedMedia: ShapeableImageView = view.findViewById(R.id.ivReceivedMedia)
        val ivReceivedPlayIcon: ImageView = view.findViewById(R.id.ivReceivedPlayIcon)
    }

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.DateHeader -> VIEW_TYPE_DATE_SEPARATOR
        is ListItem.MessageItem -> VIEW_TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_SEPARATOR -> DateSeparatorViewHolder(
                inflater.inflate(R.layout.item_date_separator, parent, false)
            )
            else -> MessageViewHolder(
                inflater.inflate(R.layout.item_message, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.DateHeader -> (holder as DateSeparatorViewHolder).tvDate.text = item.label
            is ListItem.MessageItem -> bindMessage(holder as MessageViewHolder, item.message)
        }
    }

    override fun getItemCount() = items.size

    private fun bindMessage(holder: MessageViewHolder, message: Message) {
        val isSent = message.senderId == currentUserId
        val reactionEmoji = message.reactions.values.firstOrNull()
        val isMedia = message.type == "image" || message.type == "video"

        if (isSent) {
            holder.sentContainer.visibility = View.VISIBLE
            holder.receivedContainer.visibility = View.GONE

            holder.tvSentTime.text = formatTime(message.timestamp)
            bindMediaOrText(
                isMedia = isMedia,
                isVideo = message.type == "video",
                mediaUrl = message.mediaUrl,
                thumbnailUrl = message.mediaThumbnailUrl,
                text = message.text,
                mediaFrame = holder.sentMediaFrame,
                mediaImageView = holder.ivSentMedia,
                playIcon = holder.ivSentPlayIcon,
                textView = holder.tvSent
            )

            if (message.replyToText != null) {
                holder.sentReplyQuote.visibility = View.VISIBLE
                holder.tvSentReplySender.text =
                    if (message.replyToSenderId == currentUserId) "You" else "Them"
                holder.tvSentReplyText.text = message.replyToText
                holder.sentReplyQuote.setOnClickListener {
                    message.replyToMessageId?.let { id -> onReplyQuoteTap(id) }
                }
            } else {
                holder.sentReplyQuote.visibility = View.GONE
                holder.sentReplyQuote.setOnClickListener(null)
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
            holder.sentMediaFrame.setOnClickListener {
                if (isMedia) onMediaClick(message)
            }
        } else {
            holder.receivedContainer.visibility = View.VISIBLE
            holder.sentContainer.visibility = View.GONE

            holder.tvReceivedTime.text = formatTime(message.timestamp)
            bindMediaOrText(
                isMedia = isMedia,
                isVideo = message.type == "video",
                mediaUrl = message.mediaUrl,
                thumbnailUrl = message.mediaThumbnailUrl,
                text = message.text,
                mediaFrame = holder.receivedMediaFrame,
                mediaImageView = holder.ivReceivedMedia,
                playIcon = holder.ivReceivedPlayIcon,
                textView = holder.tvReceived
            )

            if (message.replyToText != null) {
                holder.receivedReplyQuote.visibility = View.VISIBLE
                holder.tvReceivedReplySender.text =
                    if (message.replyToSenderId == currentUserId) "You" else "Them"
                holder.tvReceivedReplyText.text = message.replyToText
                holder.receivedReplyQuote.setOnClickListener {
                    message.replyToMessageId?.let { id -> onReplyQuoteTap(id) }
                }
            } else {
                holder.receivedReplyQuote.visibility = View.GONE
                holder.receivedReplyQuote.setOnClickListener(null)
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
            holder.receivedMediaFrame.setOnClickListener {
                if (isMedia) onMediaClick(message)
            }
        }
    }

    /**
     * Shows either the media frame (image/video thumbnail + optional play icon)
     * or the plain text view, hiding whichever one isn't needed. For videos,
     * Glide loads the thumbnail URL — falling back to the full mediaUrl if no
     * thumbnail was generated, so old/edge-case messages still show something.
     */
    private fun bindMediaOrText(
        isMedia: Boolean,
        isVideo: Boolean,
        mediaUrl: String?,
        thumbnailUrl: String?,
        text: String,
        mediaFrame: FrameLayout,
        mediaImageView: ShapeableImageView,
        playIcon: ImageView,
        textView: TextView
    ) {
        if (isMedia) {
            mediaFrame.visibility = View.VISIBLE
            textView.visibility = View.GONE

            val imageToLoad = if (isVideo) (thumbnailUrl ?: mediaUrl) else mediaUrl
            Glide.with(mediaImageView.context)
                .load(imageToLoad)
                .centerCrop()
                .into(mediaImageView)

            playIcon.visibility = if (isVideo) View.VISIBLE else View.GONE
        } else {
            mediaFrame.visibility = View.GONE
            textView.visibility = View.VISIBLE
            textView.text = text
        }
    }

    fun findPositionByMessageId(messageId: String): Int {
        return items.indexOfFirst {
            it is ListItem.MessageItem && it.message.messageId == messageId
        }
    }

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        items = buildItems(newMessages)
        notifyDataSetChanged()
    }
}