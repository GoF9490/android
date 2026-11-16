package com.example.musicapp.data.api.model

import kotlinx.serialization.Serializable

/**
 * 트랙 변환 상태.
 * UNKNOWN은 서버 값이 아니라 클라이언트 전용 값이다. 서버가 모르는 값을 보내거나 누락하면 UNKNOWN이 된다.
 * UNKNOWN의 UI 처리는 후속 Task에서 일반 오류로 취급한다(`when` 분기 시 UNKNOWN 또는 else 필수).
 */
@Serializable
enum class TrackStatus { PENDING, PROCESSING, READY, FAILED, UNKNOWN }
