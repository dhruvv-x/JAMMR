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
    data class Success(val chats: List<Chat>) : ChatListState()
    data class Error(val message: String) : ChatListState()
}

sealed class MessagesState {
    object Loading : MessagesState()
    data class Success(val messages: List<Message>) : MessagesState()
    data class Error(val message: String) : MessagesState()
}

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private var chatsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    private val _chatListState = MutableLiveData<ChatListState>()
    val chatListState: LiveData<ChatListState> = _chatListState

    private val _messagesState = MutableLiveData<MessagesState>()
    val messagesState: LiveData<MessagesState> = _messagesState

    private val _currentChatId = MutableLiveData<String>()
    val currentChatId: LiveData<String> = _currentChatId

    /** Starts listening for the current user's chat list (Chat List screen). */
    fun loadUserChats(currentUserId: String) {
        _chatListState.value = ChatListState.Loading
        chatsListener?.remove()
        chatsListener = repository.listenForUserChats(
            currentUserId = currentUserId,
            onChatsChanged = { chats -> _chatListState.value = ChatListState.Success(chats) },
            onError = { e -> _chatListState.value = ChatListState.Error(e.message ?: "Failed to load chats") }
        )
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