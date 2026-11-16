package com.example.musicapp.ui

import com.example.musicapp.data.api.ErrorCode

/** 재생 오류(PlaybackException) 문구. PRD §3.4 */
const val PLAYBACK_ERROR_MESSAGE = "재생 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."

private const val LOGIN_FAILED_MESSAGE = "로그인에 실패했습니다. 다시 시도해 주세요."

/** 오류 코드 → 사용자 문구(PRD §3.4). 코드가 추가되면 컴파일 오류로 누락을 막는다. */
fun getErrorMessage(code: ErrorCode): String = when (code) {
    ErrorCode.INVALID_AUDIO_FORMAT ->
        "지원하지 않거나 손상된 음원 파일입니다. MP3, WAV, FLAC, M4A 파일을 선택해 주세요."
    ErrorCode.INVALID_ARTWORK -> "앨범자켓은 10MB 이하의 JPEG 또는 PNG 이미지만 사용할 수 있습니다."
    ErrorCode.INVALID_REQUEST -> "요청 내용을 확인해 주세요. (입력 길이 초과 등)"
    ErrorCode.UNAUTHORIZED -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
    ErrorCode.TRACK_NOT_FOUND -> "존재하지 않는 곡입니다. 목록을 새로고침합니다."
    ErrorCode.TRACK_NOT_READY -> "아직 변환 중인 곡입니다. 잠시 후 다시 시도해 주세요."
    ErrorCode.PAYLOAD_TOO_LARGE -> "파일이 너무 큽니다. 음원은 100MB, 앨범자켓은 10MB 이하여야 합니다."
    ErrorCode.INTERNAL_ERROR -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    ErrorCode.NETWORK_ERROR -> "서버에 연결할 수 없습니다. 네트워크를 확인해 주세요."
}

/** 로그인 콜백 `?error=` 값 → 문구(PRD §4.4). 모르는 값·null은 login_failed 문구. */
fun getLoginErrorMessage(error: String?): String = when (error) {
    "access_denied" -> "로그인이 취소되었습니다."
    "invalid_request" -> "로그인 요청이 올바르지 않습니다. 다시 시도해 주세요."
    "authorization_request_not_found" -> "로그인 시간이 초과되었습니다(3분). 다시 시도해 주세요."
    "invalid_state_parameter" -> "로그인 검증에 실패했습니다. 다시 시도해 주세요."
    "invalid_user_info" -> "구글 계정 정보를 확인할 수 없습니다. 이메일이 인증된 계정으로 로그인해 주세요."
    "server_error", "temporarily_unavailable" -> "로그인 서버에 문제가 있습니다. 잠시 후 다시 시도해 주세요."
    "invalid_redirect_uri" -> "로그인 설정 오류입니다. (개발 환경이면 §12의 허용 콜백 등록을 확인하세요)"
    else -> LOGIN_FAILED_MESSAGE
}
