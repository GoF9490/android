package com.example.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.musicapp.data.player.NotificationPermissionStore
import com.example.musicapp.data.player.shouldRequestNotificationPermission
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.LaunchedEffect
import com.example.musicapp.feature.player.ExpandedPlayerHost
import com.example.musicapp.feature.player.MiniPlayerHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.feature.auth.AuthAction
import com.example.musicapp.feature.auth.AuthViewModel
import com.example.musicapp.feature.auth.launchLogin
import com.example.musicapp.feature.auth.DeepLinkResult
import com.example.musicapp.feature.auth.parseDeepLink
import com.example.musicapp.ui.MessageKind
import com.example.musicapp.ui.UiMessage
import com.example.musicapp.ui.getLoginErrorMessage
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicapp.download.TrackDownloader
import com.example.musicapp.download.needsStoragePermission
import com.example.musicapp.feature.player.PlayerEvent
import com.example.musicapp.feature.player.PlayerViewModel
import com.example.musicapp.feature.tracks.SearchBar
import com.example.musicapp.feature.upload.UploadDialogHost
import com.example.musicapp.feature.upload.UploadViewModel
import com.example.musicapp.data.auth.AuthState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.musicapp.feature.tracks.TrackList
import com.example.musicapp.feature.tracks.TrackListViewModel
import com.example.musicapp.ui.MainScreen
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.theme.MusicAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var messageBus: MessageBus

    @Inject
    lateinit var trackDownloader: TrackDownloader

    @Inject
    lateinit var notificationPermissionStore: NotificationPermissionStore

    // 결과는 사용하지 않는다(거부해도 재생은 동작하며 알림 컨트롤만 제한된다)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // API 28 이하에서 저장 권한 허용 후 이어서 실행할 다운로드 요청(trackId to 파일명)
    private var pendingDownload: Pair<String, String?>? = null

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingDownload
            pendingDownload = null
            if (pending != null) {
                if (granted) startDownload(pending.first, pending.second) else postDownloadFailed()
            }
        }

    private val authViewModel: AuthViewModel by viewModels()
    private val trackListViewModel: TrackListViewModel by viewModels()
    private val uploadViewModel: UploadViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

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
        // 프로세스 재생성 시에는 이미 처리한 딥링크일 수 있으므로 처음 생성될 때만 처리한다
        if (savedInstanceState == null) handleDeepLink(intent)
        setContent {
            MusicAppTheme {
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val trackListState by trackListViewModel.state.collectAsStateWithLifecycle()
                val expiredIds by trackListViewModel.poller.expiredIds.collectAsStateWithLifecycle()
                val statusScope = rememberCoroutineScope()
                // 현재 곡 id만 구독해 재생/일시정지·위치 변화로는 목록이 다시 그려지지 않게 한다
                val currentTrackId by playerViewModel.currentTrackId.collectAsStateWithLifecycle()
                var showUpload by rememberSaveable { mutableStateOf(false) }
                // 확장 플레이어 열림 상태. 회전·재생성에도 유지하고, 곡이 없어지면 닫는다
                var showExpanded by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(currentTrackId) {
                    if (currentTrackId == null) showExpanded = false
                }
                // 로그인 상태에서만 업로드 다이얼로그를 보인다. 로그아웃되면 상태를 초기화하고 닫는다
                val canUpload = authState is AuthState.LoggedIn
                LaunchedEffect(canUpload) {
                    if (!canUpload && authState !is AuthState.Loading) {
                        showUpload = false
                        uploadViewModel.reset()
                    }
                }
                // 변환 상태 폴링: 앱이 STARTED일 때만 돌고 백그라운드에서는 취소된다(PRD §6.3)
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        trackListViewModel.poller.run()
                    }
                }
                // 재생 시작 오류에 따른 목록 갱신을 중계한다(PRD §7.1)
                LaunchedEffect(Unit) {
                    playerViewModel.events.collect { event ->
                        when (event) {
                            is PlayerEvent.TrackRefreshed -> trackListViewModel.updateTrack(event.track)
                            PlayerEvent.ListRefreshRequested -> trackListViewModel.refresh()
                            PlayerEvent.RequestNotificationPermission -> requestNotificationPermissionOnce()
                        }
                    }
                }
                MainScreen(
                    messageBus = messageBus,
                    topBar = {
                        SearchBar(
                            keyword = trackListState.keyword,
                            onKeywordChange = trackListViewModel::updateKeyword,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        AuthAction(
                            authState = authState,
                            onLoginClick = ::handleLoginClick,
                            onUploadClick = { showUpload = true },
                            onLogoutClick = authViewModel::handleLogout,
                        )
                    },
                    // 곡을 고르기 전에는 슬롯 자체를 두지 않는다
                    playerBar = if (currentTrackId != null) {
                        {
                            MiniPlayerHost(
                                viewModel = playerViewModel,
                                baseUrl = BuildConfig.BASE_URL,
                                onOpenClick = { showExpanded = true },
                            )
                        }
                    } else {
                        null
                    },
                ) { padding ->
                    if (showExpanded && currentTrackId != null) {
                        ExpandedPlayerHost(
                            viewModel = playerViewModel,
                            baseUrl = BuildConfig.BASE_URL,
                            onDownloadClick = { play ->
                                // 확장 플레이어는 파일명을 모르므로 목록 상태에서 trackId로 찾는다(없으면 기본 이름)
                                val name = trackListViewModel.state.value.tracks
                                    .firstOrNull { it.trackId == play.trackId }?.originalFilename
                                handleDownloadClick(play.trackId, name)
                            },
                            onCloseClick = { showExpanded = false },
                        )
                    }
                    if (showUpload && canUpload) {
                        UploadDialogHost(
                            viewModel = uploadViewModel,
                            onDismiss = { showUpload = false },
                            onCompleted = trackListViewModel::refresh,
                        )
                    }
                    // 플레이어 바가 없으면 Scaffold 하단 패딩이 0이므로 내비게이션 바 높이로 보정한다
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val listPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = maxOf(padding.calculateBottomPadding(), navBottom),
                    )
                    TrackList(
                        state = trackListState,
                        baseUrl = BuildConfig.BASE_URL,
                        onTrackClick = playerViewModel::onTrackClick,
                        onDownloadClick = { track -> handleDownloadClick(track.trackId, track.originalFilename) },
                        onLoadMore = trackListViewModel::loadNextPage,
                        onRefresh = trackListViewModel::refresh,
                        contentPadding = listPadding,
                        currentTrackId = currentTrackId,
                        expiredIds = expiredIds,
                        onVisibleIdsChange = trackListViewModel.poller::updateVisibleIds,
                        onStatusRefreshClick = { id -> statusScope.launch { trackListViewModel.poller.refreshOne(id) } },
                    )
                }
            }
        }
    }

    /**
     * 첫 재생 시점에 알림 권한을 한 번만 요청한다(PRD §7.3). 시스템 다이얼로그만 쓰고,
     * 결과(거부 포함)와 무관하게 재생은 진행하므로 콜백에서 할 일이 없다.
     */
    private suspend fun requestNotificationPermissionOnce() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        // 이미 허용·API 33 미만이면 저장소를 읽지 않는다
        if (!shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted, alreadyAsked = false)) return
        if (!shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted, notificationPermissionStore.hasAsked())) return
        notificationPermissionStore.markAsked()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 다운로드 버튼 처리. API 28 이하에서는 저장 권한을 먼저 확인한다(PRD §8). */
    private fun handleDownloadClick(trackId: String, filename: String?) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (needsStoragePermission(Build.VERSION.SDK_INT, granted)) {
            pendingDownload = trackId to filename
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        startDownload(trackId, filename)
    }

    private fun startDownload(trackId: String, filename: String?) {
        lifecycleScope.launch {
            try {
                trackDownloader.enqueue(trackId, filename)
                messageBus.post(UiMessage("download-start", "다운로드를 시작합니다", MessageKind.INFO))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postDownloadFailed()
            }
        }
    }

    private fun postDownloadFailed() {
        messageBus.post(UiMessage("download-failed", "다운로드를 시작하지 못했습니다", MessageKind.ERROR))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Activity 컨텍스트로 Custom Tab을 열어 콜백이 같은 태스크의 singleTop MainActivity로 돌아오게 한다. */
    private fun handleLoginClick() {
        launchLogin(this, BuildConfig.BASE_URL) {
            messageBus.post(UiMessage("login-no-browser", getLoginErrorMessage(null), MessageKind.ERROR))
        }
    }

    /** 로그인 콜백 딥링크를 처리한다. 토큰·URI는 로그에 남기지 않는다(PRD §4.2). */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val result = parseDeepLink(
            scheme = uri.scheme,
            host = uri.host,
            path = uri.path,
            token = uri.getQueryParameter("token"),
            error = uri.getQueryParameter("error"),
        )
        // 값을 꺼낸 직후 data를 비워 회전·재생성 시 재처리를 막는다
        intent.data = null
        when (result) {
            is DeepLinkResult.Token -> authViewModel.handleLogin(result.token)
            is DeepLinkResult.Error ->
                messageBus.post(UiMessage("login-error", getLoginErrorMessage(result.code), MessageKind.ERROR))
            DeepLinkResult.None -> Unit
        }
    }
}
