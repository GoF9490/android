package com.example.musicapp.download

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import com.example.musicapp.BuildConfig
import com.example.musicapp.data.auth.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원본 파일을 시스템 [DownloadManager]에 맡겨 공용 Downloads 폴더에 저장한다(PRD §8).
 * 파일은 앱 메모리로 읽지 않으며, 토큰은 요청 헤더에만 쓰고 제목·설명·로그에 남기지 않는다.
 */
@Singleton
class TrackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {

    /** 다운로드를 등록하고 요청 id를 돌려준다. [filename]은 저장 파일명(없으면 trackId 기반 기본 이름). */
    suspend fun enqueue(trackId: String, filename: String?): Long {
        val token = tokenStore.awaitToken()
        val safeName = sanitizeFilename(filename, trackId)
        val request = DownloadManager.Request(buildDownloadUrl(BuildConfig.BASE_URL, trackId).toUri())
            .setTitle(safeName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        if (token != null) request.addRequestHeader("Authorization", "Bearer $token")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}
