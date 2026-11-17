package com.example.musicapp.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.util.resolveMediaUrl

/** 트랙의 스트림·아트워크 URL을 BASE_URL과 결합한다(플랫폼 의존 없음). */
data class ResolvedMediaUrls(val streamUrl: String, val artworkUrl: String?)

fun resolveTrackUrls(baseUrl: String, track: StreamPlayResponse): ResolvedMediaUrls =
    ResolvedMediaUrls(
        streamUrl = resolveMediaUrl(baseUrl, track.streamUrl),
        artworkUrl = track.artworkUrl?.takeIf { it.isNotBlank() }?.let { resolveMediaUrl(baseUrl, it) },
    )

/** HLS(m3u8) MediaItem을 만든다. 미디어 요청에는 인증 헤더가 필요 없다. */
fun createMediaItem(baseUrl: String, track: StreamPlayResponse): MediaItem {
    val urls = resolveTrackUrls(baseUrl, track)
    val metadata = MediaMetadata.Builder()
        .setTitle(track.title)
        .setArtist(track.artist)
        .setArtworkUri(urls.artworkUrl?.toUri())
        .build()
    return MediaItem.Builder()
        .setMediaId(track.trackId)
        .setUri(urls.streamUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .setMediaMetadata(metadata)
        .build()
}

/** Player.STATE_* 와 isPlaying을 PlaybackStatus로 변환한다. IDLE은 null(이전 상태 유지). */
fun mapPlaybackStatus(playbackState: Int, isPlaying: Boolean, playWhenReady: Boolean): PlaybackStatus? =
    when (playbackState) {
        Player.STATE_BUFFERING -> PlaybackStatus.Loading
        Player.STATE_READY -> if (isPlaying || playWhenReady) PlaybackStatus.Playing else PlaybackStatus.Paused
        Player.STATE_ENDED -> PlaybackStatus.Ended
        else -> null
    }
