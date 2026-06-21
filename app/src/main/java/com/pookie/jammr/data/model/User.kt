package com.pookie.jammr.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis()
)