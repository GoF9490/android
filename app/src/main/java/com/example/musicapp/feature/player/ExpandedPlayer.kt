package com.example.musicapp.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.player.PlaybackStatus
import com.example.musicapp.ui.components.Artwork
import com.example.musicapp.util.formatClock
import com.example.musicapp.util.resolveMediaUrl
import kotlin.math.roundToInt

/**
 * 확장 플레이어(풀스크린). 상태와 콜백만 받는다(ViewModel 연결은 [ExpandedPlayerHost]).
 * [positionMs]는 람다로 받아 [SeekSlider] 안에서만 읽는다(500ms 갱신이 화면 전체를 다시 그리지 않게).
 */
@Composable
fun ExpandedPlayer(
    track: StreamPlayResponse,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatOne: Boolean,
    volume: Float,
    baseUrl: String,
    positionMs: () -> Long,
    durationMs: Long,
    onToggleClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onRepeatClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onCloseClick) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "플레이어 닫기")
                }
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(
                    url = track.artworkUrl?.let { resolveMediaUrl(baseUrl, it) },
                    contentDescription = "${track.title} 앨범자켓",
                    size = minOf(maxWidth, 320.dp),
                    cornerRadius = 8.dp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            SeekSlider(positionMs = positionMs, durationMs = durationMs, onSeek = onSeek)
            Spacer(Modifier.height(8.dp))
            // 재생/일시정지(가운데), 반복·다운로드(양옆)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RepeatToggle(repeatOne = repeatOne, onClick = onRepeatClick)
                if (isLoading) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp).semantics { contentDescription = "재생 준비 중" },
                            strokeWidth = 3.dp,
                        )
                    }
                } else {
                    FilledIconButton(onClick = onToggleClick, modifier = Modifier.size(64.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "일시정지" else "재생",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "${track.title} 원본 다운로드",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            VolumeSlider(volume = volume, onChange = onVolumeChange, onChangeFinished = onVolumeChangeFinished)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 시크 슬라이더. 드래그 중에는 로컬 값만 갱신하고 손을 뗄 때 [onSeek]를 1회 호출한다.
 * 위치 State는 이 함수 안에서만 읽는다.
 */
@Composable
private fun SeekSlider(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: positionToFraction(positionMs(), durationMs)
    val shownMs = fractionToPositionMs(fraction, durationMs)
    val clock = "${formatClock(shownMs)} / ${formatClock(durationMs)}"
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek(fractionToPositionMs(it, durationMs)) }
                dragFraction = null
            },
            enabled = durationMs > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "재생 위치"
                    stateDescription = clock
                },
        )
        Text(
            text = clock,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
    }
}

@Composable
private fun VolumeSlider(volume: Float, onChange: (Float) -> Unit, onChangeFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = volume,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .semantics {
                    contentDescription = "볼륨"
                    stateDescription = "${(volume * 100).roundToInt()}%"
                },
        )
    }
}

/** 반복재생 토글. 켜지면 아이콘이 바뀌고(RepeatOne) 강조색이 되며, 상태 설명(켜짐/꺼짐)을 함께 전달한다. */
@Composable
private fun RepeatToggle(repeatOne: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = repeatOne,
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics {
            contentDescription = "반복재생"
            stateDescription = if (repeatOne) "켜짐" else "꺼짐"
        },
    ) {
        Icon(
            imageVector = if (repeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = null,
            tint = if (repeatOne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * ViewModel 연결. 풀스크린 [Dialog]로 띄우며 뒤로 가기는 [onCloseClick]로 닫는다.
 * 곡이 없으면 아무것도 그리지 않는다.
 */
@Composable
fun ExpandedPlayerHost(
    viewModel: PlayerViewModel,
    baseUrl: String,
    onDownloadClick: (StreamPlayResponse) -> Unit,
    onCloseClick: () -> Unit,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ExpandedPlayer(
            track = track,
            isPlaying = state.isPlaying,
            isLoading = state.status == PlaybackStatus.Loading,
            repeatOne = state.repeatOne,
            volume = state.volume,
            baseUrl = baseUrl,
            positionMs = { position.value },
            durationMs = resolveDurationMs(state.durationMs, track.durationSeconds),
            onToggleClick = viewModel::onTogglePlayPause,
            onSeek = viewModel::onSeek,
            onVolumeChange = viewModel::onVolumeChange,
            onVolumeChangeFinished = viewModel::onVolumeChangeFinished,
            onRepeatClick = viewModel::onToggleRepeat,
            onDownloadClick = { onDownloadClick(track) },
            onCloseClick = onCloseClick,
        )
    }
}
