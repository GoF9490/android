package com.example.musicapp.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicapp.data.api.model.UserResponse
import com.example.musicapp.ui.theme.MusicAppTheme

/** 원형 아바타 버튼과 `업로드`·`로그아웃` 드롭다운 메뉴. */
@Composable
fun ProfileMenu(
    user: UserResponse,
    onUploadClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        // IconButton 기본 터치 영역이 48dp이므로 아바타는 32dp로 그린다
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "프로필 메뉴" },
        ) {
            ProfileAvatar(url = user.profileImageUrl)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("업로드") },
                onClick = {
                    expanded = false
                    onUploadClick()
                },
            )
            DropdownMenuItem(
                text = { Text("로그아웃") },
                onClick = {
                    expanded = false
                    onLogoutClick()
                },
            )
        }
    }
}

/** 프로필 이미지가 없거나 로딩에 실패하면 기본 사람 아이콘을 보여 준다. 버튼 쪽에서 설명을 제공하므로 장식으로 취급한다. */
@Composable
private fun ProfileAvatar(url: String?) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ProfileMenuPreview() {
    MusicAppTheme {
        ProfileMenu(
            user = UserResponse(userId = 1, email = "a@b.c", profileImageUrl = null, role = "USER"),
            onUploadClick = {},
            onLogoutClick = {},
        )
    }
}
