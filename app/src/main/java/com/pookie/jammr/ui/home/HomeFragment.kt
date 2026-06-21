package com.pookie.jammr.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.AuthViewModel
import com.pookie.jammr.viewmodel.MusicShelf
import com.pookie.jammr.viewmodel.MusicState
import com.pookie.jammr.viewmodel.MusicViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private val musicViewModel: MusicViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var tvGreeting: TextView
    private lateinit var btnLogout: ImageButton
    private lateinit var shelvesContainer: ViewGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // Holds references to each shelf's views, keyed by shelf id, so we can update them individually.
    private val shelfViewsMap = mutableMapOf<String, ShelfViews>()

    private data class ShelfViews(
        val progressBar: ProgressBar,
        val errorText: TextView,
        val recyclerView: RecyclerView,
        val adapter: SongAdapter
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvGreeting = view.findViewById(R.id.tvGreeting)
        btnLogout = view.findViewById(R.id.btnLogout)
        shelvesContainer = view.findViewById(R.id.shelvesContainer)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)

        tvGreeting.text = buildGreeting()

        btnLogout.setOnClickListener {
            authViewModel.signOut()
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }

        buildShelfSections()
        observeShelfStates()

        musicViewModel.loadHomeShelves()

        swipeRefresh.setOnRefreshListener {
            musicViewModel.loadHomeShelves()
        }
    }

    private fun buildGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning 🎵"
            hour < 17 -> "Good afternoon 🎵"
            else -> "Good evening 🎵"
        }
    }

    /**
     * Inflates one section_music_shelf.xml per shelf defined in MusicViewModel.homeShelves,
     * adds it to the container, and stores references for later state updates.
     */
    private fun buildShelfSections() {
        val inflater = LayoutInflater.from(requireContext())

        musicViewModel.homeShelves.forEach { shelf ->
            val shelfView = inflater.inflate(R.layout.section_music_shelf, shelvesContainer, false)

            val tvTitle = shelfView.findViewById<TextView>(R.id.tvShelfTitle)
            val progressBar = shelfView.findViewById<ProgressBar>(R.id.shelfProgressBar)
            val errorText = shelfView.findViewById<TextView>(R.id.tvShelfError)
            val recyclerView = shelfView.findViewById<RecyclerView>(R.id.rvShelfSongs)

            tvTitle.text = shelf.displayTitle

            val adapter = SongAdapter(emptyList()) { song ->
                // TODO: navigate to Song Detail screen — built in a later step
            }
            recyclerView.layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            recyclerView.adapter = adapter

            shelfViewsMap[shelf.id] = ShelfViews(progressBar, errorText, recyclerView, adapter)
            shelvesContainer.addView(shelfView)
        }
    }

    private fun observeShelfStates() {
        musicViewModel.shelfStates.observe(viewLifecycleOwner) { statesMap ->
            swipeRefresh.isRefreshing = false

            statesMap.forEach { (shelfId, state) ->
                val views = shelfViewsMap[shelfId] ?: return@forEach

                when (state) {
                    is MusicState.Loading -> {
                        views.progressBar.visibility = View.VISIBLE
                        views.errorText.visibility = View.GONE
                        views.recyclerView.visibility = View.GONE
                    }
                    is MusicState.Success -> {
                        views.progressBar.visibility = View.GONE
                        views.errorText.visibility = View.GONE
                        views.recyclerView.visibility = View.VISIBLE
                        views.adapter.updateSongs(state.songs)
                    }
                    is MusicState.Error -> {
                        views.progressBar.visibility = View.GONE
                        views.recyclerView.visibility = View.GONE
                        views.errorText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}