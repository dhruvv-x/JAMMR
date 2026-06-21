package com.pookie.jammr.data.repository

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
                    "lastMessageSenderId" to ""
                )
                chatRef.set(newChat).await()
            }
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a message inside a chat. Updates the chat document's
     * lastMessage/lastMessageTimestamp fields at the same time, so the
     * chat list screen can show an up-to-date preview without extra queries.
     */
    suspend fun sendMessage(chatId: String, message: Message): Result<Unit> {
        return try {
            val messageRef = db.collection("chats").document(chatId)
                .collection("messages").document()

            val messageWithId = message.copy(messageId = messageRef.id)
            messageRef.set(messageWithId).await()

            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessage" to if (message.type == "song") "🎵 Shared a song" else message.text,
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessageSenderId" to message.senderId
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listens for real-time message updates inside a chat.
     * Returns a ListenerRegistration so the caller can remove the listener
     * when the screen is destroyed (prevents memory leaks / unwanted callbacks).
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
     * Listens for real-time updates to the current user's chat list
     * (used on the Chat List / Home screen).
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
     * Fetches a user's profile by UID — used to display the other
     * participant's name/photo in the chat list and chat screen header.
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
     * Looks up a user by their exact email address — used by the
     * temporary "start a new chat by email" flow until a real Friend
     * System exists. Returns null (inside a successful Result) if no
     * user with that email is found, distinct from a query failure.
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