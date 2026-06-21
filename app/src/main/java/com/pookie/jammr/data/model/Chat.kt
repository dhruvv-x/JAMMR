package com.pookie.jammr.data.model

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val otherUserName: String = "",
    val otherUserPhotoUrl: String? = null
)