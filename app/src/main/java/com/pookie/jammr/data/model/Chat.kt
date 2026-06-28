package com.pookie.jammr.data.model

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val otherUserName: String = "",
    val otherUserPhotoUrl: String? = null,
    // Map of userId -> unread count, e.g. {"uid1": 3, "uid2": 0}
    val unreadCounts: Map<String, Long> = emptyMap()
)