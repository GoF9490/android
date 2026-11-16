package com.example.musicapp.data.api

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorNormalizerTest {

    private fun createHttpException(status: Int, body: String, type: String = "application/json"): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody(type.toMediaType())))

    private fun createJson(code: String, message: String = "서버 메시지"): String =
        """{"code": "$code", "message": "$message", "timestamp": "2026-11-11T10:00:00+09:00"}"""

    @Test
    fun JSON_본문의_서버_코드_8개를_그대로_사용한다() {
        val cases = mapOf(
            "INVALID_AUDIO_FORMAT" to 400,
            "INVALID_ARTWORK" to 400,
            "INVALID_REQUEST" to 400,
            "UNAUTHORIZED" to 401,
            "TRACK_NOT_FOUND" to 404,
            "TRACK_NOT_READY" to 409,
            "PAYLOAD_TOO_LARGE" to 413,
            "INTERNAL_ERROR" to 500,
        )
        cases.forEach { (code, status) ->
            val result = normalizeError(createHttpException(status, createJson(code)))
            assertEquals(ErrorCode.valueOf(code), result.code)
            assertEquals(status, result.status)
        }
    }

    @Test
    fun 모르는_code는_INTERNAL_ERROR다() {
        val result = normalizeError(createHttpException(400, createJson("WHATEVER")))
        assertEquals(ErrorCode.INTERNAL_ERROR, result.code)
        assertEquals(400, result.status)
    }

    @Test
    fun 서버가_NETWORK_ERROR를_보내도_INTERNAL_ERROR다() {
        val result = normalizeError(createHttpException(500, createJson("NETWORK_ERROR")))
        assertEquals(ErrorCode.INTERNAL_ERROR, result.code)
    }

    @Test
    fun HTML_413은_PAYLOAD_TOO_LARGE다() {
        val result = normalizeError(createHttpException(413, "<html>413</html>", "text/html"))
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, result.code)
    }

    @Test
    fun HTML_502는_INTERNAL_ERROR다() {
        val result = normalizeError(createHttpException(502, "<html>502</html>", "text/html"))
        assertEquals(ErrorCode.INTERNAL_ERROR, result.code)
        assertEquals(502, result.status)
    }

    @Test
    fun HTML_그_외_4xx는_INVALID_REQUEST다() {
        listOf(400, 404, 405).forEach { status ->
            val result = normalizeError(createHttpException(status, "<html></html>", "text/html"))
            assertEquals(ErrorCode.INVALID_REQUEST, result.code)
        }
    }

    @Test
    fun 빈_본문은_상태로_추정한다() {
        assertEquals(ErrorCode.INVALID_REQUEST, normalizeError(createHttpException(404, "")).code)
        assertEquals(ErrorCode.INTERNAL_ERROR, normalizeError(createHttpException(503, "")).code)
    }

    @Test
    fun 필드가_누락된_JSON은_상태로_추정한다() {
        val result = normalizeError(createHttpException(409, """{"code": "TRACK_NOT_READY"}"""))
        assertEquals(ErrorCode.INVALID_REQUEST, result.code)
    }

    @Test
    fun IOException_계열은_NETWORK_ERROR이고_상태가_없다() {
        listOf(IOException("x"), SocketTimeoutException("x"), UnknownHostException("x")).forEach {
            val result = normalizeError(it)
            assertEquals(ErrorCode.NETWORK_ERROR, result.code)
            assertNull(result.status)
        }
    }

    @Test
    fun CancellationException은_그대로_전파한다() {
        val cancellation = CancellationException("취소")
        val thrown = assertThrows(CancellationException::class.java) { normalizeError(cancellation) }
        assertSame(cancellation, thrown)
    }

    @Test
    fun ApiException은_같은_인스턴스를_반환한다() {
        val original = ApiException(404, ErrorCode.TRACK_NOT_FOUND)
        assertSame(original, normalizeError(original))
    }

    @Test
    fun 그_외_예외는_INTERNAL_ERROR다() {
        val result = normalizeError(IllegalStateException("x"))
        assertEquals(ErrorCode.INTERNAL_ERROR, result.code)
        assertNull(result.status)
    }

    @Test
    fun ApiException에_서버_message가_담기지_않는다() {
        val result = normalizeError(createHttpException(404, createJson("TRACK_NOT_FOUND", "비밀 서버 메시지")))
        assertFalse(result.message.orEmpty().contains("비밀 서버 메시지"))
    }
}
