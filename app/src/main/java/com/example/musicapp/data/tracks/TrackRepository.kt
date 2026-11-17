package com.example.musicapp.data.tracks

import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.model.TrackPageResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.normalizeError
import javax.inject.Inject
import javax.inject.Singleton

/** 트랙 조회. 모든 오류는 `ApiException`으로 정규화하고 취소 예외는 그대로 던진다. */
@Singleton
class TrackRepository @Inject constructor(
    private val api: MusicApi,
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

    companion object {
        const val PAGE_SIZE = 20
    }
}
