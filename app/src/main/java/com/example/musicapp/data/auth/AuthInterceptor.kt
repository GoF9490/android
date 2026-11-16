package com.example.musicapp.data.auth

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 토큰이 있고 요청 호스트·포트가 BASE_URL과 같을 때만 `Authorization: Bearer`를 붙인다.
 * 외부 호스트(구글 프로필 이미지 등)에는 붙이지 않는다. 스킴은 비교하지 않는다.
 * 토큰은 보관·로깅하지 않고 요청마다 tokenProvider로 읽는다.
 */
class AuthInterceptor(
    baseUrl: HttpUrl,
    private val tokenProvider: () -> String?,
) : Interceptor {

    private val baseHost = baseUrl.host
    private val basePort = baseUrl.port

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)
        if (request.url.host != baseHost || request.url.port != basePort) return chain.proceed(request)
        val token = tokenProvider()
        if (token.isNullOrEmpty()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
