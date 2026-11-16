package com.example.musicapp.data.api

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createClient(maxRetries: Int = 2): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = maxRetries, retryDelayMillis = 0))
            .build()

    private fun get(client: OkHttpClient = createClient()) =
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

    private fun enqueueCodes(vararg codes: Int) {
        codes.forEach { server.enqueue(MockResponse().setResponseCode(it)) }
    }

    @Test
    fun get_5xx가_계속되면_총_3회_요청하고_마지막_응답을_반환한다() {
        enqueueCodes(500, 500, 500)
        get().use { assertEquals(500, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun get_중간에_성공하면_재시도를_멈춘다() {
        enqueueCodes(500, 200)
        get().use { assertEquals(200, it.code) }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun get_503_503_200이면_3회째에_성공한다() {
        enqueueCodes(503, 503, 200)
        get().use { assertEquals(200, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun get_4xx는_재시도하지_않는다() {
        for (code in listOf(401, 404, 409)) {
            val before = server.requestCount
            enqueueCodes(code)
            get().use { assertEquals(code, it.code) }
            assertEquals(1, server.requestCount - before)
        }
    }

    @Test
    fun post_5xx는_재시도하지_않는다() {
        enqueueCodes(500)
        val request = Request.Builder().url(server.url("/x"))
            .post("a".toRequestBody()).build()
        createClient().newCall(request).execute().use { assertEquals(500, it.code) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun get_연결이_끊겨도_2회_재시도해_성공한다() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        enqueueCodes(200)
        get().use { assertEquals(200, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun get_계속_끊기면_IOException을_전파한다() {
        repeat(3) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        }
        assertThrows(IOException::class.java) { get().close() }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun maxRetries가_0이면_재시도하지_않는다() {
        enqueueCodes(500)
        get(createClient(maxRetries = 0)).use { assertEquals(500, it.code) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun 취소된_호출은_재시도하지_않는다() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        enqueueCodes(200)
        val call = createClient().newCall(Request.Builder().url(server.url("/x")).build())
        call.cancel()
        assertThrows(IOException::class.java) { call.execute().close() }
        assertEquals(0, server.requestCount)
    }
}
