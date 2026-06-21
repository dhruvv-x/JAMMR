package com.pookie.jammr.ui.chat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.ChatListState
import com.pookie.jammr.viewmodel.ChatViewModel
import com.pookie.jammr.viewmodel.UserSearchState

class ChatListFragment : Fragment() {

    private val chatViewModel: ChatViewModel by viewModels()

    private lateinit var rvChats: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var fabNewChat: FloatingActionButton
    private lateinit var adapter: ChatListAdapter

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChats = view.findViewById(R.id.rvChats)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        fabNewChat = view.findViewById(R.id.fabNewChat)

        adapter = ChatListAdapter(emptyList()) { chatPreview ->
            findNavController().navigate(
                R.id.action_chatListFragment_to_chatThreadFragment,
                bundleOf(
                    "otherUserId" to chatPreview.otherUserId,
                    "otherUserName" to chatPreview.otherUserName
                )
            )
        }
        rvChats.layoutManager = LinearLayoutManager(requireContext())
        rvChats.adapter = adapter

        fabNewChat.setOnClickListener { showStartChatDialog() }

        observeChatList()
        observeUserSearch()

        val uid = currentUserId
        if (uid != null) {
            chatViewModel.loadUserChats(uid)
        } else {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeChatList() {
        chatViewModel.chatListState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ChatListState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    rvChats.visibility = View.GONE
                    tvEmptyState.visibility = View.GONE
                }
                is ChatListState.Success -> {
                    progressBar.visibility = View.GONE
                    if (state.chats.isEmpty()) {
                        rvChats.visibility = View.GONE
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        rvChats.visibility = View.VISIBLE
                        tvEmptyState.visibility = View.GONE
                        adapter.updateChats(state.chats)
                    }
                }
                is ChatListState.Error -> {
                    progressBar.visibility = View.GONE
                    rvChats.visibility = View.GONE
                    tvEmptyState.visibility = View.VISIBLE
                    tvEmptyState.text = "Couldn't load chats:\n${state.message}"
                }
            }
        }
    }

    /**
     * Temporary "start a new chat" flow until a real Friend System exists.
     * Shows a dialog asking for the other person's email, looks them up,
     * and on a match navigates straight to the Chat Thread screen.
     */
    private fun showStartChatDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_start_chat, null)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etSearchEmail)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Start a new chat")
            .setView(dialogView)
            .setPositiveButton("Search", null) // set below to control dismiss behavior
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = etEmail.text.toString()
                chatViewModel.searchUserByEmail(email)
                // Dialog itself stays open while we search; closed by observeUserSearch()
                // once we know whether the lookup succeeded.
            }
        }
        dialog.show()
        this.activeDialog = dialog
    }

    private var activeDialog: AlertDialog? = null

    private fun observeUserSearch() {
        chatViewModel.userSearchState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UserSearchState.Idle -> Unit
                is UserSearchState.Loading -> {
                    activeDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                }
                is UserSearchState.Found -> {
                    val uid = currentUserId
                    activeDialog?.dismiss()
                    if (uid != null && state.user.uid == uid) {
                        Toast.makeText(requireContext(), "That's you! Try a friend's email.", Toast.LENGTH_SHORT).show()
                    } else if (uid != null) {
                        findNavController().navigate(
                            R.id.action_chatListFragment_to_chatThreadFragment,
                            bundleOf(
                                "otherUserId" to state.user.uid,
                                "otherUserName" to state.user.name.ifBlank { state.user.email }
                            )
                        )
                    }
                    chatViewModel.clearUserSearchState()
                }
                is UserSearchState.NotFound -> {
                    activeDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    Toast.makeText(requireContext(), "No JAMMR user with that email", Toast.LENGTH_SHORT).show()
                    chatViewModel.clearUserSearchState()
                }
                is UserSearchState.Error -> {
                    activeDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    chatViewModel.clearUserSearchState()
                }
            }
        }
    }
}