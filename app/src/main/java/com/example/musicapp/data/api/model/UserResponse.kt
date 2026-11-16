package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val userId: Long,
    val email: String,
    val profileImageUrl: String? = null,
    val role: String,
)
