package com.pookie.jammr.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.jammr.data.model.Song
import com.pookie.jammr.data.repository.MusicRepository
import kotlinx.coroutines.launch

sealed class MusicState {
    object Loading : MusicState()
    data class Success(val songs: List<Song>) : MusicState()
    data class Error(val message: String) : MusicState()
}

/**
 * Represents one horizontal "shelf" on the Home screen, e.g. Bollywood, Hollywood, Punjabi.
 * displayTitle = what's shown to the user, searchTerm = what's sent to iTunes Search API.
 */
data class MusicShelf(
    val id: String,
    val displayTitle: String,
    val searchTerm: String
)

class MusicViewModel : ViewModel() {

    private val repository = MusicRepository()

    // Default shelves shown on Home. Easy to extend later without touching Fragment code.
    val homeShelves = listOf(
        MusicShelf("bollywood", "🔥 Trending Bollywood", "bollywood hits 2025"),
        MusicShelf("hollywood", "🌍 Trending Hollywood", "top pop hits 2025"),
        MusicShelf("punjabi", "🎧 Punjabi Hits", "punjabi hits 2025")
    )

    private val _shelfStates = MutableLiveData<Map<String, MusicState>>(emptyMap())
    val shelfStates: LiveData<Map<String, MusicState>> = _shelfStates

    private val _searchState = MutableLiveData<MusicState>()
    val searchState: LiveData<MusicState> = _searchState

    /**
     * Loads all Home shelves in parallel (each shelf is independent —
     * one failing doesn't block the others from loading/showing).
     */
    fun loadHomeShelves() {
        homeShelves.forEach { shelf ->
            updateShelfState(shelf.id, MusicState.Loading)
            viewModelScope.launch {
                val result = repository.searchSongs(shelf.searchTerm)
                val newState = if (result.isSuccess) {
                    MusicState.Success(result.getOrNull() ?: emptyList())
                } else {
                    MusicState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
                }
                updateShelfState(shelf.id, newState)
            }
        }
    }

    private fun updateShelfState(shelfId: String, state: MusicState) {
        val current = _shelfStates.value ?: emptyMap()
        _shelfStates.value = current + (shelfId to state)
    }

    fun searchSongs(query: String) {
        _searchState.value = MusicState.Loading
        viewModelScope.launch {
            val result = repository.searchSongs(query)
            _searchState.value = if (result.isSuccess) {
                MusicState.Success(result.getOrNull() ?: emptyList())
            } else {
                MusicState.Error(result.exceptionOrNull()?.message ?: "Search failed")
            }
        }
    }
}