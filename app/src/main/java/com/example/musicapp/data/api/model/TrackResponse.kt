package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackResponse(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Int,
    // 기본값이 있어야 coerceInputValues가 알 수 없는 enum 값을 UNKNOWN으로 바꾼다
    val status: TrackStatus = TrackStatus.UNKNOWN,
    val artworkUrl: String? = null,
    val originalFilename: String,
    // mp3 | wav | flac | m4a
    val fileExtension: String,
    val fileSizeBytes: Long,
    // UTC ISO-8601
    val createdAt: String,
)
