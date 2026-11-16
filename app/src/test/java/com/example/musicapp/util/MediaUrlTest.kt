package com.example.musicapp.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrlTest {

    @Test
    fun resolveMediaUrl_상대경로는_baseUrl과_결합한다() {
        val cases = listOf(
            Triple("http://localhost", "/hls/a/index.m3u8", "http://localhost/hls/a/index.m3u8"),
            Triple("http://localhost/", "/hls/a/index.m3u8", "http://localhost/hls/a/index.m3u8"),
            Triple("http://localhost", "hls/a/index.m3u8", "http://localhost/hls/a/index.m3u8"),
            Triple("http://localhost/", "hls/a/index.m3u8", "http://localhost/hls/a/index.m3u8"),
            Triple("http://localhost//", "//x.jpg", "http://localhost/x.jpg"),
            Triple("http://localhost:8080", "/api/tracks/1/download", "http://localhost:8080/api/tracks/1/download"),
        )
        cases.forEach { (base, url, expected) ->
            assertEquals("base=$base url=$url", expected, resolveMediaUrl(base, url))
        }
    }

    @Test
    fun resolveMediaUrl_절대_URL은_그대로_반환한다() {
        val cases = listOf(
            "http://localhost" to "http://cdn.example.com/a.jpg",
            "http://localhost" to "https://cdn.example.com/a.jpg",
            "http://localhost" to "HTTPS://cdn.example.com/a.jpg",
            "http://localhost/" to "https://cdn.example.com/a.jpg",
        )
        cases.forEach { (base, url) ->
            assertEquals("base=$base url=$url", url, resolveMediaUrl(base, url))
        }
    }
}
