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

    /**
     * Generates a deterministic chat ID for two users by sorting their UIDs
     * alphabetically and joining them. This guarantees that no matter who
     * initiates the chat, both users always land on the SAME chat document —
     * preventing duplicate chats between the same pair of people.
     */
    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    /**
     * Ensures a chat document exists between two users. If it already exists,
     * does nothing (safe to call every time a chat screen opens).
     * Returns the chatId either way.
     */
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

    /**
     * Sends a message inside a chat.
     * - Updates lastMessage/lastMessageTimestamp on the chat document.
     * - Increments the OTHER user's unread count by 1.
     * The sender's own count is not touched (they just sent it, they've seen it).
     */
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

    /**
     * Resets the current user's unread count to 0.
     * Call this when the user opens a chat thread.
     */
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

    /**
     * Listens for real-time message updates inside a chat.
     */
    fun listenForMessages(
        chatId: String,
        onMessagesChanged: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull {
                    it.toObject(Message::class.java)
                } ?: emptyList()
                onMessagesChanged(messages)
            }
    }

    /**
     * Listens for real-time updates to the current user's chat list.
     */
    fun listenForUserChats(
        currentUserId: String,
        onChatsChanged: (List<Chat>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull {
                    it.toObject(Chat::class.java)
                } ?: emptyList()
                onChatsChanged(chats)
            }
    }

    /**
     * One-time fetch of all chats — used by the "Forward message" picker.
     */
    suspend fun getUserChatsOnce(currentUserId: String): Result<List<Chat>> {
        return try {
            val snapshot = db.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()
            val chats = snapshot.documents.mapNotNull { it.toObject(Chat::class.java) }
            Result.success(chats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds, changes, or removes the current user's reaction on a message.
     */
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

    /**
     * Fetches a user's profile by UID.
     */
    suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java) ?: User()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Looks up a user by their exact email address.
     */
    suspend fun findUserByEmail(email: String): Result<User?> {
        return try {
            val snapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}