package com.example.musicapp.feature.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.musicapp.ui.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTitleAndGoogleButton() {
        composeRule.setContent { MusicAppTheme { LoginDialog(onDismiss = {}, onLoginClick = {}) } }

        composeRule.onNodeWithText("로그인").assertExists()
        composeRule.onNodeWithText("Sign with Google").assertExists()
    }

    @Test
    fun invokesLoginAndDismissOnceWhenButtonClicked() {
        var loginCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MusicAppTheme { LoginDialog(onDismiss = { dismissCount++ }, onLoginClick = { loginCount++ }) }
        }

        composeRule.onNodeWithText("Sign with Google").performClick()

        assertEquals(1, loginCount)
        assertEquals(1, dismissCount)
    }
}
