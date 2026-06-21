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
 * relative to whoever is currently viewing the list. The raw Chat model
 * doesn't carry a fixed "other user" — that depends on who's looking.
 */
data class ChatPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserPhotoUrl: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long
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

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private var chatsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    // Avoids re-fetching the same user's profile repeatedly as the chat list
    // updates in real time (e.g. every time a new message arrives).
    private val userProfileCache = mutableMapOf<String, User>()

    private val _chatListState = MutableLiveData<ChatListState>()
    val chatListState: LiveData<ChatListState> = _chatListState

    private val _messagesState = MutableLiveData<MessagesState>()
    val messagesState: LiveData<MessagesState> = _messagesState

    private val _currentChatId = MutableLiveData<String>()
    val currentChatId: LiveData<String> = _currentChatId

    private val _userSearchState = MutableLiveData<UserSearchState>(UserSearchState.Idle)
    val userSearchState: LiveData<UserSearchState> = _userSearchState

    /** Starts listening for the current user's chat list (Chat List screen). */
    fun loadUserChats(currentUserId: String) {
        _chatListState.value = ChatListState.Loading
        chatsListener?.remove()
        chatsListener = repository.listenForUserChats(
            currentUserId = currentUserId,
            onChatsChanged = { chats -> enrichAndEmitChats(chats, currentUserId) },
            onError = { e -> _chatListState.value = ChatListState.Error(e.message ?: "Failed to load chats") }
        )
    }

    /**
     * Resolves each chat's "other participant" and fetches their profile
     * (cached) before exposing the list to the UI.
     */
    private fun enrichAndEmitChats(chats: List<Chat>, currentUserId: String) {
        viewModelScope.launch {
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
            _chatListState.value = ChatListState.Success(previews)
        }
    }

    /** Opens (or creates) a chat with another user, then starts listening for its messages. */
    fun openChatWith(currentUserId: String, otherUserId: String) {
        _messagesState.value = MessagesState.Loading
        viewModelScope.launch {
            val result = repository.getOrCreateChat(currentUserId, otherUserId)
            if (result.isSuccess) {
                val chatId = result.getOrNull()!!
                _currentChatId.value = chatId
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

    /** Sends a plain text message in the currently open chat. */
    fun sendTextMessage(senderId: String, text: String) {
        val chatId = _currentChatId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val message = Message(
                senderId = senderId,
                text = text,
                type = "text",
                timestamp = System.currentTimeMillis()
            )
            repository.sendMessage(chatId, message)
        }
    }

    suspend fun fetchUserProfile(uid: String): User? {
        val result = repository.getUserProfile(uid)
        return result.getOrNull()
    }

    /**
     * Looks up a user by email for the "start new chat" flow.
     * Trims and lowercases the input since emails are case-insensitive
     * and stray whitespace from copy-paste is common.
     */
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

    /** Resets the search state — call after handling a Found/NotFound/Error result. */
    fun clearUserSearchState() {
        _userSearchState.value = UserSearchState.Idle
    }

    /** Call from Fragment's onDestroyView() to avoid leaking Firestore listeners. */
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