package com.example.musicapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.musicapp.ui.MainScreen
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.theme.MusicAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var messageBus: MessageBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱은 다크 테마 고정이므로 시스템 설정과 무관하게 밝은 아이콘의 시스템 바를 쓴다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MusicAppTheme {
                MainScreen(messageBus = messageBus) { padding ->
                    // 본문 슬롯: 이후 Task에서 트랙 목록으로 채운다
                    Box(modifier = Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }
}
