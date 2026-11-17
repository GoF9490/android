package com.example.musicapp.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.player.PlaybackStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.ui.components.Artwork
import com.example.musicapp.util.resolveMediaUrl

/**
 * 하단 미니 플레이어 바. 상태와 콜백만 받는다(ViewModel 연결은 호출부).
 * [positionMs]는 람다로 받아 진행바의 그리기 단계에서만 읽는다(500ms 갱신이 recomposition을 일으키지 않게).
 */
@Composable
fun MiniPlayerBar(
    track: StreamPlayResponse,
    isPlaying: Boolean,
    isLoading: Boolean,
    baseUrl: String,
    positionMs: () -> Long,
    durationMs: Long,
    onToggleClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MiniProgressBar(positionMs = positionMs, durationMs = durationMs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "플레이어 열기", onClick = onOpenClick)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(
                url = track.artworkUrl?.let { resolveMediaUrl(baseUrl, it) },
                contentDescription = null,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLoading) {
                // 버튼 자리를 유지해 레이아웃이 흔들리지 않게 한다
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "재생 준비 중" },
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = onToggleClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "일시정지" else "재생",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * ViewModel 연결. 위치 State는 읽지 않고 람다로만 넘겨 진행바의 그리기 단계에서만 읽는다.
 * 곡이 없으면 아무것도 그리지 않는다.
 */
@Composable
fun MiniPlayerHost(
    viewModel: PlayerViewModel,
    baseUrl: String,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    MiniPlayerBar(
        track = track,
        isPlaying = state.isPlaying,
        isLoading = state.status == PlaybackStatus.Loading,
        baseUrl = baseUrl,
        positionMs = { position.value },
        // 플레이어 duration이 확정되기 전에는 서버가 준 길이를 쓴다
        durationMs = resolveDurationMs(state.durationMs, track.durationSeconds),
        onToggleClick = viewModel::onTogglePlayPause,
        onOpenClick = onOpenClick,
        modifier = modifier,
    )
}

/** 얇은 진행바. 위치는 그리기 단계에서만 읽는다. 장식이므로 접근성 트리에서 숨긴다. */
@Composable
private fun MiniProgressBar(positionMs: () -> Long, durationMs: Long) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(trackColor)
                val fraction = if (durationMs > 0) (positionMs().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                drawRect(progressColor, size = Size(size.width * fraction, size.height))
            },
    )
}
