package com.example.musicapp.data.auth

import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.normalizeError
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.MessageKind
import com.example.musicapp.ui.UiMessage
import com.example.musicapp.ui.getErrorMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 인증 상태의 단일 소유자(PRD §4.3). 토큰 저장·`/me` 조회·상태 전이를 한 Mutex로 직렬화한다.
 * 목록·재생 클래스에는 의존하지 않으므로 로그아웃이 그 상태를 건드리지 않는다.
 * 토큰은 로그·예외에 남기지 않는다.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MusicApi,
    private val tokenStore: TokenStore,
    private val messageBus: MessageBus,
) {

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Loading)

    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    /** 앱 기동 시 1회. 저장된 토큰으로 사용자를 조회한다. */
    suspend fun initialize() {
        mutex.withLock { loadUser(tokenStore.awaitToken()) }
    }

    /** 토큰을 저장하고 `/me`로 사용자를 조회한다. */
    suspend fun login(token: String) {
        mutex.withLock {
            mutableState.value = AuthState.Loading
            try {
                tokenStore.saveToken(token)
                loadUser(token)
            } finally {
                // 취소로 Loading에 갇히지 않게 한다(토큰은 유지되어 이후 재조회로 복구)
                if (mutableState.value == AuthState.Loading) mutableState.value = AuthState.LoggedOut
            }
        }
    }

    /** 서버 호출 없이 토큰만 삭제한다. */
    suspend fun logout() {
        mutex.withLock {
            tokenStore.clearToken()
            mutableState.value = AuthState.LoggedOut
        }
    }

    /**
     * 포그라운드 복귀용 재조회. 이미 진행 중인 작업이 있거나 [AuthState.Loading]이면 건너뛴다.
     * [AuthState.LoggedIn]이면 토큰 만료만 점검하고 네트워크는 호출하지 않는다(재조회 실패로 강등되지 않음).
     */
    suspend fun refreshUser() {
        if (mutableState.value == AuthState.Loading) return
        if (!mutex.tryLock()) return
        try {
            val token = tokenStore.awaitToken()
            if (mutableState.value is AuthState.LoggedIn && token != null && !JwtParser.isExpired(token)) return
            loadUser(token)
        } finally {
            mutex.unlock()
        }
    }

    /** Mutex를 쥔 상태에서만 호출한다. */
    private suspend fun loadUser(token: String?) {
        if (token == null) {
            mutableState.value = AuthState.LoggedOut
            return
        }
        if (JwtParser.isExpired(token)) {
            // 만료 토큰은 /me 없이 조용히 로그아웃(스낵바 없음)
            tokenStore.clearToken()
            mutableState.value = AuthState.LoggedOut
            return
        }
        try {
            mutableState.value = AuthState.LoggedIn(api.getMe())
        } catch (t: Throwable) {
            val error = normalizeError(t)
            if (error.status == 401) {
                tokenStore.clearToken()
                messageBus.post(
                    UiMessage(
                        id = ErrorCode.UNAUTHORIZED.name,
                        text = getErrorMessage(ErrorCode.UNAUTHORIZED),
                        kind = MessageKind.ERROR,
                    ),
                )
            }
            // 5xx·네트워크·기타 4xx는 토큰을 유지한 채 비로그인으로 표시(포그라운드 복귀 시 재시도)
            mutableState.value = AuthState.LoggedOut
        }
    }
}
