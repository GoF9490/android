package com.example.musicapp.util

/**
 * 절대 URL(http://, https://)은 그대로 쓰고, 상대경로면 baseUrl과 결합한다.
 * 슬래시 중복·누락은 하나로 정리한다.
 */
fun resolveMediaUrl(baseUrl: String, url: String): String {
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return url
    }
    return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}
