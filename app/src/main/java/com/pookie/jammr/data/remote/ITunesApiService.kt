package com.pookie.jammr.data.remote

import com.pookie.jammr.data.model.ITunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApiService {

    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25
    ): ITunesSearchResponse
}