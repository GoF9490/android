package com.example.musicapp.ui

import com.example.musicapp.data.api.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun 모든_오류_코드에_문구가_있다() {
        ErrorCode.entries.forEach { assertTrue(it.name, getErrorMessage(it).isNotBlank()) }
    }

    @Test
    fun 오류_코드_문구가_PRD_표와_같다() {
        val expected = mapOf(
            ErrorCode.INVALID_AUDIO_FORMAT to "지원하지 않거나 손상된 음원 파일입니다. MP3, WAV, FLAC, M4A 파일을 선택해 주세요.",
            ErrorCode.INVALID_ARTWORK to "앨범자켓은 10MB 이하의 JPEG 또는 PNG 이미지만 사용할 수 있습니다.",
            ErrorCode.INVALID_REQUEST to "요청 내용을 확인해 주세요. (입력 길이 초과 등)",
            ErrorCode.UNAUTHORIZED to "로그인이 만료되었습니다. 다시 로그인해 주세요.",
            ErrorCode.TRACK_NOT_FOUND to "존재하지 않는 곡입니다. 목록을 새로고침합니다.",
            ErrorCode.TRACK_NOT_READY to "아직 변환 중인 곡입니다. 잠시 후 다시 시도해 주세요.",
            ErrorCode.PAYLOAD_TOO_LARGE to "파일이 너무 큽니다. 음원은 100MB, 앨범자켓은 10MB 이하여야 합니다.",
            ErrorCode.INTERNAL_ERROR to "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            ErrorCode.NETWORK_ERROR to "서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.",
        )
        assertEquals(ErrorCode.entries.size, expected.size)
        expected.forEach { (code, message) -> assertEquals(code.name, message, getErrorMessage(code)) }
    }

    @Test
    fun 재생_오류_문구가_PRD_표와_같다() {
        assertEquals("재생 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", PLAYBACK_ERROR_MESSAGE)
    }

    @Test
    fun 로그인_오류_문구가_PRD_표와_같다() {
        val serverError = "로그인 서버에 문제가 있습니다. 잠시 후 다시 시도해 주세요."
        val expected = mapOf(
            "access_denied" to "로그인이 취소되었습니다.",
            "invalid_request" to "로그인 요청이 올바르지 않습니다. 다시 시도해 주세요.",
            "authorization_request_not_found" to "로그인 시간이 초과되었습니다(3분). 다시 시도해 주세요.",
            "invalid_state_parameter" to "로그인 검증에 실패했습니다. 다시 시도해 주세요.",
            "invalid_user_info" to "구글 계정 정보를 확인할 수 없습니다. 이메일이 인증된 계정으로 로그인해 주세요.",
            "server_error" to serverError,
            "temporarily_unavailable" to serverError,
            "invalid_redirect_uri" to "로그인 설정 오류입니다. (개발 환경이면 §12의 허용 콜백 등록을 확인하세요)",
            "login_failed" to "로그인에 실패했습니다. 다시 시도해 주세요.",
        )
        expected.forEach { (error, message) -> assertEquals(error, message, getLoginErrorMessage(error)) }
    }

    @Test
    fun 모르는_로그인_오류는_login_failed_문구다() {
        val fallback = "로그인에 실패했습니다. 다시 시도해 주세요."
        assertEquals(fallback, getLoginErrorMessage("unknown_code"))
        assertEquals(fallback, getLoginErrorMessage(null))
        assertEquals(fallback, getLoginErrorMessage(""))
    }
}
