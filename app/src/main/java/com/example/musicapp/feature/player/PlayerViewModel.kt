package com.example.musicapp.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.player.PlaybackStatus
import com.example.musicapp.player.PlayerRepository
import com.example.musicapp.player.PlayerState
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.MessageKind
import com.example.musicapp.ui.TRACK_FAILED_MESSAGE
import com.example.musicapp.ui.UiMessage
import com.example.musicapp.ui.getErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 목록 쪽(TrackListViewModel)에 전달할 이벤트. MainActivity가 중계한다. */
sealed interface PlayerEvent {
    /** 409 뒤 재조회한 최신 트랙. 목록의 해당 행을 갱신한다. */
    data class TrackRefreshed(val track: TrackResponse) : PlayerEvent

    /** 404. 목록을 새로고침한다. */
    data object ListRefreshRequested : PlayerEvent
}

/** 행 클릭에 대한 결정. */
sealed interface PlayAction {
    /** 재생 시작(`/play` 1회 호출). */
    data object Start : PlayAction

    /** `/play` 없이 재생/일시정지 토글. */
    data object Toggle : PlayAction

    data object Ignore : PlayAction

    /** 재생하지 않고 스낵바만 표시. */
    data class Notify(val id: String, val text: String) : PlayAction
}

/**
 * 행 클릭 분기(PRD §6.3, §7.1). 순수 함수.
 * [pendingTrackId]는 `/play` 응답을 기다리는 트랙 id다.
 */
fun decidePlayAction(
    track: TrackResponse,
    playerState: PlayerState,
    pendingTrackId: String?,
): PlayAction = when (track.status) {
    TrackStatus.PENDING, TrackStatus.PROCESSING ->
        PlayAction.Notify(ErrorCode.TRACK_NOT_READY.name, getErrorMessage(ErrorCode.TRACK_NOT_READY))
    TrackStatus.FAILED -> PlayAction.Notify("TRACK_FAILED", TRACK_FAILED_MESSAGE)
    // UNKNOWN은 일반 오류 취급(PRD §16 #26)
    TrackStatus.UNKNOWN ->
        PlayAction.Notify(ErrorCode.INTERNAL_ERROR.name, getErrorMessage(ErrorCode.INTERNAL_ERROR))
    TrackStatus.READY -> when {
        pendingTrackId == track.trackId -> PlayAction.Ignore
        playerState.currentTrack?.trackId != track.trackId -> PlayAction.Start
        else -> when (playerState.status) {
            PlaybackStatus.Playing, PlaybackStatus.Paused -> PlayAction.Toggle
            PlaybackStatus.Loading -> PlayAction.Ignore
            PlaybackStatus.Ended, PlaybackStatus.Error, PlaybackStatus.Idle -> PlayAction.Start
        }
    }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playerRepository: PlayerRepository,
    private val messageBus: MessageBus,
) : ViewModel() {

    private val eventChannel = Channel<PlayerEvent>(Channel.BUFFERED)
    val events: Flow<PlayerEvent> = eventChannel.receiveAsFlow()

    /** 재생 상태. 단일 출처는 PlayerRepository이며 UI는 여기로만 구독한다. */
    val playerState: StateFlow<PlayerState> = playerRepository.state

    /** 현재 곡 id만 따로 노출한다. 재생/일시정지 변화로는 목록이 다시 그려지지 않게 한다. */
    val currentTrackId: StateFlow<String?> = playerRepository.state
        .map { it.currentTrack?.trackId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, playerRepository.state.value.currentTrack?.trackId)

    /** 재생 중 약 500ms 간격으로 갱신되는 위치(ms). 진행바 안에서만 읽는다. */
    val positionMs: StateFlow<Long> = playerRepository.positionMs

    private var requestId = 0
    private var playJob: Job? = null
    private var pendingTrackId: String? = null

    /** 미니 플레이어 재생/일시정지 버튼. `/play`는 호출하지 않는다. */
    fun onTogglePlayPause() = playerRepository.togglePlayPause()

    /** 시크 슬라이더에서 손을 뗐을 때 1회. `/play`는 호출하지 않는다. */
    fun onSeek(positionMs: Long) = playerRepository.seekTo(positionMs)

    /** 볼륨 슬라이더 드래그 중에도 즉시 반영한다(저장은 [onVolumeChangeFinished]). */
    fun onVolumeChange(volume: Float) = playerRepository.setVolume(volume)

    fun onVolumeChangeFinished() = playerRepository.commitVolume()

    /** 반복재생(REPEAT_MODE_ONE) 토글. `/play`는 호출하지 않는다. */
    fun onToggleRepeat() = playerRepository.setRepeatOne(!playerRepository.state.value.repeatOne)

    fun onTrackClick(track: TrackResponse) {
        when (val action = decidePlayAction(track, playerRepository.state.value, pendingTrackId)) {
            PlayAction.Start -> startPlay(track.trackId)
            PlayAction.Toggle -> playerRepository.togglePlayPause()
            PlayAction.Ignore -> Unit
            is PlayAction.Notify -> postMessage(action.id, action.text)
        }
    }

    /** 재생 시작 클릭 1회당 `/play` 1회. 최신이 아닌 응답은 폐기한다. */
    private fun startPlay(trackId: String) {
        val myRequestId = ++requestId
        playJob?.cancel()
        pendingTrackId = trackId
        playJob = viewModelScope.launch {
            try {
                val response = trackRepository.getPlayInfo(trackId)
                if (myRequestId != requestId) return@launch
                playerRepository.play(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (myRequestId != requestId) return@launch
                handlePlayError(trackId, e)
            } finally {
                if (myRequestId == requestId) pendingTrackId = null
            }
        }
    }

    private suspend fun handlePlayError(trackId: String, error: ApiException) {
        postMessage(error.code.name, getErrorMessage(error.code))
        when (error.code) {
            ErrorCode.TRACK_NOT_READY -> try {
                eventChannel.send(PlayerEvent.TrackRefreshed(trackRepository.getTrack(trackId)))
            } catch (e: ApiException) {
                // 재조회 실패는 알리지 않는다. 폴링·다음 새로고침이 상태를 맞춘다.
                if (e.code == ErrorCode.TRACK_NOT_FOUND) eventChannel.send(PlayerEvent.ListRefreshRequested)
            }
            ErrorCode.TRACK_NOT_FOUND -> eventChannel.send(PlayerEvent.ListRefreshRequested)
            else -> Unit
        }
    }

    private fun postMessage(id: String, text: String) {
        messageBus.post(UiMessage(id = id, text = text, kind = MessageKind.ERROR))
    }
}
