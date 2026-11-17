package com.example.musicapp.feature.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.musicapp.data.api.model.UserResponse
import com.example.musicapp.ui.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val user = UserResponse(userId = 1, email = "test@example.com", profileImageUrl = null, role = "USER")

    @Test
    fun hidesItemsUntilAvatarClicked() {
        composeRule.setContent {
            MusicAppTheme { ProfileMenu(user = user, onUploadClick = {}, onLogoutClick = {}) }
        }

        composeRule.onNodeWithText("업로드").assertDoesNotExist()
        composeRule.onNodeWithText("로그아웃").assertDoesNotExist()
    }

    @Test
    fun showsUploadAndLogoutItemsWhenAvatarClicked() {
        composeRule.setContent {
            MusicAppTheme { ProfileMenu(user = user, onUploadClick = {}, onLogoutClick = {}) }
        }

        composeRule.onNodeWithContentDescription("프로필 메뉴").performClick()

        composeRule.onNodeWithText("업로드").assertExists()
        composeRule.onNodeWithText("로그아웃").assertExists()
    }

    @Test
    fun invokesOnlyMatchingCallbackAndClosesMenu() {
        var uploadCount = 0
        var logoutCount = 0
        composeRule.setContent {
            MusicAppTheme {
                ProfileMenu(user = user, onUploadClick = { uploadCount++ }, onLogoutClick = { logoutCount++ })
            }
        }

        composeRule.onNodeWithContentDescription("프로필 메뉴").performClick()
        composeRule.onNodeWithText("로그아웃").performClick()

        assertEquals(0, uploadCount)
        assertEquals(1, logoutCount)
        composeRule.onNodeWithText("로그아웃").assertDoesNotExist()
    }
}
