package com.example.musicapp.feature.tracks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.ui.theme.extendedColors

/** 변환 상태 뱃지. READY면 아무것도 그리지 않는다. 텍스트를 포함해 색에만 의존하지 않는다. */
@Composable
fun StatusBadge(status: TrackStatus, modifier: Modifier = Modifier) {
    val label = getStatusLabel(status) ?: return
    val isFailed = status == TrackStatus.FAILED || status == TrackStatus.UNKNOWN
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isFailed) MaterialTheme.extendedColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
