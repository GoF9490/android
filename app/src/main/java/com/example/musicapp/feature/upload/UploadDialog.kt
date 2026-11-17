package com.example.musicapp.feature.upload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.musicapp.ui.components.Artwork
import com.example.musicapp.ui.theme.MusicAppTheme

/**
 * 업로드 다이얼로그(PRD §5). 상태와 콜백만 받으며 표시 여부는 호출 측이 관리한다.
 * 전송 중에는 입력을 잠그고, 닫기 요청은 [onCloseRequest]로 보내 호출 측이 확인 대화상자를 띄운다.
 */
@Composable
fun UploadDialog(
    state: UploadState,
    showCloseConfirm: Boolean,
    onPickAudio: () -> Unit,
    onPickArtwork: () -> Unit,
    onClearArtwork: () -> Unit,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelUpload: () -> Unit,
    onCloseRequest: () -> Unit,
    onConfirmContinue: () -> Unit,
    onConfirmClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = state.phase == UploadPhase.Editing
    Dialog(
        onDismissRequest = onCloseRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("업로드", style = MaterialTheme.typography.headlineSmall)
                AudioPickerRow(state = state, enabled = isEditing, onPickAudio = onPickAudio)
                ArtworkPicker(
                    state = state,
                    enabled = isEditing,
                    onPickArtwork = onPickArtwork,
                    onClearArtwork = onClearArtwork,
                )
                MetadataField("제목", state.title, state.titlePlaceholder, isEditing, onTitleChange)
                MetadataField("가수", state.artist, state.artistPlaceholder, isEditing, onArtistChange)
                MetadataField("앨범", state.album, "", isEditing, onAlbumChange)
                ErrorList(state.errors)
                if (!isEditing) UploadProgress(state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isEditing) {
                        DialogTextButton(onClick = onCancelUpload, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("전송 취소")
                        }
                    }
                    DialogTextButton(onClick = onCloseRequest, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("취소")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("저장")
                    }
                }
            }
        }
    }
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = onConfirmContinue,
            title = { Text("업로드를 취소할까요?") },
            text = { Text("전송 중인 업로드가 중단됩니다.") },
            confirmButton = { DialogTextButton(onClick = onConfirmContinue) { Text("계속 업로드") } },
            dismissButton = { DialogTextButton(onClick = onConfirmClose) { Text("전송 취소하고 닫기") } },
        )
    }
}

@Composable
private fun AudioPickerRow(state: UploadState, enabled: Boolean, onPickAudio: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPickAudio, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (state.file == null) "파일 선택" else "파일 변경")
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.file?.displayName ?: "선택된 파일 없음",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.isReadingTags) {
                Text(
                    "태그 읽는 중...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 표시 우선순위: 직접 고른 자켓 > 내장 커버(미리보기 전용) > 음표 플레이스홀더. */
@Composable
private fun ArtworkPicker(
    state: UploadState,
    enabled: Boolean,
    onPickArtwork: () -> Unit,
    onClearArtwork: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onPickArtwork)
                .semantics { contentDescription = "앨범자켓 선택" },
        ) {
            val model: Any? = state.artwork?.uri ?: state.embeddedCover
            if (model == null) {
                Artwork(url = null, contentDescription = null, size = 96.dp, cornerRadius = 8.dp)
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("앨범자켓", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.artwork != null) "직접 고른 이미지" else "탭하여 이미지 선택 (선택 사항)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.artwork != null) {
            IconButton(onClick = onClearArtwork, enabled = enabled) {
                Icon(Icons.Rounded.Close, contentDescription = "자켓 제거")
            }
        }
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    // label 파라미터를 쓰면 placeholder가 포커스 때만 보이므로 라벨은 입력칸 위에 따로 둔다
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** accent(빨강)는 텍스트 색으로 쓰지 않으므로 본문 색으로 그린다. */
@Composable
private fun DialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { content() }
}

/** 오류는 아이콘과 텍스트를 함께 보여 색만으로 전달하지 않는다. */
@Composable
private fun ErrorList(errors: List<String>) {
    if (errors.isEmpty()) return
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        errors.forEach { message ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun UploadProgress(state: UploadState) {
    val processing = state.phase == UploadPhase.Processing
    val label = if (processing) "서버에서 처리 중..." else "업로드 중... ${state.progress}%"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (processing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "업로드 진행률"; stateDescription = label },
            )
        } else {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "업로드 진행률"; stateDescription = label },
            )
        }
    }
}

@Preview
@Composable
private fun UploadDialogPreview() {
    MusicAppTheme {
        UploadDialog(
            state = UploadState(phase = UploadPhase.Uploading, progress = 40),
            showCloseConfirm = false,
            onPickAudio = {}, onPickArtwork = {}, onClearArtwork = {},
            onTitleChange = {}, onArtistChange = {}, onAlbumChange = {},
            onSave = {}, onCancelUpload = {}, onCloseRequest = {},
            onConfirmContinue = {}, onConfirmClose = {},
        )
    }
}
