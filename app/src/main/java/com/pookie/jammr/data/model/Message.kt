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
    val songPreviewUrl: String? = null
)