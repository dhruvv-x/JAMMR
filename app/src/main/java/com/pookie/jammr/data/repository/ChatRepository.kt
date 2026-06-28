package com.pookie.jammr.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.pookie.jammr.data.model.Chat
import com.pookie.jammr.data.model.Message
import com.pookie.jammr.data.model.User
import kotlinx.coroutines.tasks.await

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    suspend fun getOrCreateChat(currentUserId: String, otherUserId: String): Result<String> {
        return try {
            val chatId = generateChatId(currentUserId, otherUserId)
            val chatRef = db.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            if (!snapshot.exists()) {
                val newChat = mapOf(
                    "chatId" to chatId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to 0L,
                    "lastMessageSenderId" to "",
                    "unreadCounts" to mapOf(currentUserId to 0L, otherUserId to 0L)
                )
                chatRef.set(newChat).await()
            }
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: String, message: Message, otherUserId: String): Result<Unit> {
        return try {
            val messageRef = db.collection("chats").document(chatId)
                .collection("messages").document()
            val messageWithId = message.copy(messageId = messageRef.id)
            messageRef.set(messageWithId).await()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessage" to if (message.type == "song") "🎵 Shared a song" else message.text,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSenderId" to message.senderId,
                    "unreadCounts.${otherUserId}" to FieldValue.increment(1)
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetUnreadCount(chatId: String, currentUserId: String): Result<Unit> {
        return try {
            db.collection("chats").document(chatId)
                .update("unreadCounts.${currentUserId}", 0L)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenForMessages(
        chatId: String,
        onMessagesChanged: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                val messages = snapshot?.documents?.mapNotNull {
                    it.toObject(Message::class.java)
                } ?: emptyList()
                onMessagesChanged(messages)
            }
    }

    fun listenForUserChats(
        currentUserId: String,
        onChatsChanged: (List<Chat>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                val chats = snapshot?.documents?.mapNotNull {
                    it.toObject(Chat::class.java)
                } ?: emptyList()
                onChatsChanged(chats)
            }
    }

    suspend fun getUserChatsOnce(currentUserId: String): Result<List<Chat>> {
        return try {
            val snapshot = db.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .get().await()
            Result.success(snapshot.documents.mapNotNull { it.toObject(Chat::class.java) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(
        chatId: String,
        messageId: String,
        userId: String,
        emoji: String,
        currentReaction: String?
    ): Result<Unit> {
        return try {
            val messageRef = db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            if (currentReaction == emoji) {
                messageRef.update("reactions.$userId", FieldValue.delete()).await()
            } else {
                messageRef.update("reactions.$userId", emoji).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            Result.success(snapshot.toObject(User::class.java) ?: User())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findUserByEmail(email: String): Result<User?> {
        return try {
            val snapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1).get().await()
            Result.success(snapshot.documents.firstOrNull()?.toObject(User::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Typing indicator ─────────────────────────────────────────────────────

    suspend fun setTyping(chatId: String, userId: String, isTyping: Boolean) {
        try {
            val value: Any = if (isTyping) true else FieldValue.delete()
            db.collection("chats").document(chatId)
                .update("typingUsers.$userId", value)
                .await()
        } catch (_: Exception) { }
    }

    fun listenForTyping(
        chatId: String,
        onTypingChanged: (Set<String>) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val typingUsers = snapshot.get("typingUsers") as? Map<String, Boolean> ?: emptyMap()
                onTypingChanged(typingUsers.filterValues { it }.keys)
            }
    }
}