package com.example.musicapp.data.tracks

import android.net.Uri
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.apiJson
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.di.createRetrofit
import com.example.musicapp.di.createUploadOkHttpClient
import com.example.musicapp.feature.upload.SelectedFile
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 업로드 전용 클라이언트 + `TrackRepository.uploadTrack`을 MockWebServer로 검증한다. */
class TrackUploadTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: TrackRepository
    private var token: String? = null

    private val audioBytes = ByteArray(50_000) { (it % 251).toByte() }
    private val uriContents = mutableMapOf<Uri, ByteArray>()

    private val trackJson = """
        {"trackId": "t1", "title": "T", "artist": "A", "album": null,
         "durationSeconds": 10, "status": "PENDING", "artworkUrl": null,
         "originalFilename": "a.mp3", "fileExtension": "mp3",
         "fileSizeBytes": 1, "createdAt": "2026-11-11T01:00:00Z"}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = OkHttpClient.Builder().build()
        client = createUploadOkHttpClient(base, server.url("/")) { token }
        val api = createRetrofit(server.url("/").toString(), client, apiJson).create(MusicApi::class.java)
        val opener = UriStreamOpener { uri -> uriContents[uri]?.let { ByteArrayInputStream(it) } }
        repository = TrackRepository(api, api, opener)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newFile(
        name: String = "a.mp3",
        bytes: ByteArray = audioBytes,
        mime: String? = "audio/mpeg",
        size: Long? = bytes.size.toLong(),
    ): SelectedFile {
        val uri = mockk<Uri>()
        uriContents[uri] = bytes
        return SelectedFile(uri, name, size, mime)
    }

    private fun enqueueAccepted() {
        server.enqueue(
            MockResponse().setResponseCode(202).setHeader("Content-Type", "application/json").setBody(trackJson)
        )
    }

    private suspend fun upload(
        file: SelectedFile = newFile(),
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artwork: SelectedFile? = null,
        onProgress: (Int) -> Unit = {},
    ) = repository.uploadTrack(file, title, artist, album, artwork, onProgress)

    @Test
    fun 빈_값_파트는_본문에_없다() = runTest {
        enqueueAccepted()

        upload(title = "  ", artist = "", album = null)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertFalse(body.contains("name=\"title\""))
        assertFalse(body.contains("name=\"artist\""))
        assertFalse(body.contains("name=\"album\""))
        assertFalse(body.contains("name=\"artworkFile\""))
    }

    @Test
    fun 필드명과_trim된_값이_전송된다() = runTest {
        enqueueAccepted()

        upload(title = "  제목 ", artist = "가수", album = "앨범", artwork = newFile("c.png", ByteArray(10), "image/png"))

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        listOf("file", "title", "artist", "album", "artworkFile").forEach {
            assertTrue("$it 파트", body.contains("name=\"$it\""))
        }
        assertTrue(body.contains("\r\n\r\n제목\r\n"))
        assertTrue(body.contains("filename=\"c.png\""))
        // 큰 file 파트가 마지막에 온다
        assertTrue(body.indexOf("name=\"artworkFile\"") < body.indexOf("name=\"file\""))
    }

    @Test
    fun 한글_파일명은_UTF8_원문으로_전송된다() = runTest {
        enqueueAccepted()

        upload(file = newFile(name = "내 음악 01.mp3"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("filename=\"내 음악 01.mp3\""))
    }

    @Test
    fun 파일_내용이_바이트_그대로_전송되고_길이가_일치한다() = runTest {
        enqueueAccepted()

        upload()

        val request = server.takeRequest()
        val bytes = request.body.readByteArray()
        val start = indexOf(bytes, "\r\n\r\n".toByteArray(), indexOf(bytes, "name=\"file\"".toByteArray(), 0))
        assertEquals(audioBytes.toList(), bytes.copyOfRange(start + 4, start + 4 + audioBytes.size).toList())
        assertEquals(request.bodySize, request.getHeader("Content-Length")!!.toLong())
    }

    @Test
    fun 크기를_모르면_chunked로_전송한다() = runTest {
        enqueueAccepted()
        val progress = mutableListOf<Int>()

        upload(file = newFile(size = null), onProgress = { progress.add(it) })

        val request = server.takeRequest()
        assertNull(request.getHeader("Content-Length"))
        assertTrue(progress.isEmpty())
    }

    @Test
    fun 응답_202를_파싱한다() = runTest {
        enqueueAccepted()

        val result = upload()

        assertEquals("t1", result.trackId)
        assertEquals(TrackStatus.PENDING, result.status)
    }

    @Test
    fun 응답_413_HTML_본문은_PAYLOAD_TOO_LARGE다() = runTest {
        server.enqueue(MockResponse().setResponseCode(413).setBody("<html>Request Entity Too Large</html>"))

        val e = runCatching { upload() }.exceptionOrNull()
        assertTrue(e is ApiException)
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, (e as ApiException).code)
        assertFalse(e.message.orEmpty().contains("Entity"))
    }

    @Test
    fun 토큰이_있으면_Authorization을_붙인다() = runTest {
        token = "test-token"
        enqueueAccepted()

        upload()

        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 토큰이_없으면_Authorization을_붙이지_않는다() = runTest {
        enqueueAccepted()

        upload()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun 업로드_클라이언트는_타임아웃이_없다() {
        assertEquals(0, client.readTimeoutMillis)
        assertEquals(0, client.writeTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
    }

    @Test
    fun 서버_5xx는_재시도하지_않는다() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val e = runCatching { upload() }.exceptionOrNull()

        assertEquals(ErrorCode.INTERNAL_ERROR, (e as ApiException).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun 진행률은_단조_증가하며_100으로_끝난다() = runTest {
        enqueueAccepted()
        val progress = CopyOnWriteArrayList<Int>()

        upload(onProgress = { progress.add(it) })

        assertTrue(progress.isNotEmpty())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted(), progress.toList())
        assertEquals(progress.distinct(), progress.toList())
    }

    @Test
    fun 코루틴_취소_시_진행_중인_Call도_취소된다() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { upload() }
        assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        val deadline = System.currentTimeMillis() + 3_000
        while (client.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(0, client.dispatcher.runningCallsCount())
    }

    private fun indexOf(source: ByteArray, target: ByteArray, from: Int): Int {
        for (i in from..source.size - target.size) {
            if (target.indices.all { source[i + it] == target[it] }) return i
        }
        return -1
    }
}
