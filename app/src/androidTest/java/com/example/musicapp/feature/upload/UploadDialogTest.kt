package com.example.musicapp.feature.upload

import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.musicapp.ui.theme.MusicAppTheme
import org.junit.Rule
import org.junit.Test

class UploadDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val file = SelectedFile(
        uri = Uri.parse("content://test/song.mp3"),
        displayName = "song.mp3",
        sizeBytes = 1_000L,
        mimeType = "audio/mpeg",
    )

    private fun showDialog(state: UploadState) {
        composeRule.setContent {
            MusicAppTheme {
                UploadDialog(
                    state = state,
                    showCloseConfirm = false,
                    onPickAudio = {}, onPickArtwork = {}, onClearArtwork = {},
                    onTitleChange = {}, onArtistChange = {}, onAlbumChange = {},
                    onSave = {}, onCancelUpload = {}, onCloseRequest = {},
                    onConfirmContinue = {}, onConfirmClose = {},
                )
            }
        }
    }

    @Test
    fun showsFilledTagValues() {
        showDialog(UploadState(file = file, title = "태그 제목", artist = "태그 가수", album = "태그 앨범"))

        composeRule.onNodeWithText("태그 제목").assertExists()
        composeRule.onNodeWithText("태그 가수").assertExists()
        composeRule.onNodeWithText("태그 앨범").assertExists()
        composeRule.onNodeWithText("song.mp3").assertExists()
    }

    @Test
    fun showsPlaceholdersWhenFieldsEmpty() {
        showDialog(UploadState(file = file, titlePlaceholder = "placeholder-title"))

        composeRule.onNodeWithText("placeholder-title").assertExists()
        composeRule.onNodeWithText(UNKNOWN_ARTIST_PLACEHOLDER).assertExists()
    }

    @Test
    fun showsInlineErrors() {
        showDialog(UploadState(file = file, errors = listOf("제목은 255자 이하여야 합니다.")))

        composeRule.onNodeWithText("제목은 255자 이하여야 합니다.").assertExists()
    }

    @Test
    fun hidesErrorsWhenNone() {
        showDialog(UploadState(file = file))

        composeRule.onNodeWithText("제목은 255자 이하여야 합니다.").assertDoesNotExist()
    }

    @Test
    fun disablesSaveUntilFileSelected() {
        showDialog(UploadState())

        composeRule.onNodeWithText("선택된 파일 없음").assertExists()
        composeRule.onNodeWithText("저장").assertIsNotEnabled()
    }

    @Test
    fun enablesSaveWhenFileSelected() {
        showDialog(UploadState(file = file))

        composeRule.onNodeWithText("저장").assertIsEnabled()
    }

    @Test
    fun showsProgressAndLocksSaveWhileUploading() {
        showDialog(UploadState(phase = UploadPhase.Uploading, file = file, progress = 40))

        composeRule.onNodeWithText("업로드 중... 40%").assertExists()
        composeRule.onNodeWithText("전송 취소").assertExists()
        composeRule.onNodeWithText("저장").assertIsNotEnabled()
    }
}
