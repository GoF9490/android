package com.example.musicapp.player

import com.example.musicapp.data.api.model.StreamPlayResponse
import kotlinx.coroutines.flow.StateFlow

/** 재생 상태. Media3 타입을 노출하지 않는다(ViewModel 테스트에서 가짜 구현을 쓰기 위해서). */
enum class PlaybackStatus { Idle, Loading, Playing, Paused, Ended, Error }

data class PlayerState(
    val currentTrack: StreamPlayResponse? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val repeatOne: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.Idle,
)

/** 재생 제어 창구. 실제 ExoPlayer는 PlaybackService가 소유하고 구현체는 MediaController로만 제어한다. */
interface PlayerRepository {
    val state: StateFlow<PlayerState>

    /** 재생 중에만 약 500ms 간격으로 갱신되는 현재 위치(ms). */
    val positionMs: StateFlow<Long>

    fun play(track: StreamPlayResponse)

    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    /** 0f..1f 범위를 벗어난 값은 범위로 보정한다. */
    fun setVolume(volume: Float)

    fun setRepeatOne(repeatOne: Boolean)

    fun stop()
}
