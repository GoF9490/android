package com.example.musicapp.feature.tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.ui.components.Artwork
import com.example.musicapp.util.formatDuration

/**
 * 트랙 행. 다운로드 버튼은 자체 클릭을 소비하므로 행 클릭([onClick])으로 전달되지 않는다.
 * [artworkUrl]은 호출부에서 BASE_URL을 적용한 값이다.
 */
@Composable
fun TrackRow(
    track: TrackResponse,
    artworkUrl: String?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 현재 재생 곡이면 true. 좌측 accent 바(모양)와 상태 설명으로 표시한다. 제목은 흰색을 유지한다. */
    isCurrent: Boolean = false,
    /** 자동 폴링이 멈춘 변환 중 행이면 true. 뱃지 옆에 `상태 새로고침` 버튼을 보인다. */
    showStatusRefresh: Boolean = false,
    onStatusRefreshClick: () -> Unit = {},
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                selected = isCurrent
                if (isCurrent) stateDescription = "현재 재생 중"
            }
            .drawBehind {
                if (isCurrent) drawRect(accentColor, size = Size(4.dp.toPx(), size.height))
            }
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(url = artworkUrl, contentDescription = null, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "노래 • ${track.artist} • ${formatDuration(track.durationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = track.status)
                if (showStatusRefresh) {
                    // 48dp 터치 영역 확보를 위해 최소 크기를 지정하고, 텍스트로 의미를 전달한다
                    TextButton(
                        onClick = onStatusRefreshClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "${track.title} 변환 상태 새로고침" },
                    ) {
                        Text("상태 새로고침", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        IconButton(onClick = onDownloadClick) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "${track.title} 원본 다운로드",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
