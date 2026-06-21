package com.pookie.jammr.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pookie.jammr.R

/**
 * STUB — full message bubble UI, input box, and send wiring come in the
 * next step. For now this just confirms navigation + args are working:
 * tapping a chat (existing or newly started) lands here with the right
 * otherUserId/otherUserName.
 */
class ChatThreadFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val otherUserName = arguments?.getString("otherUserName") ?: "Chat"
        view.findViewById<TextView>(R.id.tvThreadTitle).text = otherUserName
    }
}