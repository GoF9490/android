package com.example.musicapp.feature.auth

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.api.model.UserResponse
import com.example.musicapp.data.auth.AuthState
import com.example.musicapp.ui.theme.MusicAppTheme

/**
 * 상단바 우측 슬롯. 비로그인이면 `로그인` 버튼(+[LoginDialog]), 로그인이면 [ProfileMenu]를 보여 준다.
 * [AuthState.Loading] 중에는 아무것도 그리지 않는다.
 */
@Composable
fun AuthAction(
    authState: AuthState,
    onLoginClick: () -> Unit,
    onUploadClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    when (authState) {
        AuthState.Loading -> Unit
        AuthState.LoggedOut -> {
            Button(
                onClick = { showDialog = true },
                modifier = modifier.heightIn(min = 48.dp),
            ) {
                Text("로그인")
            }
            if (showDialog) {
                LoginDialog(onDismiss = { showDialog = false }, onLoginClick = onLoginClick)
            }
        }
        is AuthState.LoggedIn -> ProfileMenu(
            user = authState.user,
            onUploadClick = onUploadClick,
            onLogoutClick = onLogoutClick,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun AuthActionLoggedOutPreview() {
    MusicAppTheme {
        AuthAction(AuthState.LoggedOut, onLoginClick = {}, onUploadClick = {}, onLogoutClick = {})
    }
}

@Preview
@Composable
private fun AuthActionLoggedInPreview() {
    MusicAppTheme {
        AuthAction(
            AuthState.LoggedIn(UserResponse(userId = 1, email = "a@b.c", profileImageUrl = null, role = "USER")),
            onLoginClick = {},
            onUploadClick = {},
            onLogoutClick = {},
        )
    }
}
