package com.example.musicapp.data.api

import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * GET 요청만 5xx 응답·[IOException]에 최대 [maxRetries]회 재시도한다.
 * 4xx와 POST 등 비멱등 요청은 재시도하지 않는다(PRD §3.4).
 * 마지막 시도의 5xx 응답은 그대로 반환하고, 마지막 시도의 예외는 그대로 던진다.
 * 재시도 사이에는 [retryDelayMillis] × 시도횟수만큼 기다린다(테스트에서는 0).
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val retryDelayMillis: Long = 300L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var attempt = 0
        while (true) {
            val isLastAttempt = attempt >= maxRetries
            try {
                val response = chain.proceed(request)
                if (response.code < 500 || isLastAttempt) return response
                response.close()
            } catch (e: IOException) {
                // 취소된 호출이거나 마지막 시도면 그대로 전파한다
                if (isLastAttempt || chain.call().isCanceled()) throw e
            }
            attempt++
            waitBeforeRetry(attempt)
            if (chain.call().isCanceled()) throw IOException("Canceled")
        }
    }

    private fun waitBeforeRetry(attempt: Int) {
        if (retryDelayMillis <= 0) return
        try {
            Thread.sleep(retryDelayMillis * attempt)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Retry interrupted")
        }
    }
}
