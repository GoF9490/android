package com.example.musicapp.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.ui.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val track = StreamPlayResponse(
        trackId = "t1",
        streamUrl = "/stream/t1/index.m3u8",
        title = "테스트 곡",
        artist = "테스트 가수",
        durationSeconds = 180,
        artworkUrl = null,
    )

    @Test
    fun showsTitleArtistAndPlayButtonWhenPaused() {
        composeRule.setContent {
            MusicAppTheme {
                MiniPlayerBar(
                    track = track, isPlaying = false, isLoading = false, baseUrl = "http://localhost",
                    positionMs = { 0L }, durationMs = 180_000L, onToggleClick = {}, onOpenClick = {},
                )
            }
        }

        composeRule.onNodeWithText("테스트 곡").assertExists()
        composeRule.onNodeWithText("테스트 가수").assertExists()
        composeRule.onNodeWithContentDescription("재생").assertExists()
        composeRule.onNodeWithContentDescription("일시정지").assertDoesNotExist()
    }

    @Test
    fun togglesBetweenPlayAndPause() {
        var toggleCount = 0
        composeRule.setContent {
            var isPlaying by remember { mutableStateOf(false) }
            MusicAppTheme {
                MiniPlayerBar(
                    track = track, isPlaying = isPlaying, isLoading = false, baseUrl = "http://localhost",
                    positionMs = { 0L }, durationMs = 180_000L,
                    onToggleClick = {
                        toggleCount++
                        isPlaying = !isPlaying
                    },
                    onOpenClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("재생").performClick()
        composeRule.onNodeWithContentDescription("일시정지").assertExists()
        composeRule.onNodeWithContentDescription("일시정지").performClick()
        composeRule.onNodeWithContentDescription("재생").assertExists()
        assertEquals(2, toggleCount)
    }

    @Test
    fun showsLoadingIndicatorInsteadOfToggleButton() {
        composeRule.setContent {
            MusicAppTheme {
                MiniPlayerBar(
                    track = track, isPlaying = false, isLoading = true, baseUrl = "http://localhost",
                    positionMs = { 0L }, durationMs = 180_000L, onToggleClick = {}, onOpenClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("재생 준비 중").assertExists()
        composeRule.onNodeWithContentDescription("재생").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("일시정지").assertDoesNotExist()
    }
}
