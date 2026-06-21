package com.pookie.jammr.data.model

import com.google.gson.annotations.SerializedName

data class Song(
    @SerializedName("trackId")
    val trackId: Long = 0L,

    @SerializedName("trackName")
    val trackName: String = "",

    @SerializedName("artistName")
    val artistName: String = "",

    @SerializedName("collectionName")
    val albumName: String? = null,

    @SerializedName("artworkUrl100")
    val artworkUrl: String? = null,

    @SerializedName("previewUrl")
    val previewUrl: String? = null,

    @SerializedName("trackTimeMillis")
    val durationMillis: Long? = null,

    @SerializedName("trackViewUrl")
    val externalUrl: String? = null
)

data class ITunesSearchResponse(
    @SerializedName("resultCount")
    val resultCount: Int = 0,

    @SerializedName("results")
    val results: List<Song> = emptyList()
)