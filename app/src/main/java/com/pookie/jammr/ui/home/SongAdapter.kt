package com.pookie.jammr.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.pookie.jammr.R
import com.pookie.jammr.data.model.Song

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivArtwork: ImageView = itemView.findViewById(R.id.ivArtwork)
        val tvTrackName: TextView = itemView.findViewById(R.id.tvTrackName)
        val tvArtistName: TextView = itemView.findViewById(R.id.tvArtistName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_card, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.tvTrackName.text = song.trackName
        holder.tvArtistName.text = song.artistName

        val highResArt = song.artworkUrl?.replace("100x100", "400x400")

        Glide.with(holder.itemView.context)
            .load(highResArt)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .centerCrop()
            .into(holder.ivArtwork)

        holder.itemView.setOnClickListener { onSongClick(song) }
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}