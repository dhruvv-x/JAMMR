package com.pookie.jammr.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.ChatViewModel
import com.pookie.jammr.viewmodel.MessagesState

class ChatThreadFragment : Fragment() {

    private val chatViewModel: ChatViewModel by viewModels()

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvThreadTitle: TextView
    private lateinit var adapter: MessageAdapter

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMessages = view.findViewById(R.id.rvMessages)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        btnBack = view.findViewById(R.id.btnBack)
        tvThreadTitle = view.findViewById(R.id.tvThreadTitle)

        val otherUserId = arguments?.getString("otherUserId") ?: return
        val otherUserName = arguments?.getString("otherUserName") ?: "Chat"

        tvThreadTitle.text = otherUserName

        val uid = currentUserId ?: return

        adapter = MessageAdapter(emptyList(), uid)
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also {
            it.stackFromEnd = true
        }
        rvMessages.adapter = adapter

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendTextMessage(uid, text)
                etMessage.setText("")
            }
        }

        chatViewModel.openChatWith(uid, otherUserId)
        observeMessages()
    }

    private fun observeMessages() {
        chatViewModel.messagesState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MessagesState.Success -> {
                    adapter.updateMessages(state.messages)
                    if (state.messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(state.messages.size - 1)
                    }
                }
                is MessagesState.Loading -> Unit
                is MessagesState.Error -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatViewModel.stopListeningToMessages()
    }
}