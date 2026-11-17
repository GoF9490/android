package com.example.musicapp.data.tracks

import android.net.Uri
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source

/** `content://` Uri를 스트림으로 여는 의존성. 테스트에서 가짜로 대체한다. 열 수 없으면 null. */
fun interface UriStreamOpener {
    fun open(uri: Uri): InputStream?
}

/**
 * Uri의 내용을 메모리에 통째로 올리지 않고 청크 단위로 전송하는 [RequestBody].
 * [length]가 음수이면 chunked 전송이며 진행률은 보고하지 않는다.
 * [onProgress]는 OkHttp 쓰기 스레드에서 호출된다(스로틀 적용, 값은 0..100).
 */
class ContentUriRequestBody(
    private val openStream: () -> InputStream?,
    private val mediaType: MediaType?,
    private val length: Long,
    private val onProgress: ((Int) -> Unit)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : RequestBody() {

    constructor(
        opener: UriStreamOpener,
        uri: Uri,
        mediaType: MediaType?,
        length: Long,
        onProgress: ((Int) -> Unit)? = null,
    ) : this({ opener.open(uri) }, mediaType, length, onProgress)

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = if (length >= 0) length else -1L

    /** 스트림은 한 번만 읽을 수 있으므로 재전송(리다이렉트·재시도)을 허용하지 않는다. */
    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        val input = openStream() ?: throw IOException("파일을 열 수 없습니다.")
        val throttle = onProgress?.let { UploadProgressThrottle(clock = clock, onProgress = it) }
        input.source().use { source ->
            val buffer = Buffer()
            var written = 0L
            throttle?.report(0, length)
            while (true) {
                val read = source.read(buffer, CHUNK_BYTES)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                throttle?.report(written, length)
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 8192L
    }
}
