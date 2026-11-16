package com.example.musicapp.data.auth

import com.example.musicapp.data.api.model.UserResponse

/** 앱의 인증 상태. [Loading]은 기동 직후 또는 로그인 직후 `/me` 조회 중일 때다. */
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: UserResponse) : AuthState
}
