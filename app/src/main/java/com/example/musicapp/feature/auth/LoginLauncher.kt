package com.example.musicapp.feature.auth

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** 백엔드 화이트리스트와 문자열 완전 일치해야 하는, 이미 인코딩된 콜백 주소. */
const val LOGIN_REDIRECT_URI = "musicapp%3A%2F%2Foauth2%2Fcallback"

/** `{baseUrl}/oauth2/authorization/google?redirect_uri=...`. 이중 인코딩을 피하려고 문자열로 이어 붙인다. */
fun buildLoginUrl(baseUrl: String): String =
    "${baseUrl.trimEnd('/')}/oauth2/authorization/google?redirect_uri=$LOGIN_REDIRECT_URI"

/**
 * Custom Tab으로 로그인 페이지를 연다. 같은 태스크에서 열려야 콜백이 singleTop MainActivity로 돌아오므로
 * NEW_TASK 플래그를 붙이지 않는다. 브라우저가 없으면 [onNoBrowser]를 호출한다.
 */
fun launchLogin(context: Context, baseUrl: String, onNoBrowser: () -> Unit) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, buildLoginUrl(baseUrl).toUri())
    } catch (e: ActivityNotFoundException) {
        onNoBrowser()
    }
}
