package com.example.musicapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Hilt 의존성 그래프의 진입점. Coil 싱글턴 ImageLoader는 Hilt가 만든 것을 쓴다
@HiltAndroidApp
class MusicApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
