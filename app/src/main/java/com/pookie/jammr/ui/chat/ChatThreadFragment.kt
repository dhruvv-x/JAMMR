package com.pookie.jammr.ui.chat

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.pookie.jammr.R
import com.pookie.jammr.data.model.Message
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

    private lateinit var replyPreviewBar: View
    private lateinit var tvReplyPreviewSender: TextView
    private lateinit var tvReplyPreviewText: TextView
    private lateinit var btnCancelReply: ImageButton

    // The message currently being replied to, if any. Cleared after sending
    // or when the user taps the cancel (X) button on the preview bar.
    private var pendingReplyTo: Message? = null

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
        replyPreviewBar = view.findViewById(R.id.replyPreviewBar)
        tvReplyPreviewSender = view.findViewById(R.id.tvReplyPreviewSender)
        tvReplyPreviewText = view.findViewById(R.id.tvReplyPreviewText)
        btnCancelReply = view.findViewById(R.id.btnCancelReply)

        // Push the whole screen up by exactly the keyboard's height when it opens,
        // and back down when it closes. Needed because enableEdgeToEdge() in
        // MainActivity makes the app draw behind the keyboard, so without this
        // the on-screen keyboard would cover the message input box.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight)
            insets
        }

        val otherUserId = arguments?.getString("otherUserId") ?: return
        val otherUserName = arguments?.getString("otherUserName") ?: "Chat"

        tvThreadTitle.text = otherUserName

        val uid = currentUserId ?: return

        adapter = MessageAdapter(emptyList(), uid) { message, anchorView ->
            showMessageActionsPopup(message, anchorView, uid)
        }
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also {
            it.stackFromEnd = true
        }
        rvMessages.adapter = adapter

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        btnCancelReply.setOnClickListener {
            clearPendingReply()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendTextMessage(uid, text, replyTo = pendingReplyTo)
                etMessage.setText("")
                clearPendingReply()
            }
        }

        chatViewModel.openChatWith(uid, otherUserId)
        observeMessages()
    }

    /**
     * Long-press popup: a row of quick-react emojis plus a "Reply" option,
     * anchored right above/below the bubble that was pressed.
     */
    private fun showMessageActionsPopup(message: Message, anchorView: View, currentUserId: String) {
        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.popup_message_actions, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 8f

        val emojiIds = listOf(
            R.id.emoji1, R.id.emoji2, R.id.emoji3,
            R.id.emoji4, R.id.emoji5, R.id.emoji6
        )
        for (id in emojiIds) {
            val emojiView = popupView.findViewById<TextView>(id)
            emojiView.setOnClickListener {
                chatViewModel.toggleReaction(message, currentUserId, emojiView.text.toString())
                popupWindow.dismiss()
            }
        }

        popupView.findViewById<TextView>(R.id.btnReplyOption).setOnClickListener {
            setPendingReply(message, currentUserId)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height - 16)
    }

    /** Shows the reply preview bar above the input box for the given message. */
    private fun setPendingReply(message: Message, currentUserId: String) {
        pendingReplyTo = message
        tvReplyPreviewSender.text = if (message.senderId == currentUserId) "Replying to yourself" else "Replying to ${tvThreadTitle.text}"
        tvReplyPreviewText.text = if (message.type == "song") "🎵 ${message.songTrackName}" else message.text
        replyPreviewBar.visibility = View.VISIBLE
        etMessage.requestFocus()
    }

    private fun clearPendingReply() {
        pendingReplyTo = null
        replyPreviewBar.visibility = View.GONE
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
