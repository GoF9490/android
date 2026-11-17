package com.example.musicapp.data.tracks

import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.model.TrackPageResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TrackRepositoryTest {

    private val api = mockk<MusicApi>()
    private val repository = TrackRepository(api, api, mockk<UriStreamOpener>())
    private val emptyPage = TrackPageResponse(emptyList(), 0, 20, 0, 0, false)

    @Test
    fun getTracks는_size_20과_page_keyword를_전달한다() = runTest {
        coEvery { api.getTracks(any(), any(), any()) } returns emptyPage

        repository.getTracks("abc", 2)
        repository.getTracks(null, 0)

        coVerify(exactly = 1) { api.getTracks("abc", 2, 20) }
        coVerify(exactly = 1) { api.getTracks(null, 0, 20) }
    }

    @Test
    fun 네트워크_오류는_ApiException으로_정규화된다() = runTest {
        coEvery { api.getTracks(any(), any(), any()) } throws IOException("끊김")

        val e = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getTracks(null, 0) }
        }
        assertEquals(ErrorCode.NETWORK_ERROR, e.code)
    }

    @Test
    fun HTTP_오류는_상태코드를_담은_ApiException이_된다() = runTest {
        val http = HttpException(Response.error<Any>(500, "".toResponseBody()))
        coEvery { api.getTrack(any()) } throws http

        val e = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getTrack("t1") }
        }
        assertEquals(500, e.status)
        assertEquals(ErrorCode.INTERNAL_ERROR, e.code)
    }

    @Test
    fun getPlayInfo는_트랙_id로_1회_호출하고_오류를_정규화한다() = runTest {
        val play = com.example.musicapp.data.api.model.StreamPlayResponse("t1", "/s/t1.m3u8", "제목", "가수", 10)
        coEvery { api.getPlayInfo("t1") } returns play
        assertEquals(play, repository.getPlayInfo("t1"))
        coVerify(exactly = 1) { api.getPlayInfo("t1") }

        coEvery { api.getPlayInfo("t2") } throws IOException("끊김")
        val e = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getPlayInfo("t2") }
        }
        assertEquals(ErrorCode.NETWORK_ERROR, e.code)
    }

    @Test
    fun 취소_예외는_변환하지_않는다()= runTest {
        coEvery { api.getTrack(any()) } throws CancellationException("취소")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getTrack("t1") }
        }
    }
}
