package com.pookie.jammr.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.pookie.jammr.data.model.Chat
import com.pookie.jammr.data.model.Message
import com.pookie.jammr.data.model.User
import com.pookie.jammr.data.repository.ChatRepository
import kotlinx.coroutines.launch

sealed class ChatListState {
    object Loading : ChatListState()
    data class Success(val chats: List<ChatPreview>) : ChatListState()
    data class Error(val message: String) : ChatListState()
}

/**
 * A Chat document enriched with the OTHER participant's profile, resolved
 * relative to whoever is currently viewing the list. Also carries the
 * current user's unread count for this chat.
 */
data class ChatPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserPhotoUrl: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0
)

sealed class MessagesState {
    object Loading : MessagesState()
    data class Success(val messages: List<Message>) : MessagesState()
    data class Error(val message: String) : MessagesState()
}

sealed class UserSearchState {
    object Idle : UserSearchState()
    object Loading : UserSearchState()
    data class Found(val user: User) : UserSearchState()
    object NotFound : UserSearchState()
    data class Error(val message: String) : UserSearchState()
}

sealed class ForwardPickerState {
    object Idle : ForwardPickerState()
    object Loading : ForwardPickerState()
    data class Ready(val chats: List<ChatPreview>) : ForwardPickerState()
    data class Error(val message: String) : ForwardPickerState()
}

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private var chatsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    private val userProfileCache = mutableMapOf<String, User>()

    private val _chatListState = MutableLiveData<ChatListState>()
    val chatListState: LiveData<ChatListState> = _chatListState

    private val _messagesState = MutableLiveData<MessagesState>()
    val messagesState: LiveData<MessagesState> = _messagesState

    private val _currentChatId = MutableLiveData<String>()
    val currentChatId: LiveData<String> = _currentChatId

    // Tracks the other user in the currently open chat, needed to increment
    // their unread count when we send a message.
    private var currentOtherUserId: String? = null

    private val _userSearchState = MutableLiveData<UserSearchState>(UserSearchState.Idle)
    val userSearchState: LiveData<UserSearchState> = _userSearchState

    private val _forwardPickerState = MutableLiveData<ForwardPickerState>(ForwardPickerState.Idle)
    val forwardPickerState: LiveData<ForwardPickerState> = _forwardPickerState

    fun loadUserChats(currentUserId: String) {
        _chatListState.value = ChatListState.Loading
        chatsListener?.remove()
        chatsListener = repository.listenForUserChats(
            currentUserId = currentUserId,
            onChatsChanged = { chats -> enrichAndEmitChats(chats, currentUserId) },
            onError = { e -> _chatListState.value = ChatListState.Error(e.message ?: "Failed to load chats") }
        )
    }

    private fun enrichAndEmitChats(chats: List<Chat>, currentUserId: String) {
        viewModelScope.launch {
            val previews = chats.map { chat ->
                val otherUserId = chat.participants.firstOrNull { it != currentUserId } ?: ""
                val otherUser = userProfileCache[otherUserId] ?: run {
                    val fetched = repository.getUserProfile(otherUserId).getOrNull()
                    if (fetched != null) userProfileCache[otherUserId] = fetched
                    fetched
                }
                val unread = (chat.unreadCounts[currentUserId] ?: 0L).toInt()
                ChatPreview(
                    chatId = chat.chatId,
                    otherUserId = otherUserId,
                    otherUserName = otherUser?.name?.takeIf { it.isNotBlank() } ?: "Unknown user",
                    otherUserPhotoUrl = otherUser?.photoUrl,
                    lastMessage = chat.lastMessage,
                    lastMessageTimestamp = chat.lastMessageTimestamp,
                    unreadCount = unread
                )
            }
            _chatListState.value = ChatListState.Success(previews)
        }
    }

    /** Opens (or creates) a chat, resets unread count, then starts listening for messages. */
    fun openChatWith(currentUserId: String, otherUserId: String) {
        _messagesState.value = MessagesState.Loading
        currentOtherUserId = otherUserId
        viewModelScope.launch {
            val result = repository.getOrCreateChat(currentUserId, otherUserId)
            if (result.isSuccess) {
                val chatId = result.getOrNull()!!
                _currentChatId.value = chatId
                // Reset unread count as soon as the thread opens
                repository.resetUnreadCount(chatId, currentUserId)
                listenToMessages(chatId)
            } else {
                _messagesState.value = MessagesState.Error(
                    result.exceptionOrNull()?.message ?: "Could not open chat"
                )
            }
        }
    }

    private fun listenToMessages(chatId: String) {
        messagesListener?.remove()
        messagesListener = repository.listenForMessages(
            chatId = chatId,
            onMessagesChanged = { messages -> _messagesState.value = MessagesState.Success(messages) },
            onError = { e -> _messagesState.value = MessagesState.Error(e.message ?: "Failed to load messages") }
        )
    }

    fun sendTextMessage(senderId: String, text: String, replyTo: Message? = null) {
        val chatId = _currentChatId.value ?: return
        val otherUserId = currentOtherUserId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val message = Message(
                senderId = senderId,
                text = text,
                type = "text",
                timestamp = System.currentTimeMillis(),
                replyToMessageId = replyTo?.messageId,
                replyToSenderId = replyTo?.senderId,
                replyToText = replyTo?.let { if (it.type == "song") "🎵 ${it.songTrackName}" else it.text }
            )
            repository.sendMessage(chatId, message, otherUserId)
        }
    }

    fun toggleReaction(message: Message, userId: String, emoji: String) {
        val chatId = _currentChatId.value ?: return
        viewModelScope.launch {
            repository.toggleReaction(
                chatId = chatId,
                messageId = message.messageId,
                userId = userId,
                emoji = emoji,
                currentReaction = message.reactions[userId]
            )
        }
    }

    fun loadChatsForForwarding(currentUserId: String) {
        _forwardPickerState.value = ForwardPickerState.Loading
        viewModelScope.launch {
            val result = repository.getUserChatsOnce(currentUserId)
            if (result.isFailure) {
                _forwardPickerState.value = ForwardPickerState.Error(
                    result.exceptionOrNull()?.message ?: "Couldn't load chats"
                )
                return@launch
            }
            val chats = result.getOrNull() ?: emptyList()
            val previews = chats.map { chat ->
                val otherUserId = chat.participants.firstOrNull { it != currentUserId } ?: ""
                val otherUser = userProfileCache[otherUserId] ?: run {
                    val fetched = repository.getUserProfile(otherUserId).getOrNull()
                    if (fetched != null) userProfileCache[otherUserId] = fetched
                    fetched
                }
                ChatPreview(
                    chatId = chat.chatId,
                    otherUserId = otherUserId,
                    otherUserName = otherUser?.name?.takeIf { it.isNotBlank() } ?: "Unknown user",
                    otherUserPhotoUrl = otherUser?.photoUrl,
                    lastMessage = chat.lastMessage,
                    lastMessageTimestamp = chat.lastMessageTimestamp
                )
            }
            _forwardPickerState.value = ForwardPickerState.Ready(previews)
        }
    }

    fun clearForwardPickerState() {
        _forwardPickerState.value = ForwardPickerState.Idle
    }

    fun forwardMessage(message: Message, targetChatId: String, senderId: String) {
        // For forward we need the other user in that target chat — look it up from cached state
        val targetChat = (_forwardPickerState.value as? ForwardPickerState.Ready)
            ?.chats?.firstOrNull { it.chatId == targetChatId }
        val otherUserId = targetChat?.otherUserId ?: return
        viewModelScope.launch {
            val forwarded = Message(
                senderId = senderId,
                text = message.text,
                type = message.type,
                timestamp = System.currentTimeMillis(),
                songTrackId = message.songTrackId,
                songTrackName = message.songTrackName,
                songArtistName = message.songArtistName,
                songArtworkUrl = message.songArtworkUrl,
                songPreviewUrl = message.songPreviewUrl
            )
            repository.sendMessage(targetChatId, forwarded, otherUserId)
        }
    }

    suspend fun fetchUserProfile(uid: String): User? {
        val result = repository.getUserProfile(uid)
        return result.getOrNull()
    }

    fun searchUserByEmail(email: String) {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) {
            _userSearchState.value = UserSearchState.Error("Enter an email address")
            return
        }
        _userSearchState.value = UserSearchState.Loading
        viewModelScope.launch {
            val result = repository.findUserByEmail(normalized)
            _userSearchState.value = when {
                result.isFailure -> UserSearchState.Error(result.exceptionOrNull()?.message ?: "Search failed")
                result.getOrNull() == null -> UserSearchState.NotFound
                else -> UserSearchState.Found(result.getOrNull()!!)
            }
        }
    }

    fun clearUserSearchState() {
        _userSearchState.value = UserSearchState.Idle
    }

    fun stopListeningToMessages() {
        messagesListener?.remove()
        messagesListener = null
    }

    override fun onCleared() {
        super.onCleared()
        chatsListener?.remove()
        messagesListener?.remove()
    }
}