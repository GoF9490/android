package com.example.musicapp.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.musicapp.ui.theme.MusicAppTheme

/**
 * 앱 기본 레이아웃: 상단바 슬롯, 본문 슬롯, 하단 플레이어 슬롯, 스낵바 호스트.
 * 시스템 바 inset은 Scaffold와 상·하단 컨테이너가 처리한다(edge-to-edge).
 */
@Composable
fun MainScreen(
    messageBus: MessageBus,
    modifier: Modifier = Modifier,
    topBar: @Composable RowScope.() -> Unit = {},
    playerBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MainTopBar(topBar) },
        bottomBar = { playerBar?.let { PlayerBarContainer(it) } },
        snackbarHost = { MessageHost(messageBus) },
        content = content,
    )
}

@Composable
private fun MainTopBar(slot: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = slot,
        )
    }
}

@Composable
private fun PlayerBarContainer(slot: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            slot()
        }
    }
}

@Preview
@Composable
private fun MainScreenPreview() {
    MusicAppTheme {
        MainScreen(messageBus = MessageBus()) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding))
        }
    }
}
