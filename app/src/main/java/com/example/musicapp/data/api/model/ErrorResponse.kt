package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

/** 서버 오류 본문. `message`는 화면에 노출하지 않고 클라이언트 문구를 쓴다(PRD §3.4). */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: String,
)
