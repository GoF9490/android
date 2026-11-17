package com.example.musicapp.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.data.player.VolumeStore
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POSITION_INTERVAL_MS = 500L

/**
 * PlaybackService의 세션에 MediaController로 연결해 재생을 제어한다. 메인 스레드에서만 호출한다.
 * 컨트롤러 연결 전의 호출은 큐에 쌓았다가 연결 후 순서대로 실행한다(play는 최신 1개만 유지).
 */
@Singleton
class MediaControllerPlayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("baseUrl") private val baseUrl: String,
    private val volumeStore: VolumeStore,
) : PlayerRepository {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private class PendingAction(val isPlay: Boolean, val action: (MediaController) -> Unit)

    private val pending = ArrayDeque<PendingAction>()

    /** 사용자가 저장값 로드보다 먼저 볼륨을 바꿨으면 로드한 값으로 덮어쓰지 않는다. */
    private var volumeTouched = false

    init {
        // 저장된 볼륨을 복원한다. 컨트롤러가 이미 연결되어 있으면 바로 적용한다.
        scope.launch {
            val saved = volumeStore.load()
            if (!volumeTouched) {
                _state.update { it.copy(volume = saved) }
                controller?.volume = saved
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncStatus()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncStatus()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = syncStatus()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncStatus()

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatOne = repeatMode == Player.REPEAT_MODE_ONE) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // URL·토큰이 담길 수 있는 메시지는 남기지 않는다.
            stopPositionUpdates()
            _state.update { it.copy(status = PlaybackStatus.Error, isPlaying = false) }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            // 서비스가 종료되면 다음 호출에서 재연결한다.
            this@MediaControllerPlayerRepository.controller = null
            controllerFuture = null
            stopPositionUpdates()
            _state.value = PlayerState(repeatOne = _state.value.repeatOne, volume = _state.value.volume)
            _positionMs.value = 0L
        }
    }

    override fun play(track: StreamPlayResponse) {
        _state.update {
            it.copy(
                currentTrack = track,
                isPlaying = false,
                durationMs = track.durationSeconds * 1000L,
                status = PlaybackStatus.Loading,
            )
        }
        _positionMs.value = 0L
        runWhenConnected(isPlay = true) { c ->
            c.volume = _state.value.volume
            c.setMediaItem(createMediaItem(baseUrl, track))
            c.repeatMode = if (_state.value.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            c.prepare()
            c.play()
        }
    }

    override fun togglePlayPause() {
        runWhenConnected { c ->
            if (c.playbackState == Player.STATE_ENDED) {
                c.seekTo(0)
                c.play()
            } else if (c.isPlaying || c.playWhenReady) {
                c.pause()
            } else {
                c.play()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _positionMs.value = target
        runWhenConnected { it.seekTo(target) }
    }

    override fun setVolume(volume: Float) {
        val target = volume.coerceIn(0f, 1f)
        volumeTouched = true
        _state.update { it.copy(volume = target) }
        runWhenConnected { it.volume = target }
    }

    override fun commitVolume() {
        val target = _state.value.volume
        scope.launch {
            try {
                volumeStore.save(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 저장 실패는 다음 조정 때 다시 시도한다. 재생에는 영향이 없다.
            }
        }
    }

    override fun setRepeatOne(repeatOne: Boolean) {
        _state.update { it.copy(repeatOne = repeatOne) }
        runWhenConnected {
            it.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    override fun stop() {
        stopPositionUpdates()
        _state.update { PlayerState(repeatOne = it.repeatOne, volume = it.volume) }
        _positionMs.value = 0L
        pending.removeAll { it.isPlay }
        runWhenConnected {
            it.stop()
            it.clearMediaItems()
        }
    }

    /** 연결되어 있으면 즉시, 아니면 연결 후 실행한다. 새 play는 큐의 이전 play를 대체한다. */
    private fun runWhenConnected(isPlay: Boolean = false, action: (MediaController) -> Unit) {
        val connected = controller
        if (connected != null) {
            action(connected)
            return
        }
        if (isPlay) pending.removeAll { it.isPlay }
        pending.addLast(PendingAction(isPlay, action))
        ensureConnecting()
    }

    private fun ensureConnecting() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                if (connected == null) {
                    handleConnectionFailure()
                } else {
                    controller = connected
                    connected.addListener(listener)
                    connected.volume = _state.value.volume
                    _state.update { it.copy(repeatOne = connected.repeatMode == Player.REPEAT_MODE_ONE || it.repeatOne) }
                    while (pending.isNotEmpty()) pending.removeFirst().action(connected)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun handleConnectionFailure() {
        controllerFuture = null
        pending.clear()
        _state.update { it.copy(status = PlaybackStatus.Error, isPlaying = false) }
    }

    private fun syncStatus() {
        val c = controller ?: return
        // 서비스가 재생 목록을 비웠다(최근 앱 제거 등). 남아 있는 곡 정보로 미니 플레이어가 유령처럼 남지 않게 초기화한다.
        if (c.mediaItemCount == 0 && _state.value.currentTrack != null && _state.value.status != PlaybackStatus.Loading) {
            stopPositionUpdates()
            _state.update { PlayerState(repeatOne = it.repeatOne, volume = it.volume) }
            _positionMs.value = 0L
            return
        }
        val mapped = mapPlaybackStatus(c.playbackState, c.isPlaying, c.playWhenReady)
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        _state.update { current ->
            // 오류 상태는 새 play 전까지 유지하되, 재생 준비가 시작되면 벗어난다.
            val status = mapped ?: current.status
            current.copy(
                status = status,
                isPlaying = c.isPlaying,
                durationMs = duration ?: current.durationMs,
            )
        }
        if (c.playbackState == Player.STATE_ENDED) {
            stopPositionUpdates()
            _positionMs.value = c.duration.takeIf { it != C.TIME_UNSET } ?: _positionMs.value
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { _positionMs.value = it.currentPosition.coerceAtLeast(0L) }
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        controller?.let { if (it.playbackState != Player.STATE_IDLE) _positionMs.value = it.currentPosition.coerceAtLeast(0L) }
    }
}
