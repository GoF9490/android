package com.example.musicapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.musicapp.feature.auth.AuthViewModel
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

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면이 앞으로 돌아올 때 인증 재조회(콜드 스타트의 첫 ON_START는 Loading이라 무시됨)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) authViewModel.handleForeground()
            },
        )
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
