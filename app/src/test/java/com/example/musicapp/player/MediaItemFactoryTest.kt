package com.example.musicapp.player

import androidx.media3.common.Player
import com.example.musicapp.data.api.model.StreamPlayResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemFactoryTest {

    private fun track(streamUrl: String, artworkUrl: String? = null) = StreamPlayResponse(
        trackId = "t1",
        streamUrl = streamUrl,
        title = "제목",
        artist = "가수",
        durationSeconds = 10,
        artworkUrl = artworkUrl,
    )

    @Test
    fun 상대경로는_baseUrl과_슬래시_중복없이_결합한다() {
        val expected = "http://localhost/media/t1/index.m3u8"
        assertEquals(expected, resolveTrackUrls("http://localhost/", track("/media/t1/index.m3u8")).streamUrl)
        assertEquals(expected, resolveTrackUrls("http://localhost", track("media/t1/index.m3u8")).streamUrl)
        assertEquals(expected, resolveTrackUrls("http://localhost/", track("media/t1/index.m3u8")).streamUrl)
    }

    @Test
    fun 절대URL은_그대로_쓴다() {
        val urls = resolveTrackUrls(
            "http://localhost/",
            track("https://cdn.example.com/a.m3u8", "http://other.example.com/a.jpg"),
        )
        assertEquals("https://cdn.example.com/a.m3u8", urls.streamUrl)
        assertEquals("http://other.example.com/a.jpg", urls.artworkUrl)
    }

    @Test
    fun 아트워크는_없으면_null_상대경로면_결합한다() {
        assertNull(resolveTrackUrls("http://localhost/", track("/a.m3u8", null)).artworkUrl)
        assertNull(resolveTrackUrls("http://localhost/", track("/a.m3u8", " ")).artworkUrl)
        assertEquals(
            "http://localhost/api/art/1",
            resolveTrackUrls("http://localhost/", track("/a.m3u8", "/api/art/1")).artworkUrl,
        )
    }

    @Test
    fun 플레이어_상태를_PlaybackStatus로_변환한다() {
        assertEquals(PlaybackStatus.Loading, mapPlaybackStatus(Player.STATE_BUFFERING, false, true))
        assertEquals(PlaybackStatus.Playing, mapPlaybackStatus(Player.STATE_READY, true, true))
        assertEquals(PlaybackStatus.Paused, mapPlaybackStatus(Player.STATE_READY, false, false))
        assertEquals(PlaybackStatus.Ended, mapPlaybackStatus(Player.STATE_ENDED, false, false))
        assertNull(mapPlaybackStatus(Player.STATE_IDLE, false, false))
    }
}
