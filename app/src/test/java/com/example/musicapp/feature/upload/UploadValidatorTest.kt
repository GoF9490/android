package com.example.musicapp.feature.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadValidatorTest {
    private val mb = 1024L * 1024

    @Test
    fun validateAudio_경계값() {
        assertNull(UploadValidator.validateAudio(100 * mb))
        assertNull(UploadValidator.validateAudio(0))
        assertNull(UploadValidator.validateAudio(null))
        assertEquals(UploadValidationError.AudioTooLarge, UploadValidator.validateAudio(100 * mb + 1))
    }

    @Test
    fun validateAudio_1024단위() {
        assertNull(UploadValidator.validateAudio(100_000_000L))
        assertEquals(UploadValidationError.AudioTooLarge, UploadValidator.validateAudio(104_857_601L))
        assertEquals(104_857_600L, MAX_AUDIO_BYTES)
    }

    @Test
    fun validateArtwork_크기_경계값() {
        assertNull(UploadValidator.validateArtwork(10 * mb, "image/jpeg"))
        assertNull(UploadValidator.validateArtwork(null, "image/png"))
        assertEquals(
            UploadValidationError.ArtworkTooLarge,
            UploadValidator.validateArtwork(10 * mb + 1, "image/jpeg"),
        )
    }

    @Test
    fun validateArtwork_MIME() {
        assertNull(UploadValidator.validateArtwork(1, "image/jpeg"))
        assertNull(UploadValidator.validateArtwork(1, "image/png"))
        assertNull(UploadValidator.validateArtwork(1, "IMAGE/PNG"))
        for (type in listOf("image/gif", "image/webp", null)) {
            assertEquals(
                UploadValidationError.ArtworkInvalidType,
                UploadValidator.validateArtwork(1, type),
            )
        }
    }

    @Test
    fun validateText_글자수_경계값() {
        assertTrue(UploadValidator.validateText("a".repeat(200), "a".repeat(150), "a".repeat(200)).isEmpty())
        assertEquals(
            listOf(UploadValidationError.TitleTooLong),
            UploadValidator.validateText("a".repeat(201), "", ""),
        )
        assertEquals(
            listOf(UploadValidationError.ArtistTooLong),
            UploadValidator.validateText("", "a".repeat(151), ""),
        )
        assertEquals(
            listOf(UploadValidationError.AlbumTooLong),
            UploadValidator.validateText("", "", "a".repeat(201)),
        )
    }

    @Test
    fun validateText_trim_후_센다() {
        val padded = "  " + "a".repeat(200) + "  "
        assertTrue(UploadValidator.validateText(padded, "", "").isEmpty())
        assertTrue(UploadValidator.validateText(" ".repeat(500), " ".repeat(500), " ".repeat(500)).isEmpty())
    }

    @Test
    fun 오류_문구는_웹과_같다() {
        assertEquals(
            "파일이 너무 큽니다. 음원은 100MB, 앨범자켓은 10MB 이하여야 합니다.",
            UploadValidationError.AudioTooLarge.message,
        )
        val artworkMessage = "앨범자켓은 10MB 이하의 JPEG 또는 PNG 이미지만 사용할 수 있습니다."
        assertEquals(artworkMessage, UploadValidationError.ArtworkTooLarge.message)
        assertEquals(artworkMessage, UploadValidationError.ArtworkInvalidType.message)
        assertEquals("제목은 200자 이하로 입력해 주세요.", UploadValidationError.TitleTooLong.message)
        assertEquals("가수는 150자 이하로 입력해 주세요.", UploadValidationError.ArtistTooLong.message)
        assertEquals("앨범은 200자 이하로 입력해 주세요.", UploadValidationError.AlbumTooLong.message)
    }

    @Test
    fun validate_종합() {
        assertTrue(UploadValidator.validate(1, "t", "a", "b", null).isEmpty())
        assertTrue(UploadValidator.validate(null, "", "", "", ArtworkInfo(1, "image/png")).isEmpty())

        val errors = UploadValidator.validate(
            audioSizeBytes = 100 * mb + 1,
            title = "a".repeat(201),
            artist = "a".repeat(151),
            album = "a".repeat(201),
            artwork = ArtworkInfo(10 * mb + 1, "image/gif"),
        )
        assertEquals(
            listOf(
                UploadValidationError.AudioTooLarge,
                UploadValidationError.TitleTooLong,
                UploadValidationError.ArtistTooLong,
                UploadValidationError.AlbumTooLong,
                UploadValidationError.ArtworkInvalidType,
            ),
            errors,
        )
    }
}
