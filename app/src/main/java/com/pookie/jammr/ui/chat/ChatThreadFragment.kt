package com.pookie.jammr.ui.chat

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.pookie.jammr.viewmodel.ForwardPickerState
import com.pookie.jammr.viewmodel.MediaUploadState
import com.pookie.jammr.viewmodel.MessagesState
import java.io.ByteArrayOutputStream

class ChatThreadFragment : Fragment() {

    private val chatViewModel: ChatViewModel by viewModels()

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var tvThreadTitle: TextView
    private lateinit var tvTypingIndicator: TextView
    private lateinit var adapter: MessageAdapter

    private lateinit var replyPreviewBar: View
    private lateinit var tvReplyPreviewSender: TextView
    private lateinit var tvReplyPreviewText: TextView
    private lateinit var btnCancelReply: ImageButton

    private var pendingReplyTo: Message? = null
    private var pendingForwardMessage: Message? = null
    private var activeForwardDialog: AlertDialog? = null

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // Modern system photo/video picker — no storage permission needed at all,
    // since it runs as a separate trusted system process and only hands back
    // a content:// Uri for the one item the user picked.
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) showMediaPreview(uris)
    }

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
        btnAttach = view.findViewById(R.id.btnAttach)
        uploadProgressBar = view.findViewById(R.id.uploadProgressBar)
        tvThreadTitle = view.findViewById(R.id.tvThreadTitle)
        tvTypingIndicator = view.findViewById(R.id.tvTypingIndicator)
        replyPreviewBar = view.findViewById(R.id.replyPreviewBar)
        tvReplyPreviewSender = view.findViewById(R.id.tvReplyPreviewSender)
        tvReplyPreviewText = view.findViewById(R.id.tvReplyPreviewText)
        btnCancelReply = view.findViewById(R.id.btnCancelReply)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight)
            insets
        }

        val otherUserId = arguments?.getString("otherUserId") ?: return
        val otherUserName = arguments?.getString("otherUserName") ?: "Chat"

        tvThreadTitle.text = otherUserName

        val uid = currentUserId ?: return

        adapter = MessageAdapter(
            messages = emptyList(),
            currentUserId = uid,
            onMessageLongPress = { message, anchorView -> showMessageActionsPopup(message, anchorView, uid) },
            onMediaClick = { message -> openMediaViewer(message) }
        )
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also {
            it.stackFromEnd = true
        }
        rvMessages.adapter = adapter

        btnBack.setOnClickListener {
            chatViewModel.clearTyping(uid)
            findNavController().popBackStack()
        }

        btnCancelReply.setOnClickListener { clearPendingReply() }

        btnAttach.setOnClickListener {
            pickMedia.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendTextMessage(uid, text, replyTo = pendingReplyTo)
                etMessage.setText("")
                clearPendingReply()
                // Clear typing immediately after sending
                chatViewModel.clearTyping(uid)
            }
        }

        // Typing indicator: notify ViewModel on every text change
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                chatViewModel.onUserTyping(uid, s?.isNotEmpty() == true)
            }
        })

        chatViewModel.openChatWith(uid, otherUserId)

        // Start listening for typing AFTER openChatWith so chatId is set
        // We observe currentChatId and kick off typing listener once we have it
        chatViewModel.currentChatId.observe(viewLifecycleOwner) { chatId ->
            if (!chatId.isNullOrBlank()) {
                chatViewModel.listenForTyping(otherUserId)
            }
        }

        observeMessages()
        observeTyping()
        observeForwardPicker(uid)
        observeMediaUpload()
    }

    private fun observeTyping() {
        chatViewModel.isOtherUserTyping.observe(viewLifecycleOwner) { isTyping ->
            tvTypingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
        }
    }

    /**
     * Called once the user picks a photo or video from the system picker.
     * Determines the media type from the returned Uri's MIME type, extracts
     * a thumbnail frame for videos (needed since we never want to download
     * the full video just to render its bubble), then hands off to the
     * ViewModel to upload + send.
     */
    private fun showMediaPreview(uris: List<Uri>) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_media_preview, null)
        dialog.setContentView(sheetView)

        val container = sheetView.findViewById<LinearLayout>(R.id.previewContainer)
        val tvCount = sheetView.findViewById<TextView>(R.id.tvMediaCount)
        val btnSend = sheetView.findViewById<android.widget.Button>(R.id.btnSendMedia)

        tvCount.text = if (uris.size == 1) "1 item selected" else "${uris.size} items selected"

        val size = resources.getDimensionPixelSize(android.R.dimen.thumbnail_height).coerceAtLeast(200)
        uris.forEach { uri ->
            val iv = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = 8
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this).load(uri).centerCrop().into(iv)
            container.addView(iv)
        }

        btnSend.setOnClickListener {
            dialog.dismiss()
            sendPickedMediaList(uris)
        }

        dialog.show()
    }

    private fun sendPickedMediaList(uris: List<Uri>) {
        val uid = currentUserId ?: return
        uris.forEach { uri ->
            val mimeType = requireContext().contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video")
            if (isVideo) {
                val thumbnailBytes = extractVideoThumbnailBytes(uri)
                chatViewModel.sendMediaMessage(uid, uri, isVideo = true, videoThumbnailBytes = thumbnailBytes, context = requireContext().applicationContext)
            } else {
                chatViewModel.sendMediaMessage(uid, uri, isVideo = false, videoThumbnailBytes = null, context = requireContext().applicationContext)
            }
        }
    }

    /**
     * Grabs a single frame from the video (at the 1-second mark, falling
     * back to frame 0 for very short clips) and JPEG-encodes it. Returns
     * null on any failure — sendMediaMessage() handles a null thumbnail
     * gracefully by falling back to showing the full video URL instead.
     */
    private fun extractVideoThumbnailBytes(uri: Uri): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val frame: Bitmap? = retriever.getFrameAtTime(1_000_000) ?: retriever.frameAtTime
            retriever.release()
            if (frame == null) return null

            ByteArrayOutputStream().use { stream ->
                frame.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun observeMediaUpload() {
        chatViewModel.mediaUploadState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MediaUploadState.Idle -> {
                    uploadProgressBar.visibility = View.GONE
                }
                is MediaUploadState.Uploading -> {
                    uploadProgressBar.visibility = View.VISIBLE
                    uploadProgressBar.progress = state.progress
                }
                is MediaUploadState.Done -> {
                    uploadProgressBar.visibility = View.GONE
                    chatViewModel.clearMediaUploadState()
                }
                is MediaUploadState.Error -> {
                    uploadProgressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Upload failed: ${state.message}", Toast.LENGTH_LONG).show()
                    chatViewModel.clearMediaUploadState()
                }
            }
        }
    }

    /** Opens the full-screen viewer for a tapped image/video bubble. */
    private fun openMediaViewer(message: Message) {
        val url = message.mediaUrl ?: return
        val bundle = Bundle().apply {
            putString("mediaUrl", url)
            putBoolean("isVideo", message.type == "video")
        }
        findNavController().navigate(R.id.action_chatThreadFragment_to_mediaViewerFragment, bundle)
    }

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

        popupView.findViewById<TextView>(R.id.btnForwardOption).setOnClickListener {
            popupWindow.dismiss()
            showForwardDialog(message, currentUserId)
        }

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height - 16)
    }

    private fun setPendingReply(message: Message, currentUserId: String) {
        pendingReplyTo = message
        tvReplyPreviewSender.text = if (message.senderId == currentUserId) "Replying to yourself" else "Replying to ${tvThreadTitle.text}"
        tvReplyPreviewText.text = when (message.type) {
            "song" -> "🎵 ${message.songTrackName}"
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            else -> message.text
        }
        replyPreviewBar.visibility = View.VISIBLE
        etMessage.requestFocus()
    }

    private fun clearPendingReply() {
        pendingReplyTo = null
        replyPreviewBar.visibility = View.GONE
    }

    private fun showForwardDialog(message: Message, currentUserId: String) {
        pendingForwardMessage = message

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_forward_list, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Forward to...")
            .setView(dialogView)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .setOnDismissListener {
                pendingForwardMessage = null
                activeForwardDialog = null
                chatViewModel.clearForwardPickerState()
            }
            .create()

        dialog.show()
        activeForwardDialog = dialog

        chatViewModel.loadChatsForForwarding(currentUserId)
    }

    private fun observeForwardPicker(currentUserId: String) {
        chatViewModel.forwardPickerState.observe(viewLifecycleOwner) { state ->
            val dialog = activeForwardDialog ?: return@observe
            val progressBar = dialog.findViewById<ProgressBar>(R.id.forwardProgressBar)
            val tvEmpty = dialog.findViewById<TextView>(R.id.tvForwardEmpty)
            val rvChats = dialog.findViewById<RecyclerView>(R.id.rvForwardChats)

            when (state) {
                is ForwardPickerState.Idle -> Unit
                is ForwardPickerState.Loading -> {
                    progressBar?.visibility = View.VISIBLE
                    tvEmpty?.visibility = View.GONE
                    rvChats?.visibility = View.GONE
                }
                is ForwardPickerState.Ready -> {
                    progressBar?.visibility = View.GONE
                    if (state.chats.isEmpty()) {
                        tvEmpty?.visibility = View.VISIBLE
                        rvChats?.visibility = View.GONE
                    } else {
                        tvEmpty?.visibility = View.GONE
                        rvChats?.visibility = View.VISIBLE
                        rvChats?.layoutManager = LinearLayoutManager(requireContext())
                        rvChats?.adapter = ForwardChatAdapter(state.chats) { chatPreview ->
                            val message = pendingForwardMessage
                            if (message != null) {
                                chatViewModel.forwardMessage(message, chatPreview.chatId, currentUserId)
                                Toast.makeText(requireContext(), "Forwarded to ${chatPreview.otherUserName}", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        }
                    }
                }
                is ForwardPickerState.Error -> {
                    progressBar?.visibility = View.GONE
                    tvEmpty?.visibility = View.VISIBLE
                    tvEmpty?.text = "Couldn't load chats:\n${state.message}"
                    rvChats?.visibility = View.GONE
                }
            }
        }
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
        currentUserId?.let { chatViewModel.clearTyping(it) }
        chatViewModel.stopListeningToMessages()
        chatViewModel.stopListeningToTyping()
    }
}