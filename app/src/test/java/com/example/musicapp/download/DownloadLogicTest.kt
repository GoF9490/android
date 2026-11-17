package com.example.musicapp.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLogicTest {

    @Test
    fun buildDownloadUrl_끝슬래시_있어도_중복없음() {
        assertEquals("http://localhost/api/tracks/abc/download", buildDownloadUrl("http://localhost/", "abc"))
        assertEquals("http://localhost/api/tracks/abc/download", buildDownloadUrl("http://localhost", "abc"))
    }

    @Test
    fun sanitizeFilename_한글은_유지() {
        assertEquals("내 노래.mp3", sanitizeFilename("내 노래.mp3", "id1"))
    }

    @Test
    fun sanitizeFilename_경로구분자와_제어문자는_치환() {
        assertEquals(".._etc_passwd", sanitizeFilename("../etc/passwd", "id1"))
        assertEquals("a_b_c.mp3", sanitizeFilename("a\\b\nc.mp3", "id1"))
    }

    @Test
    fun sanitizeFilename_빈값_점만_null은_기본이름() {
        assertEquals("track-id1", sanitizeFilename(null, "id1"))
        assertEquals("track-id1", sanitizeFilename("  ", "id1"))
        assertEquals("track-id1", sanitizeFilename("..", "id1"))
    }

    @Test
    fun needsStoragePermission_API28이하_미허용만_true() {
        assertTrue(needsStoragePermission(28, false))
        assertFalse(needsStoragePermission(28, true))
        assertFalse(needsStoragePermission(29, false))
    }
}
