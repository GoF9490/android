package com.example.musicapp.data.api

import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.data.api.model.TrackPageResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.UserResponse
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 백엔드 REST API. 경로는 앞 슬래시 없이 쓰며 baseUrl 끝 슬래시는 NetworkModule(Task 004)이 보장한다.
 * `/download`는 Retrofit으로 만들지 않는다(Task 023에서 DownloadManager가 URL로 직접 요청).
 * 기본 인자는 두지 않는다. 호출부가 값을 명시한다.
 */
interface MusicApi {

    /** 로그인 사용자 조회. 이 API만 401을 반환한다. */
    @GET("api/auth/me")
    suspend fun getMe(): UserResponse

    /** keyword가 null이면 쿼리를 보내지 않는다. */
    @GET("api/tracks")
    suspend fun getTracks(
        @Query("keyword") keyword: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): TrackPageResponse

    @GET("api/tracks/{trackId}")
    suspend fun getTrack(@Path("trackId") trackId: String): TrackResponse

    /** READY가 아니면 409. 재생 시작 클릭당 1회만 호출한다. */
    @GET("api/tracks/{trackId}/play")
    suspend fun getPlayInfo(@Path("trackId") trackId: String): StreamPlayResponse

    /**
     * 업로드. 필드명은 `Part.createFormData` 이름으로 정해진다
     * (`file`, `title`, `artist`, `album`, `artworkFile`). null 파트는 전송에서 빠진다.
     */
    @Multipart
    @POST("api/tracks/upload")
    suspend fun uploadTrack(
        @Part file: MultipartBody.Part,
        @Part title: MultipartBody.Part?,
        @Part artist: MultipartBody.Part?,
        @Part album: MultipartBody.Part?,
        @Part artworkFile: MultipartBody.Part?,
    ): TrackResponse
}
