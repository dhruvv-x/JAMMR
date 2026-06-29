package com.pookie.jammr.data.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val type: String = "text",
    val timestamp: Long = System.currentTimeMillis(),
    val songTrackId: Long? = null,
    val songTrackName: String? = null,
    val songArtistName: String? = null,
    val songArtworkUrl: String? = null,
    val songPreviewUrl: String? = null,

    // Reply: a lightweight snapshot of the message being replied to, stored
    // directly on this message so the UI can show a quote preview without
    // an extra Firestore read. If the original message is later deleted in
    // the future, this snapshot still renders fine on its own.
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,

    // Reactions: userId -> emoji. A Map (not a List) so each user can only
    // have ONE active reaction per message — reacting again with a new
    // emoji simply overwrites their previous one.
    val reactions: Map<String, String> = emptyMap(),

    // Media (type == "image" or "video"). mediaThumbnailUrl is only set for
    // videos — it's a small JPEG frame grab, shown in the chat bubble so we
    // never have to download/decode the full video just to render the list.
    // The full mediaUrl is only fetched when the user taps to play it.
    val mediaUrl: String? = null,
    val mediaThumbnailUrl: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null
)