package com.example.musicapp.feature.auth

/** 로그인 콜백 딥링크(`musicapp://oauth2/callback`)의 해석 결과. */
sealed interface DeepLinkResult {
    /** 토큰은 로그·화면에 노출되지 않도록 toString에서 가린다. */
    data class Token(val token: String) : DeepLinkResult {
        override fun toString(): String = "Token(***)"
    }

    /** [code]가 null이면 login_failed 문구로 처리한다. */
    data class Error(val code: String?) : DeepLinkResult

    /** 로그인 콜백이 아니거나 token·error가 모두 없음. */
    data object None : DeepLinkResult
}

/**
 * 딥링크 구성 요소로 결과를 구분하는 순수 함수(Uri 비의존, JVM 테스트 가능).
 * token이 있으면 error보다 우선한다. 토큰·URI는 로그에 남기지 않는다.
 */
fun parseDeepLink(
    scheme: String?,
    host: String?,
    path: String?,
    token: String?,
    error: String?,
): DeepLinkResult {
    if (scheme != "musicapp" || host != "oauth2" || path != "/callback") return DeepLinkResult.None
    if (!token.isNullOrEmpty()) return DeepLinkResult.Token(token)
    if (error != null) return DeepLinkResult.Error(error.ifEmpty { null })
    return DeepLinkResult.None
}
