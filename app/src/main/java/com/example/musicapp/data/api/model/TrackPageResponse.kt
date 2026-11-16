package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackPageResponse(
    val content: List<TrackResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
