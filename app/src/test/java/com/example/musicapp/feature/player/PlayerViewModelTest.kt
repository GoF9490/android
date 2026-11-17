package com.example.musicapp.feature.player

import app.cash.turbine.test
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.StreamPlayResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.player.PlaybackStatus
import com.example.musicapp.player.PlayerRepository
import com.example.musicapp.player.PlayerState
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.TRACK_FAILED_MESSAGE
import com.example.musicapp.ui.UiMessage
import com.example.musicapp.ui.getErrorMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** 가짜 구현. 상태를 직접 세팅하고 호출을 기록한다. */
private class FakePlayerRepository : PlayerRepository {
    val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState
    override val positionMs: StateFlow<Long> = MutableStateFlow(0L)

    val played = mutableListOf<StreamPlayResponse>()
    var toggleCount = 0

    override fun play(track: StreamPlayResponse) {
        played += track
    }

    override fun togglePlayPause() {
        toggleCount++
    }

    override fun seekTo(positionMs: Long) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun setRepeatOne(repeatOne: Boolean) = Unit
    override fun stop() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val trackRepository = mockk<TrackRepository>()
    private val player = FakePlayerRepository()
    private val messageBus = mockk<MessageBus>()
    private val messages = mutableListOf<UiMessage>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val captured = slot<UiMessage>()
        every { messageBus.post(capture(captured)) } answers {
            messages += captured.captured
            true
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: String, status: TrackStatus = TrackStatus.READY) = TrackResponse(
        trackId = id,
        title = "제목$id",
        artist = "가수",
        durationSeconds = 100,
        status = status,
        originalFilename = "$id.mp3",
        fileExtension = "mp3",
        fileSizeBytes = 1L,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun play(id: String) = StreamPlayResponse(id, "/stream/$id.m3u8", "제목$id", "가수", 100)

    private fun createViewModel() = PlayerViewModel(trackRepository, player, messageBus)

    private fun setPlayerState(trackId: String?, status: PlaybackStatus) {
        player.mutableState.value = PlayerState(
            currentTrack = trackId?.let { play(it) },
            isPlaying = status == PlaybackStatus.Playing,
            status = status,
        )
    }

    @Test
    fun READY_클릭은_play를_1회_호출하고_재생한다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } returns play("a")
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        coVerify(exactly = 1) { trackRepository.getPlayInfo("a") }
        assertEquals(listOf(play("a")), player.played)
    }

    @Test
    fun 같은_곡이_Playing_Paused면_play_없이_토글한다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        setPlayerState("a", PlaybackStatus.Playing)
        viewModel.onTrackClick(track("a"))
        setPlayerState("a", PlaybackStatus.Paused)
        viewModel.onTrackClick(track("a"))
        setPlayerState("a", PlaybackStatus.Playing)
        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        assertEquals(3, player.toggleCount)
        coVerify(exactly = 0) { trackRepository.getPlayInfo(any()) }
        assertEquals(emptyList<StreamPlayResponse>(), player.played)
    }

    @Test
    fun 시작_후_토글을_반복해도_play_호출은_총_1회다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } returns play("a")
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        repeat(4) { i ->
            setPlayerState("a", if (i % 2 == 0) PlaybackStatus.Playing else PlaybackStatus.Paused)
            viewModel.onTrackClick(track("a"))
        }

        coVerify(exactly = 1) { trackRepository.getPlayInfo(any()) }
        assertEquals(4, player.toggleCount)
    }

    @Test
    fun Ended_Error_상태의_같은_곡_재클릭은_play를_다시_호출한다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } returns play("a")
        val viewModel = createViewModel()

        setPlayerState("a", PlaybackStatus.Ended)
        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        setPlayerState("a", PlaybackStatus.Error)
        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        coVerify(exactly = 2) { trackRepository.getPlayInfo("a") }
        assertEquals(2, player.played.size)
        assertEquals(0, player.toggleCount)
    }

    @Test
    fun Loading_중_같은_곡_클릭은_무시한다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        setPlayerState("a", PlaybackStatus.Loading)
        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        coVerify(exactly = 0) { trackRepository.getPlayInfo(any()) }
        assertEquals(0, player.toggleCount)
        assertEquals(emptyList<StreamPlayResponse>(), player.played)
    }

    @Test
    fun 응답_대기_중_같은_곡_중복_클릭은_무시한다() = runTest(dispatcher) {
        val gate = CompletableDeferred<StreamPlayResponse>()
        coEvery { trackRepository.getPlayInfo("a") } coAnswers { gate.await() }
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        viewModel.onTrackClick(track("a"))
        gate.complete(play("a"))
        advanceUntilIdle()

        coVerify(exactly = 1) { trackRepository.getPlayInfo("a") }
        assertEquals(1, player.played.size)
    }

    @Test
    fun 다른_곡_클릭은_play를_호출하고_새_곡을_재생한다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("b") } returns play("b")
        val viewModel = createViewModel()

        setPlayerState("a", PlaybackStatus.Playing)
        viewModel.onTrackClick(track("b"))
        advanceUntilIdle()

        assertEquals(listOf(play("b")), player.played)
        assertEquals(0, player.toggleCount)
    }

    @Test
    fun 연속_클릭에서_먼저_보낸_응답이_늦게_와도_최신_곡만_재생된다() = runTest(dispatcher) {
        val gateA = CompletableDeferred<StreamPlayResponse>()
        val gateB = CompletableDeferred<StreamPlayResponse>()
        coEvery { trackRepository.getPlayInfo("a") } coAnswers { gateA.await() }
        coEvery { trackRepository.getPlayInfo("b") } coAnswers { gateB.await() }
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        viewModel.onTrackClick(track("b"))
        advanceUntilIdle()
        // 응답 순서를 뒤집는다: B가 먼저, A가 나중
        gateB.complete(play("b"))
        advanceUntilIdle()
        gateA.complete(play("a"))
        advanceUntilIdle()

        assertEquals(listOf(play("b")), player.played)
    }

    @Test
    fun 오래된_요청이_실패해도_최신_요청_상태를_건드리지_않는다() = runTest(dispatcher) {
        val gateA = CompletableDeferred<StreamPlayResponse>()
        coEvery { trackRepository.getPlayInfo("a") } coAnswers { gateA.await() }
        coEvery { trackRepository.getPlayInfo("b") } returns play("b")
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        viewModel.onTrackClick(track("b"))
        gateA.completeExceptionally(ApiException(500, ErrorCode.INTERNAL_ERROR))
        advanceUntilIdle()

        assertEquals(listOf(play("b")), player.played)
        assertEquals(emptyList<UiMessage>(), messages)
    }

    @Test
    fun PENDING_PROCESSING_클릭은_안내_스낵바만_띄운다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a", TrackStatus.PENDING))
        viewModel.onTrackClick(track("b", TrackStatus.PROCESSING))
        advanceUntilIdle()

        assertEquals(2, messages.size)
        messages.forEach { assertEquals(getErrorMessage(ErrorCode.TRACK_NOT_READY), it.text) }
        coVerify(exactly = 0) { trackRepository.getPlayInfo(any()) }
    }

    @Test
    fun FAILED_클릭은_변환_실패_스낵바만_띄운다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a", TrackStatus.FAILED))
        advanceUntilIdle()

        assertEquals(listOf("변환에 실패한 곡입니다"), messages.map { it.text })
        assertEquals(TRACK_FAILED_MESSAGE, messages.single().text)
        coVerify(exactly = 0) { trackRepository.getPlayInfo(any()) }
    }

    @Test
    fun UNKNOWN_클릭은_일반_오류_스낵바만_띄운다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a", TrackStatus.UNKNOWN))
        advanceUntilIdle()

        assertEquals(listOf(getErrorMessage(ErrorCode.INTERNAL_ERROR)), messages.map { it.text })
        coVerify(exactly = 0) { trackRepository.getPlayInfo(any()) }
    }

    @Test
    fun 상태가_바뀌어도_PENDING_FAILED는_재생중_곡_토글로_처리하지_않는다() = runTest(dispatcher) {
        val viewModel = createViewModel()

        setPlayerState("a", PlaybackStatus.Playing)
        viewModel.onTrackClick(track("a", TrackStatus.FAILED))

        assertEquals(0, player.toggleCount)
    }

    @Test
    fun 오류_409는_스낵바와_트랙_재조회_이벤트를_낸다() = runTest(dispatcher) {
        val fresh = track("a", TrackStatus.PROCESSING)
        coEvery { trackRepository.getPlayInfo("a") } throws ApiException(409, ErrorCode.TRACK_NOT_READY)
        coEvery { trackRepository.getTrack("a") } returns fresh
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onTrackClick(track("a"))
            advanceUntilIdle()
            val event = awaitItem() as PlayerEvent.TrackRefreshed
            assertSame(fresh, event.track)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(getErrorMessage(ErrorCode.TRACK_NOT_READY)), messages.map { it.text })
        assertEquals(emptyList<StreamPlayResponse>(), player.played)
    }

    @Test
    fun 오류_409_재조회가_실패해도_이벤트_없이_끝난다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } throws ApiException(409, ErrorCode.TRACK_NOT_READY)
        coEvery { trackRepository.getTrack("a") } throws ApiException(null, ErrorCode.NETWORK_ERROR)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onTrackClick(track("a"))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, messages.size)
    }

    @Test
    fun 오류_404는_스낵바와_목록_새로고침_이벤트를_낸다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } throws ApiException(404, ErrorCode.TRACK_NOT_FOUND)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onTrackClick(track("a"))
            advanceUntilIdle()
            assertEquals(PlayerEvent.ListRefreshRequested, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(getErrorMessage(ErrorCode.TRACK_NOT_FOUND)), messages.map { it.text })
    }

    @Test
    fun 네트워크_오류는_클라이언트_문구만_노출한다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } throws ApiException(null, ErrorCode.NETWORK_ERROR)
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        assertEquals(listOf(getErrorMessage(ErrorCode.NETWORK_ERROR)), messages.map { it.text })
        assertEquals(emptyList<StreamPlayResponse>(), player.played)
    }

    @Test
    fun 오류_뒤_같은_곡_재클릭으로_다시_시도할_수_있다() = runTest(dispatcher) {
        coEvery { trackRepository.getPlayInfo("a") } throws ApiException(null, ErrorCode.NETWORK_ERROR) andThen play("a")
        val viewModel = createViewModel()

        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()
        viewModel.onTrackClick(track("a"))
        advanceUntilIdle()

        coVerify(exactly = 2) { trackRepository.getPlayInfo("a") }
        assertEquals(listOf(play("a")), player.played)
    }

    @Test
    fun decidePlayAction_표() {
        val idle = PlayerState()
        fun state(id: String, status: PlaybackStatus) = PlayerState(currentTrack = play(id), status = status)

        assertEquals(PlayAction.Start, decidePlayAction(track("a"), idle, null))
        assertEquals(PlayAction.Start, decidePlayAction(track("b"), state("a", PlaybackStatus.Playing), null))
        assertEquals(PlayAction.Start, decidePlayAction(track("b"), state("a", PlaybackStatus.Loading), null))
        assertEquals(PlayAction.Toggle, decidePlayAction(track("a"), state("a", PlaybackStatus.Playing), null))
        assertEquals(PlayAction.Toggle, decidePlayAction(track("a"), state("a", PlaybackStatus.Paused), null))
        assertEquals(PlayAction.Ignore, decidePlayAction(track("a"), state("a", PlaybackStatus.Loading), null))
        assertEquals(PlayAction.Start, decidePlayAction(track("a"), state("a", PlaybackStatus.Ended), null))
        assertEquals(PlayAction.Start, decidePlayAction(track("a"), state("a", PlaybackStatus.Error), null))
        assertEquals(PlayAction.Ignore, decidePlayAction(track("a"), idle, "a"))
        assertEquals(PlayAction.Start, decidePlayAction(track("b"), idle, "a"))
    }
}
