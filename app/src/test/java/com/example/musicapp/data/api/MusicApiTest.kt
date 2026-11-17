package com.example.musicapp.data.api

import com.example.musicapp.data.api.model.TrackStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MusicApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MusicApi

    private val trackJson = """
        {"trackId": "t1", "title": "T", "artist": "A", "album": null,
         "durationSeconds": 10, "status": "PENDING", "artworkUrl": null,
         "originalFilename": "a.mp3", "fileExtension": "mp3",
         "fileSizeBytes": 1, "createdAt": "2026-11-11T01:00:00Z"}
    """.trimIndent()

    private val emptyPage =
        """{"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "hasNext": false}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MusicApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    @Test
    fun getTracks_keyword가_null이면_쿼리를_보내지_않는다() = runTest {
        enqueueJson(emptyPage)

        api.getTracks(keyword = null, page = 0, size = 20)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/tracks", url.encodedPath)
        assertNull(url.queryParameter("keyword"))
        assertEquals("0", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
    }

    @Test
    fun getTracks_한글_keyword를_인코딩해_전달한다() = runTest {
        enqueueJson(emptyPage)

        api.getTracks(keyword = "봄", page = 1, size = 20)

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("keyword=%EB%B4%84"))
        assertEquals("봄", request.requestUrl!!.queryParameter("keyword"))
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
    }

    @Test
    fun 단건_조회_경로와_응답_파싱을_확인한다() = runTest {
        enqueueJson(trackJson)
        enqueueJson(
            """{"trackId": "t1", "streamUrl": "/stream/t1/index.m3u8", "title": "T",
                "artist": "A", "durationSeconds": 10}"""
        )
        enqueueJson("""{"userId": 1, "email": "a@b.c", "role": "ROLE_USER"}""")

        val track = api.getTrack("t1")
        val play = api.getPlayInfo("t1")
        val me = api.getMe()

        assertEquals("/api/tracks/t1", server.takeRequest().path)
        assertEquals("/api/tracks/t1/play", server.takeRequest().path)
        assertEquals("/api/auth/me", server.takeRequest().path)
        assertEquals(TrackStatus.PENDING, track.status)
        assertEquals("/stream/t1/index.m3u8", play.streamUrl)
        assertEquals(1L, me.userId)
    }

    @Test
    fun uploadTrack_file만_넘기면_다른_파트는_본문에_없다() = runTest {
        enqueueJson(trackJson, code = 202)
        val file = MultipartBody.Part.createFormData(
            "file", "a.mp3", "x".toByteArray().toRequestBody("audio/mpeg".toMediaType())
        )

        val result = api.uploadTrack(null, null, null, null, file)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("/api/tracks/upload", request.path)
        assertTrue(body.contains("name=\"file\""))
        assertFalse(body.contains("name=\"title\""))
        assertFalse(body.contains("name=\"artist\""))
        assertFalse(body.contains("name=\"album\""))
        assertFalse(body.contains("name=\"artworkFile\""))
        assertEquals("t1", result.trackId)
    }

    @Test
    fun 비_2xx_응답은_HttpException이다() {
        enqueueJson("""{"code": "TRACK_NOT_FOUND", "message": "m", "timestamp": "t"}""", code = 404)

        val e = assertThrows(HttpException::class.java) {
            runBlocking { api.getTrack("nope") }
        }

        assertEquals(404, e.code())
    }
}
