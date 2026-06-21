package com.pookie.jammr.data.repository

import com.pookie.jammr.data.model.Song
import com.pookie.jammr.data.remote.RetrofitInstance

class MusicRepository {

    suspend fun searchSongs(query: String): Result<List<Song>> {
        return try {
            if (query.isBlank()) {
                return Result.success(emptyList())
            }
            val response = RetrofitInstance.api.searchSongs(term = query)
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}