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
    val reactions: Map<String, String> = emptyMap()
)