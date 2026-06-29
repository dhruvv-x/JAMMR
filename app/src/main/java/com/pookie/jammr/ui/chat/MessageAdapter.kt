package com.pookie.jammr.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
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
 * Renders the message list. Changes from the original:
 *
 *  1. DiffUtil replaces notifyDataSetChanged() — no more full-list flicker
 *     on every Firestore snapshot.
 *  2. type == "song" inflates item_message_song.xml into sentSongCard /
 *     receivedSongCard and wires up artwork + preview playback.
 *  3. Reactions now show ALL unique emojis from the map, de-duplicated and
 *     joined, plus a count badge when there are 2+ reactions.
 */
class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: String,
    private val otherUserName: String,
    private val onMessageLongPress: (message: Message, anchorView: View) -> Unit,
    private val onMediaClick: (message: Message) -> Unit,
    private val onSongPreviewClick: (message: Message) -> Unit = {},
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

    private fun formatTime(timestamp: Long): String = timeFormatter.format(Date(timestamp))

    private fun formatDateLabel(timestamp: Long): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()
        return when {
            isSameDay(msgCal, todayCal) -> "Today"
            isYesterday(msgCal, todayCal) -> "Yesterday"
            else -> dateFormatter.format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
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

    private fun buildItems(msgs: List<Message>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        var lastDay = -1; var lastYear = -1
        for (msg in msgs) {
            val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
            val day = cal.get(Calendar.DAY_OF_YEAR); val year = cal.get(Calendar.YEAR)
            if (day != lastDay || year != lastYear) {
                result.add(ListItem.DateHeader(formatDateLabel(msg.timestamp)))
                lastDay = day; lastYear = year
            }
            result.add(ListItem.MessageItem(msg))
        }
        return result
    }

    // ── DiffUtil callback ────────────────────────────────────────────────────

    private class ItemDiff(
        private val old: List<ListItem>,
        private val new: List<ListItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int): Boolean {
            val a = old[o]; val b = new[n]
            return when {
                a is ListItem.DateHeader && b is ListItem.DateHeader -> a.label == b.label
                a is ListItem.MessageItem && b is ListItem.MessageItem ->
                    a.message.messageId == b.message.messageId
                else -> false
            }
        }
        override fun areContentsTheSame(o: Int, n: Int) = old[o] == new[n]
    }

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class DateSeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDateSeparator)
    }

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Sent
        val sentContainer: View = view.findViewById(R.id.sentContainer)
        val sentBubble: View = view.findViewById(R.id.sentBubble)
        val tvSent: TextView = view.findViewById(R.id.tvSent)
        val tvSentTime: TextView = view.findViewById(R.id.tvSentTime)
        val sentReplyQuote: View = view.findViewById(R.id.sentReplyQuote)
        val tvSentReplySender: TextView = view.findViewById(R.id.tvSentReplySender)
        val tvSentReplyText: TextView = view.findViewById(R.id.tvSentReplyText)
        val sentReactionBadge: LinearLayout = view.findViewById(R.id.sentReactionBadge)
        val tvSentReactionEmojis: TextView = view.findViewById(R.id.tvSentReactionEmojis)
        val tvSentReactionCount: TextView = view.findViewById(R.id.tvSentReactionCount)
        val sentMediaFrame: FrameLayout = view.findViewById(R.id.sentMediaFrame)
        val ivSentMedia: ShapeableImageView = view.findViewById(R.id.ivSentMedia)
        val ivSentPlayIcon: ImageView = view.findViewById(R.id.ivSentPlayIcon)
        val sentSongCard: FrameLayout = view.findViewById(R.id.sentSongCard)

        // Received
        val receivedContainer: View = view.findViewById(R.id.receivedContainer)
        val receivedBubble: View = view.findViewById(R.id.receivedBubble)
        val tvReceived: TextView = view.findViewById(R.id.tvReceived)
        val tvReceivedTime: TextView = view.findViewById(R.id.tvReceivedTime)
        val receivedReplyQuote: View = view.findViewById(R.id.receivedReplyQuote)
        val tvReceivedReplySender: TextView = view.findViewById(R.id.tvReceivedReplySender)
        val tvReceivedReplyText: TextView = view.findViewById(R.id.tvReceivedReplyText)
        val receivedReactionBadge: LinearLayout = view.findViewById(R.id.receivedReactionBadge)
        val tvReceivedReactionEmojis: TextView = view.findViewById(R.id.tvReceivedReactionEmojis)
        val tvReceivedReactionCount: TextView = view.findViewById(R.id.tvReceivedReactionCount)
        val receivedMediaFrame: FrameLayout = view.findViewById(R.id.receivedMediaFrame)
        val ivReceivedMedia: ShapeableImageView = view.findViewById(R.id.ivReceivedMedia)
        val ivReceivedPlayIcon: ImageView = view.findViewById(R.id.ivReceivedPlayIcon)
        val receivedSongCard: FrameLayout = view.findViewById(R.id.receivedSongCard)
    }

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun getItemViewType(position: Int) = when (items[position]) {
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

    // ── Bind ─────────────────────────────────────────────────────────────────

    private fun bindMessage(holder: MessageViewHolder, message: Message) {
        val isSent = message.senderId == currentUserId

        if (isSent) {
            holder.sentContainer.visibility = View.VISIBLE
            holder.receivedContainer.visibility = View.GONE
            holder.tvSentTime.text = formatTime(message.timestamp)
            bindContent(
                message = message,
                isSent = true,
                textView = holder.tvSent,
                mediaFrame = holder.sentMediaFrame,
                mediaImageView = holder.ivSentMedia,
                playIcon = holder.ivSentPlayIcon,
                songCardSlot = holder.sentSongCard
            )
            bindReplyQuote(message, holder.sentReplyQuote, holder.tvSentReplySender, holder.tvSentReplyText)
            bindReactions(message.reactions, holder.sentReactionBadge, holder.tvSentReactionEmojis, holder.tvSentReactionCount)
            holder.sentBubble.setOnLongClickListener { onMessageLongPress(message, holder.sentBubble); true }
            holder.sentMediaFrame.setOnClickListener { onMediaClick(message) }
        } else {
            holder.receivedContainer.visibility = View.VISIBLE
            holder.sentContainer.visibility = View.GONE
            holder.tvReceivedTime.text = formatTime(message.timestamp)
            bindContent(
                message = message,
                isSent = false,
                textView = holder.tvReceived,
                mediaFrame = holder.receivedMediaFrame,
                mediaImageView = holder.ivReceivedMedia,
                playIcon = holder.ivReceivedPlayIcon,
                songCardSlot = holder.receivedSongCard
            )
            bindReplyQuote(message, holder.receivedReplyQuote, holder.tvReceivedReplySender, holder.tvReceivedReplyText)
            bindReactions(message.reactions, holder.receivedReactionBadge, holder.tvReceivedReactionEmojis, holder.tvReceivedReactionCount)
            holder.receivedBubble.setOnLongClickListener { onMessageLongPress(message, holder.receivedBubble); true }
            holder.receivedMediaFrame.setOnClickListener { onMediaClick(message) }
        }
    }

    private fun bindContent(
        message: Message,
        isSent: Boolean,
        textView: TextView,
        mediaFrame: FrameLayout,
        mediaImageView: ShapeableImageView,
        playIcon: ImageView,
        songCardSlot: FrameLayout
    ) {
        // Reset all content slots first
        textView.visibility = View.GONE
        mediaFrame.visibility = View.GONE
        songCardSlot.visibility = View.GONE

        when (message.type) {
            "song" -> {
                songCardSlot.visibility = View.VISIBLE
                // Inflate the song card if not already inflated for this slot
                if (songCardSlot.childCount == 0) {
                    LayoutInflater.from(songCardSlot.context)
                        .inflate(R.layout.item_message_song, songCardSlot, true)
                }
                val cardView = songCardSlot.getChildAt(0)
                val ivArtwork = cardView.findViewById<ShapeableImageView>(R.id.ivSongArtwork)
                val tvTrack = cardView.findViewById<TextView>(R.id.tvSongTrackName)
                val tvArtist = cardView.findViewById<TextView>(R.id.tvSongArtistName)
                val ivPlay = cardView.findViewById<ImageView>(R.id.ivSongPlayBtn)

                tvTrack.text = message.songTrackName ?: "Unknown track"
                tvArtist.text = message.songArtistName ?: ""
                Glide.with(ivArtwork.context)
                    .load(message.songArtworkUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivArtwork)

                cardView.setOnClickListener { onSongPreviewClick(message) }
                ivPlay.setOnClickListener { onSongPreviewClick(message) }
            }
            "image", "video" -> {
                mediaFrame.visibility = View.VISIBLE
                val imageToLoad = if (message.type == "video") {
                    message.mediaThumbnailUrl ?: message.mediaUrl
                } else {
                    message.mediaUrl
                }
                Glide.with(mediaImageView.context)
                    .load(imageToLoad)
                    .centerCrop()
                    .into(mediaImageView)
                playIcon.visibility = if (message.type == "video") View.VISIBLE else View.GONE
            }
            else -> {
                textView.visibility = View.VISIBLE
                textView.text = message.text
            }
        }
    }

    private fun bindReplyQuote(
        message: Message,
        quoteContainer: View,
        tvSender: TextView,
        tvText: TextView
    ) {
        if (message.replyToText != null) {
            quoteContainer.visibility = View.VISIBLE
            tvSender.text = if (message.replyToSenderId == currentUserId) "You" else otherUserName
            tvText.text = message.replyToText
            quoteContainer.setOnClickListener {
                message.replyToMessageId?.let { id -> onReplyQuoteTap(id) }
            }
        } else {
            quoteContainer.visibility = View.GONE
            quoteContainer.setOnClickListener(null)
        }
    }

    /**
     * Shows grouped reactions: unique emojis joined together ("❤️😂") and a
     * count label when there are 2 or more reactions ("· 3").
     * Hiding the badge entirely when the reactions map is empty.
     */
    private fun bindReactions(
        reactions: Map<String, String>,
        badge: LinearLayout,
        tvEmojis: TextView,
        tvCount: TextView
    ) {
        if (reactions.isEmpty()) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        // De-duplicate emojis while preserving insertion order
        val uniqueEmojis = reactions.values.distinct()
        tvEmojis.text = uniqueEmojis.joinToString("")
        val total = reactions.size
        if (total > 1) {
            tvCount.visibility = View.VISIBLE
            tvCount.text = "· $total"
        } else {
            tvCount.visibility = View.GONE
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun findPositionByMessageId(messageId: String): Int =
        items.indexOfFirst { it is ListItem.MessageItem && it.message.messageId == messageId }

    /** DiffUtil-powered update — no full rebind, no flicker. */
    fun updateMessages(newMessages: List<Message>) {
        val newItems = buildItems(newMessages)
        val diff = DiffUtil.calculateDiff(ItemDiff(items, newItems))
        messages = newMessages
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}