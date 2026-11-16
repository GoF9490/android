package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamPlayResponse(
    val trackId: String,
    val streamUrl: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val artworkUrl: String? = null,
)
