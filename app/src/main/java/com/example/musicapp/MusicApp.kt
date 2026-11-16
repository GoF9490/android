package com.example.musicapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.example.musicapp.data.auth.TokenStore
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Hilt 의존성 그래프의 진입점. Coil 싱글턴 ImageLoader는 Hilt가 만든 것을 쓴다
@HiltAndroidApp
class MusicApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var tokenStore: Lazy<TokenStore>

    override fun onCreate() {
        super.onCreate()
        // 첫 API 요청 지연을 줄이기 위해 토큰 캐시를 백그라운드에서 미리 로드한다
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { tokenStore.get().awaitToken() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
