package com.example.musicapp.feature.upload

import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.ui.getErrorMessage

const val MAX_AUDIO_BYTES: Long = 100L * 1024 * 1024
const val MAX_ARTWORK_BYTES: Long = 10L * 1024 * 1024
const val MAX_TITLE_LENGTH = 200
const val MAX_ARTIST_LENGTH = 150
const val MAX_ALBUM_LENGTH = 200

/** 업로드 사전 검증 오류. [message]는 사용자에게 보이는 문구(PRD §3.4, 웹과 동일). */
sealed class UploadValidationError(val message: String) {
    data object AudioTooLarge : UploadValidationError(getErrorMessage(ErrorCode.PAYLOAD_TOO_LARGE))
    data object ArtworkTooLarge : UploadValidationError(getErrorMessage(ErrorCode.INVALID_ARTWORK))
    data object ArtworkInvalidType : UploadValidationError(getErrorMessage(ErrorCode.INVALID_ARTWORK))
    data object TitleTooLong : UploadValidationError("제목은 ${MAX_TITLE_LENGTH}자 이하로 입력해 주세요.")
    data object ArtistTooLong : UploadValidationError("가수는 ${MAX_ARTIST_LENGTH}자 이하로 입력해 주세요.")
    data object AlbumTooLong : UploadValidationError("앨범은 ${MAX_ALBUM_LENGTH}자 이하로 입력해 주세요.")
}

/**
 * 업로드 사전 검증(순수 함수). 한도를 **초과할 때만** 오류이고, 크기·MIME을 알 수 없으면(null)
 * 서버 판단에 맡긴다. 글자 수는 서버(Java `String.length`)와 같은 UTF-16 길이이며 `trim()` 후 센다.
 */
object UploadValidator {
    private val artworkMimeTypes = setOf("image/jpeg", "image/png")

    fun validateAudio(sizeBytes: Long?): UploadValidationError? =
        if (sizeBytes != null && sizeBytes > MAX_AUDIO_BYTES) UploadValidationError.AudioTooLarge else null

    /** 자켓 MIME을 알 수 없으면(null) 형식 오류로 본다. 크기는 null이면 통과. */
    fun validateArtwork(sizeBytes: Long?, mimeType: String?): UploadValidationError? = when {
        mimeType == null || mimeType.lowercase() !in artworkMimeTypes -> UploadValidationError.ArtworkInvalidType
        sizeBytes != null && sizeBytes > MAX_ARTWORK_BYTES -> UploadValidationError.ArtworkTooLarge
        else -> null
    }

    fun validateText(title: String, artist: String, album: String): List<UploadValidationError> = buildList {
        if (title.trim().length > MAX_TITLE_LENGTH) add(UploadValidationError.TitleTooLong)
        if (artist.trim().length > MAX_ARTIST_LENGTH) add(UploadValidationError.ArtistTooLong)
        if (album.trim().length > MAX_ALBUM_LENGTH) add(UploadValidationError.AlbumTooLong)
    }

    /** 종합 검증. [artwork]가 null이면 자켓 검증을 생략한다. 오류가 없으면 빈 리스트. */
    fun validate(
        audioSizeBytes: Long?,
        title: String,
        artist: String,
        album: String,
        artwork: ArtworkInfo?,
    ): List<UploadValidationError> = buildList {
        validateAudio(audioSizeBytes)?.let(::add)
        addAll(validateText(title, artist, album))
        if (artwork != null) validateArtwork(artwork.sizeBytes, artwork.mimeType)?.let(::add)
    }
}

/** 검증에 필요한 자켓 정보(Android `Uri` 비의존). `SelectedFile`에서 만든다. */
data class ArtworkInfo(val sizeBytes: Long?, val mimeType: String?)

fun SelectedFile.toArtworkInfo(): ArtworkInfo = ArtworkInfo(sizeBytes, mimeType)
