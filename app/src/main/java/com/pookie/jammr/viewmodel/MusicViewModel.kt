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

class MusicViewModel : ViewModel() {

    private val repository = MusicRepository()

    private val _trendingState = MutableLiveData<MusicState>()
    val trendingState: LiveData<MusicState> = _trendingState

    private val _searchState = MutableLiveData<MusicState>()
    val searchState: LiveData<MusicState> = _searchState

    fun loadTrendingSongs(term: String = "bollywood hits") {
        _trendingState.value = MusicState.Loading
        viewModelScope.launch {
            val result = repository.searchSongs(term)
            _trendingState.value = if (result.isSuccess) {
                MusicState.Success(result.getOrNull() ?: emptyList())
            } else {
                MusicState.Error(result.exceptionOrNull()?.message ?: "Failed to load trending songs")
            }
        }
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