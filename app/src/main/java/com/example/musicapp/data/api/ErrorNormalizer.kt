package com.example.musicapp.data.api

import com.example.musicapp.data.api.model.ErrorResponse
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

/**
 * 예외를 [ApiException]으로 변환한다.
 * [CancellationException]은 변환하지 않고 그대로 던진다.
 */
fun normalizeError(throwable: Throwable): ApiException {
    return when (throwable) {
        is ApiException -> throwable
        is CancellationException -> throw throwable
        is HttpException -> normalizeHttpError(throwable)
        is IOException -> ApiException(null, ErrorCode.NETWORK_ERROR, throwable)
        else -> ApiException(null, ErrorCode.INTERNAL_ERROR, throwable)
    }
}

private fun normalizeHttpError(e: HttpException): ApiException {
    val status = e.code()
    val parsed = parseErrorBody(e)
    val code = if (parsed != null) ErrorCode.fromServer(parsed.code) else guessCodeFromStatus(status)
    return ApiException(status, code, e)
}

/** 본문이 ErrorResponse JSON이면 파싱하고, 아니면(HTML·빈 본문·필드 누락) null. */
private fun parseErrorBody(e: HttpException): ErrorResponse? {
    return try {
        val body = e.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) null else apiJson.decodeFromString<ErrorResponse>(body)
    } catch (ignored: Exception) {
        null
    }
}

private fun guessCodeFromStatus(status: Int): ErrorCode = when {
    status == 413 -> ErrorCode.PAYLOAD_TOO_LARGE
    status in 400..499 -> ErrorCode.INVALID_REQUEST
    else -> ErrorCode.INTERNAL_ERROR
}
