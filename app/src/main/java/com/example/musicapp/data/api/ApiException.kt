package com.example.musicapp.data.api

/** 앱에서 다루는 오류 코드. 서버 코드 8개와 클라이언트 전용 NETWORK_ERROR. */
enum class ErrorCode {
    INVALID_AUDIO_FORMAT,
    INVALID_ARTWORK,
    INVALID_REQUEST,
    UNAUTHORIZED,
    TRACK_NOT_FOUND,
    TRACK_NOT_READY,
    PAYLOAD_TOO_LARGE,
    INTERNAL_ERROR,
    NETWORK_ERROR,
    ;

    companion object {
        /** 서버 `code` 문자열을 변환한다. 모르는 값(NETWORK_ERROR 포함)은 INTERNAL_ERROR. */
        fun fromServer(code: String): ErrorCode =
            entries.firstOrNull { it != NETWORK_ERROR && it.name == code } ?: INTERNAL_ERROR
    }
}

/**
 * 정규화된 API 오류. 서버 `message`는 담지 않는다(PRD §3.4).
 * [status]는 HTTP 상태이며 네트워크 오류면 null이다.
 */
class ApiException(
    val status: Int?,
    val code: ErrorCode,
    cause: Throwable? = null,
) : Exception("API error status=$status code=${code.name}", cause)
