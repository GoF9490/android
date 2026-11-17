package com.example.musicapp.data.tracks

import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.data.api.model.TrackPageResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.normalizeError
import com.example.musicapp.feature.upload.SelectedFile
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody

/** 트랙 조회. 모든 오류는 `ApiException`으로 정규화하고 취소 예외는 그대로 던진다. */
@Singleton
class TrackRepository @Inject constructor(
    private val api: MusicApi,
    /** 타임아웃 해제·재시도 없음 클라이언트로 만든 업로드 전용 API. */
    @Named("upload") private val uploadApi: MusicApi,
    private val streamOpener: UriStreamOpener,
) {

    /** keyword가 null이면 쿼리를 보내지 않는다(호출부가 trim·빈 값 처리를 마친 값을 넘긴다). */
    suspend fun getTracks(keyword: String?, page: Int): TrackPageResponse {
        try {
            return api.getTracks(keyword, page, PAGE_SIZE)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    suspend fun getTrack(trackId: String): TrackResponse {
        try {
            return api.getTrack(trackId)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    /** 재생 정보 조회. 호출마다 STREAM 이력이 쌓이므로 재생 시작 1회당 1번만 부른다. */
    suspend fun getPlayInfo(trackId: String): StreamPlayResponse {
        try {
            return api.getPlayInfo(trackId)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    /**
     * 음원 업로드(202, PENDING 응답). title·artist·album은 `trim()` 후 값이 있을 때만 파트로 보낸다.
     * [onProgress]는 file 파트 기준 0..100을 스로틀해 OkHttp 쓰기 스레드에서 호출한다.
     * 코루틴이 취소되면 진행 중인 요청도 취소된다.
     */
    suspend fun uploadTrack(
        file: SelectedFile,
        title: String?,
        artist: String?,
        album: String?,
        artwork: SelectedFile?,
        onProgress: (Int) -> Unit,
    ): TrackResponse {
        try {
            return uploadApi.uploadTrack(
                title = textPart("title", title),
                artist = textPart("artist", artist),
                album = textPart("album", album),
                artworkFile = artwork?.let { filePart("artworkFile", it, null) },
                file = filePart("file", file, onProgress),
            )
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun textPart(name: String, value: String?): MultipartBody.Part? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else MultipartBody.Part.createFormData(name, trimmed)
    }

    private fun filePart(name: String, file: SelectedFile, onProgress: ((Int) -> Unit)?): MultipartBody.Part {
        val mediaType = file.mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val body = ContentUriRequestBody(streamOpener, file.uri, mediaType, file.sizeBytes ?: -1L, onProgress)
        return MultipartBody.Part.createFormData(name, file.displayName, body)
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
