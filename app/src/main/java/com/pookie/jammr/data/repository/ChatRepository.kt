package com.pookie.jammr.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.pookie.jammr.data.model.Chat
import com.pookie.jammr.data.model.Message
import com.pookie.jammr.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val httpClient = OkHttpClient()

    // Cloudinary unsigned-upload config. Safe to keep in client code — unlike
    // an API secret, the cloud name + an UNSIGNED preset name are not
    // sensitive: the preset itself (configured in the Cloudinary console)
    // is what restricts what an unsigned upload is allowed to do.
    private val cloudinaryCloudName = "dk5jamsan"
    private val cloudinaryUploadPreset = "jammr_unsigned"

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
                    "lastMessage" to when (message.type) {
                        "song" -> "🎵 Shared a song"
                        "image" -> "📷 Photo"
                        "video" -> "🎥 Video"
                        else -> message.text
                    },
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

    // ── Media (photo / video) sharing — via Cloudinary unsigned upload ──────

    /**
     * Uploads a photo or video to Cloudinary using an unsigned upload preset.
     * Cloudinary's REST API needs an actual file on disk (or a byte stream),
     * so the picked content:// Uri is first copied into the app's cache
     * directory, uploaded, then deleted regardless of outcome.
     *
     * [onProgress] is best-effort: OkHttp's RequestBody doesn't expose
     * granular byte-level progress without a custom wrapper, so this reports
     * 0 while uploading and 100 once the request completes. Good enough to
     * drive an indeterminate-feeling progress bar without misleading numbers.
     */
    suspend fun uploadChatMedia(
        chatId: String,
        fileUri: Uri,
        isVideo: Boolean,
        context: Context,
        onProgress: (Int) -> Unit
    ): Result<String> {
        var tempFile: File? = null
        return try {
            onProgress(0)
            tempFile = copyUriToTempFile(context, fileUri, isVideo)

            val resourceType = if (isVideo) "video" else "image"
            val mediaType = (if (isVideo) "video/mp4" else "image/jpeg").toMediaTypeOrNull()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", cloudinaryUploadPreset)
                .addFormDataPart("folder", "jammr_chat_media/$chatId")
                .addFormDataPart(
                    "file", tempFile.name,
                    tempFile.asRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudinaryCloudName/$resourceType/upload")
                .post(requestBody)
                .build()

            val responseBody = executeRequest(request)
            onProgress(100)

            val secureUrl = JSONObject(responseBody).getString("secure_url")
            Result.success(secureUrl)
        } catch (e: Exception) {
            android.util.Log.e("JAMMR_UPLOAD", "uploadChatMedia failed", e)
            Result.failure(Exception(e.message ?: e.javaClass.simpleName, e))
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Uploads a video's thumbnail bitmap (already extracted by the caller)
     * as a small JPEG, separately from the full video. Keeping it separate
     * means the chat list/thread can render instantly without ever touching
     * the (much larger) video file.
     */
    suspend fun uploadChatMediaThumbnail(
        chatId: String,
        thumbnailBytes: ByteArray
    ): Result<String> {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", cloudinaryUploadPreset)
                .addFormDataPart("folder", "jammr_chat_media/$chatId/thumbnails")
                .addFormDataPart(
                    "file", "thumb.jpg",
                    thumbnailBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudinaryCloudName/image/upload")
                .post(requestBody)
                .build()

            val responseBody = executeRequest(request)
            val secureUrl = JSONObject(responseBody).getString("secure_url")
            Result.success(secureUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Runs an OkHttp request on the IO dispatcher and returns the response body as a string. */
    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            android.util.Log.e("JAMMR_UPLOAD", "Cloudinary error \${response.code}: \$body")
            throw Exception("Cloudinary \${response.code}: \$body")
        }
        body
    }

    /** Copies a content:// Uri into a temp file in the app's cache dir so Cloudinary's API can read it as a File. */
    private fun copyUriToTempFile(context: Context, uri: Uri, isVideo: Boolean): File {
        val extension = if (isVideo) "mp4" else "jpg"
        val tempFile = File.createTempFile("upload_", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Could not read selected file")
        return tempFile
    }
}