package com.example.musicapp.feature.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 업로드 다이얼로그와 [UploadViewModel]을 잇는 최상위 연결부.
 * ViewModel은 Activity 범위라 전송은 회전과 무관하게 이어지고, 닫을 때 [UploadViewModel.reset]으로 초기화한다.
 */
@Composable
fun UploadDialogHost(
    viewModel: UploadViewModel,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCloseConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UploadEvent.Completed -> {
                    showCloseConfirm = false
                    onCompleted()
                    onDismiss()
                }
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }
    val artworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onArtworkSelected(uri)
    }

    val handleClose = {
        if (state.phase == UploadPhase.Editing) {
            viewModel.reset()
            onDismiss()
        } else {
            showCloseConfirm = true
        }
    }

    UploadDialog(
        state = state,
        showCloseConfirm = showCloseConfirm && state.phase != UploadPhase.Editing,
        onPickAudio = { audioLauncher.launch(arrayOf("audio/*")) },
        onPickArtwork = {
            artworkLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onClearArtwork = viewModel::clearArtwork,
        onTitleChange = viewModel::updateTitle,
        onArtistChange = viewModel::updateArtist,
        onAlbumChange = viewModel::updateAlbum,
        onSave = viewModel::upload,
        onCancelUpload = viewModel::cancelUpload,
        onCloseRequest = handleClose,
        onConfirmContinue = { showCloseConfirm = false },
        onConfirmClose = {
            showCloseConfirm = false
            viewModel.cancelUpload()
            viewModel.reset()
            onDismiss()
        },
    )
}
