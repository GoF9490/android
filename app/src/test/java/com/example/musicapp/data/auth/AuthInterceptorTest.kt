package com.example.musicapp.data.auth

import com.example.musicapp.data.api.RetryInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private var token: String? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createClient(vararg extra: okhttp3.Interceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(server.url("/")) { token })
        extra.forEach { builder.addInterceptor(it) }
        return builder.build()
    }

    private fun call(client: OkHttpClient, url: okhttp3.HttpUrl = server.url("/api/x"), auth: String? = null) {
        val request = Request.Builder().url(url).apply { auth?.let { header("Authorization", it) } }.build()
        client.newCall(request).execute().close()
    }

    @Test
    fun 같은_호스트에는_Bearer를_붙인다() {
        token = "tkn-1"
        server.enqueue(MockResponse())
        call(createClient())
        assertEquals("Bearer tkn-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 토큰이_null이거나_비면_붙이지_않는다() {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse())
        val client = createClient()
        token = null
        call(client)
        token = ""
        call(client)
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 다른_호스트에는_붙이지_않는다() {
        token = "tkn-1"
        server.enqueue(MockResponse())
        // 기준 호스트는 localhost, 요청은 같은 서버를 127.0.0.1(다른 호스트명)로 호출
        val base = server.url("/").newBuilder().host("localhost").build()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(base) { token }).build()
        call(client, url = server.url("/api/x").newBuilder().host("127.0.0.1").build())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 같은_호스트라도_포트가_다르면_붙이지_않는다() {
        token = "tkn-1"
        val other = MockWebServer().apply { start() }
        try {
            other.enqueue(MockResponse())
            call(createClient(), url = other.url("/api/x"))
            assertNull(other.takeRequest().getHeader("Authorization"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun 호스트_대소문자는_구분하지_않는다() {
        token = "tkn-1"
        server.enqueue(MockResponse())
        val upper = server.url("/api/x").newBuilder().host(server.url("/").host.uppercase()).build()
        call(createClient(), url = upper)
        assertEquals("Bearer tkn-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 이미_Authorization이_있으면_덮어쓰지_않는다() {
        token = "tkn-1"
        server.enqueue(MockResponse())
        call(createClient(), auth = "Bearer custom")
        assertEquals("Bearer custom", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 토큰_변경이_다음_요청에_반영된다() {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse())
        server.enqueue(MockResponse())
        val client = createClient()
        token = "a"
        call(client)
        token = "b"
        call(client)
        token = null
        call(client)
        assertEquals("Bearer a", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer b", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 재시도된_요청에도_Bearer가_붙어_있다() {
        token = "tkn-1"
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))
        call(createClient(RetryInterceptor(retryDelayMillis = 0)))
        assertEquals("Bearer tkn-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer tkn-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 외부_호스트로_리다이렉트되면_헤더가_제거된다() {
        token = "tkn-1"
        val external = MockWebServer().apply { start() }
        try {
            external.enqueue(MockResponse())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external.url("/img").toString()))
            call(createClient())
            assertEquals("Bearer tkn-1", server.takeRequest().getHeader("Authorization"))
            assertNull(external.takeRequest().getHeader("Authorization"))
        } finally {
            external.shutdown()
        }
    }
}
