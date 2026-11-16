package com.example.musicapp.feature.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.musicapp.ui.theme.MusicAppTheme

/** `"Sign with Google"` 버튼 하나를 가진 로그인 다이얼로그. 표시 여부는 호출 측이 관리한다. */
@Composable
fun LoginDialog(onDismiss: () -> Unit, onLoginClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("로그인") },
        text = {
            Button(
                onClick = {
                    onLoginClick()
                    onDismiss()
                },
            ) {
                Text("Sign with Google")
            }
        },
        confirmButton = {},
    )
}

@Preview
@Composable
private fun LoginDialogPreview() {
    MusicAppTheme { LoginDialog(onDismiss = {}, onLoginClick = {}) }
}
