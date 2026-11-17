package com.example.musicapp.feature.tracks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.ui.theme.MusicAppTheme
import org.junit.Rule
import org.junit.Test

class StatusBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun showBadge(status: TrackStatus) {
        composeRule.setContent { MusicAppTheme { StatusBadge(status = status) } }
    }

    @Test
    fun showsConvertingLabelForPending() {
        showBadge(TrackStatus.PENDING)
        composeRule.onNodeWithText("변환 중...").assertExists()
    }

    @Test
    fun showsConvertingLabelForProcessing() {
        showBadge(TrackStatus.PROCESSING)
        composeRule.onNodeWithText("변환 중...").assertExists()
    }

    @Test
    fun showsFailedLabelForFailed() {
        showBadge(TrackStatus.FAILED)
        composeRule.onNodeWithText("변환 실패").assertExists()
    }

    @Test
    fun showsFailedLabelForUnknown() {
        showBadge(TrackStatus.UNKNOWN)
        composeRule.onNodeWithText("변환 실패").assertExists()
    }

    @Test
    fun showsNothingForReady() {
        showBadge(TrackStatus.READY)
        composeRule.onNodeWithText("변환 중...").assertDoesNotExist()
        composeRule.onNodeWithText("변환 실패").assertDoesNotExist()
    }
}
