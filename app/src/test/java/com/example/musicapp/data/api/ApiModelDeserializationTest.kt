package com.example.musicapp.data.api

import com.example.musicapp.data.api.model.ErrorResponse
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.data.api.model.TrackPageResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.data.api.model.UserResponse
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiModelDeserializationTest {

    private val trackJson = """
        {
            "trackId": "3f2b8c1e-5a4d-4e7b-9c10-1a2b3c4d5e6f",
            "title": "My Song",
            "artist": "Artist",
            "album": null,
            "durationSeconds": 215,
            "status": "READY",
            "artworkUrl": null,
            "originalFilename": "song.mp3",
            "fileExtension": "mp3",
            "fileSizeBytes": 5242880,
            "createdAt": "2026-11-11T01:00:00Z"
        }
    """.trimIndent()

    private fun trackWith(statusField: String): String = """
        {
            "trackId": "t1", "title": "T", "artist": "A", "durationSeconds": 1,
            $statusField
            "originalFilename": "a.mp3", "fileExtension": "mp3",
            "fileSizeBytes": 1, "createdAt": "2026-11-11T01:00:00Z"
        }
    """.trimIndent()

    @Test
    fun decodeTrackPage_예시_JSON을_모든_필드로_역직렬화한다() {
        val json = """
            {"content": [$trackJson], "page": 0, "size": 20,
             "totalElements": 1, "totalPages": 1, "hasNext": false}
        """.trimIndent()

        val page = apiJson.decodeFromString<TrackPageResponse>(json)

        assertEquals(0, page.page)
        assertEquals(20, page.size)
        assertEquals(1L, page.totalElements)
        assertEquals(1, page.totalPages)
        assertFalse(page.hasNext)
        val track = page.content.single()
        assertEquals("3f2b8c1e-5a4d-4e7b-9c10-1a2b3c4d5e6f", track.trackId)
        assertEquals("My Song", track.title)
        assertEquals("Artist", track.artist)
        assertNull(track.album)
        assertNull(track.artworkUrl)
        assertEquals(215, track.durationSeconds)
        assertEquals(TrackStatus.READY, track.status)
        assertEquals("song.mp3", track.originalFilename)
        assertEquals("mp3", track.fileExtension)
        assertEquals(5242880L, track.fileSizeBytes)
        assertEquals("2026-11-11T01:00:00Z", track.createdAt)
    }

    @Test
    fun decodeTrack_업로드_응답은_PENDING과_artworkUrl을_가진다() {
        val json = """
            {"trackId": "t1", "title": "My Song", "artist": "Artist", "album": null,
             "durationSeconds": 215, "status": "PENDING",
             "artworkUrl": "/artwork/t1.jpg", "originalFilename": "song.mp3",
             "fileExtension": "mp3", "fileSizeBytes": 5242880,
             "createdAt": "2026-11-11T01:00:00Z"}
        """.trimIndent()

        val track = apiJson.decodeFromString<TrackResponse>(json)

        assertEquals(TrackStatus.PENDING, track.status)
        assertEquals("/artwork/t1.jpg", track.artworkUrl)
        assertNull(track.album)
    }

    @Test
    fun decodeStreamPlay_artworkUrl이_있거나_null_또는_누락이어도_역직렬화한다() {
        val base = """"trackId": "t1", "streamUrl": "/stream/t1/index.m3u8",
            "title": "My Song", "artist": "Artist", "durationSeconds": 215"""

        val withArt = apiJson.decodeFromString<StreamPlayResponse>(
            "{$base, \"artworkUrl\": \"/artwork/t1.jpg\"}"
        )
        val withNull = apiJson.decodeFromString<StreamPlayResponse>("{$base, \"artworkUrl\": null}")
        val missing = apiJson.decodeFromString<StreamPlayResponse>("{$base}")

        assertEquals("/stream/t1/index.m3u8", withArt.streamUrl)
        assertEquals("/artwork/t1.jpg", withArt.artworkUrl)
        assertNull(withNull.artworkUrl)
        assertNull(missing.artworkUrl)
    }

    @Test
    fun decodeUser_profileImageUrl이_있거나_null이어도_역직렬화한다() {
        val withImage = apiJson.decodeFromString<UserResponse>(
            """{"userId": 1, "email": "user@example.com",
                "profileImageUrl": "https://lh3.googleusercontent.com/a/example",
                "role": "ROLE_USER"}"""
        )
        val withNull = apiJson.decodeFromString<UserResponse>(
            """{"userId": 1, "email": "user@example.com", "profileImageUrl": null, "role": "ROLE_USER"}"""
        )

        assertEquals(1L, withImage.userId)
        assertEquals("https://lh3.googleusercontent.com/a/example", withImage.profileImageUrl)
        assertNull(withNull.profileImageUrl)
        assertEquals("ROLE_USER", withNull.role)
    }

    @Test
    fun decodeError_오류_예시를_역직렬화한다() {
        val error = apiJson.decodeFromString<ErrorResponse>(
            """{"code": "TRACK_NOT_READY", "message": "음원 변환이 아직 완료되지 않았습니다.",
                "timestamp": "2026-11-11T10:00:00+09:00"}"""
        )

        assertEquals("TRACK_NOT_READY", error.code)
        assertEquals("2026-11-11T10:00:00+09:00", error.timestamp)
    }

    @Test
    fun decode_모르는_필드가_추가되어도_예외_없이_역직렬화한다() {
        val track = apiJson.decodeFromString<TrackResponse>(
            trackJson.replaceFirst("{", """{"newField": 1, "nested": {"a": [1, 2]},""")
        )
        val page = apiJson.decodeFromString<TrackPageResponse>(
            """{"content": [], "page": 0, "size": 20, "totalElements": 0,
                "totalPages": 0, "hasNext": false, "extra": "x"}"""
        )
        val user = apiJson.decodeFromString<UserResponse>(
            """{"userId": 1, "email": "a@b.c", "role": "ROLE_USER", "newField": true}"""
        )

        assertEquals("My Song", track.title)
        assertEquals(0, page.content.size)
        assertNull(user.profileImageUrl)
    }

    @Test
    fun decodeTrack_status_네_값을_각각_매핑한다() {
        listOf(
            "PENDING" to TrackStatus.PENDING,
            "PROCESSING" to TrackStatus.PROCESSING,
            "READY" to TrackStatus.READY,
            "FAILED" to TrackStatus.FAILED,
        ).forEach { (raw, expected) ->
            val track = apiJson.decodeFromString<TrackResponse>(trackWith("\"status\": \"$raw\","))
            assertEquals(expected, track.status)
        }
    }

    @Test
    fun decodeTrack_알_수_없는_status는_UNKNOWN이고_나머지_필드는_정상이다() {
        val track = apiJson.decodeFromString<TrackResponse>(trackWith("\"status\": \"ARCHIVED\","))

        assertEquals(TrackStatus.UNKNOWN, track.status)
        assertEquals("T", track.title)
        assertEquals(1L, track.fileSizeBytes)
    }

    @Test
    fun decodeTrack_status가_누락되거나_null이면_UNKNOWN이다() {
        val missing = apiJson.decodeFromString<TrackResponse>(trackWith(""))
        val nulled = apiJson.decodeFromString<TrackResponse>(trackWith("\"status\": null,"))

        assertEquals(TrackStatus.UNKNOWN, missing.status)
        assertEquals(TrackStatus.UNKNOWN, nulled.status)
    }

    @Test
    fun decodeTrack_필수_필드가_없으면_SerializationException이다() {
        val json = trackJson.replace("\"trackId\": \"3f2b8c1e-5a4d-4e7b-9c10-1a2b3c4d5e6f\",", "")

        assertThrows(SerializationException::class.java) {
            apiJson.decodeFromString<TrackResponse>(json)
        }
    }
}
