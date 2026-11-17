package com.example.musicapp.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicapp.MainActivity

/**
 * 백그라운드 재생을 담당하는 서비스. ExoPlayer는 이 서비스만 소유하고,
 * UI는 MediaController(Task 5-2의 PlayerRepository)로만 제어한다.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 오디오 포커스 처리와 이어폰 분리(becoming noisy) 시 일시정지를 켠다.
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 알림 탭 시 MainActivity를 연다(딥링크·토큰은 담지 않는다).
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** 재생 중이면 포그라운드로 유지하고, 아니면 모든 플레이어를 멈추고 서비스를 종료한다. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // isPlaybackOngoing는 일시정지 뒤 10분간 true라서(Media3가 포그라운드를 유지) 쓰지 않고 직접 판단한다.
        val player = mediaSession?.player
        val isActive = player != null && player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (!isActive) {
            pauseAllPlayersAndStopSelf()
            // 앱 프로세스의 MediaController가 서비스에 바인딩되어 있으면 stopSelf만으로는 서비스와
            // 알림이 남는다. 재생 목록을 비워 알림과 포그라운드 상태를 내린다.
            player?.run {
                stop()
                clearMediaItems()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
