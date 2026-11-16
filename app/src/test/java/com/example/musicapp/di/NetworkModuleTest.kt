package com.example.musicapp.di

import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.RetryInterceptor
import com.example.musicapp.data.api.apiJson
import com.example.musicapp.data.api.normalizeError
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class NetworkModuleTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MusicApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = createBaseOkHttpClient().newBuilder()
            .addInterceptor(RetryInterceptor(retryDelayMillis = 0))
            .build()
        // 끝 슬래시 없는 baseUrl로 만들어도 정규화되어야 한다
        val baseUrl = server.url("/").toString().trimEnd('/')
        api = createRetrofit(baseUrl, client, apiJson).create(MusicApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val userJson = """{"userId": 1, "email": "a@b.c", "profileImageUrl": null, "role": "USER"}"""
    private val emptyPage =
        """{"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "hasNext": false}"""

    @Test
    fun normalizeBaseUrl_끝_슬래시를_보장한다() {
        assertEquals("http://localhost/", normalizeBaseUrl("http://localhost"))
        assertEquals("http://localhost/", normalizeBaseUrl("http://localhost/"))
        assertEquals("http://localhost/", normalizeBaseUrl("  http://localhost  "))
        assertEquals("http://host/prefix/", normalizeBaseUrl("http://host/prefix"))
    }

    @Test
    fun normalizeBaseUrl_빈_값은_예외() {
        assertThrows(IllegalArgumentException::class.java) { normalizeBaseUrl("  ") }
    }

    @Test
    fun getMe_경로와_파싱이_맞다() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(userJson))
        assertEquals(1L, api.getMe().userId)
        assertEquals("/api/auth/me", server.takeRequest().path)
    }

    @Test
    fun getTracks_5xx_2회_후_성공하면_총_3회_요청한다() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(emptyPage))
        assertEquals(0, api.getTracks(null, 0, 20).totalElements)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun getTracks_5xx가_계속되면_INTERNAL_ERROR로_정규화된다() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("<html>x</html>")) }
        val e = runCatching { api.getTracks(null, 0, 20) }.exceptionOrNull()!!
        assertEquals(ErrorCode.INTERNAL_ERROR, normalizeError(e).code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun uploadTrack_5xx는_재시도하지_않는다() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val file = MultipartBody.Part.createFormData("file", "a.mp3")
        val e = runCatching { api.uploadTrack(file, null, null, null, null) }.exceptionOrNull()!!
        assertEquals(ErrorCode.INTERNAL_ERROR, normalizeError(e).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun 기본_클라이언트_타임아웃은_30초다() {
        val client = createBaseOkHttpClient()
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
    }
}
