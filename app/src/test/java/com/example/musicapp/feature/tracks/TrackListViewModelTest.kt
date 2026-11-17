package com.example.musicapp.feature.tracks

import app.cash.turbine.test
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackPageResponse
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.ui.MessageBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TrackRepository>()
    private val messageBus = MessageBus()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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

    private fun page(vararg ids: String, hasNext: Boolean = false) =
        TrackPageResponse(ids.map { track(it) }, 0, 20, ids.size.toLong(), 1, hasNext)

    private fun createViewModel() = TrackListViewModel(repository, messageBus)

    @Test
    fun 초기_로딩이_성공하면_목록과_hasNext가_반영된다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns page("a", "b", hasNext = true)

        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.isLoading)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("a", "b"), state.tracks.map { it.trackId })
        assertTrue(state.hasNext)
        assertNull(state.error)
    }

    @Test
    fun 입력은_즉시_반영되고_조회는_디바운스_후_한번만_한다() = runTest(dispatcher) {
        coEvery { repository.getTracks(any(), any()) } returns page("a")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.updateKeyword("h")
            assertEquals("h", awaitItem().keyword)
            advanceTimeBy(200)
            viewModel.updateKeyword("he")
            assertEquals("he", awaitItem().keyword)
            advanceTimeBy(299)
            runCurrent()
            coVerify(exactly = 0) { repository.getTracks("he", any()) }
            advanceTimeBy(1)
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTracks("he", 0) }
        coVerify(exactly = 0) { repository.getTracks("h", any()) }
    }

    @Test
    fun 공백뿐인_입력은_keyword를_보내지_않고_200자를_넘으면_자른다() = runTest(dispatcher) {
        coEvery { repository.getTracks(any(), any()) } returns page("a")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateKeyword("x")
        advanceUntilIdle()
        viewModel.updateKeyword("   ")
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.getTracks(null, 0) }

        viewModel.updateKeyword("a".repeat(300))
        assertEquals(200, viewModel.state.value.keyword.length)
    }

    @Test
    fun 검색어가_바뀌면_page_0부터_다시_조회하고_결과_전까지_이전_목록을_유지한다() = runTest(dispatcher) {
        val deferred = CompletableDeferred<TrackPageResponse>()
        coEvery { repository.getTracks(null, 0) } returns page("old", hasNext = true)
        coEvery { repository.getTracks("new", 0) } coAnswers { deferred.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateKeyword("new")
        advanceTimeBy(301)
        runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertEquals(listOf("old"), viewModel.state.value.tracks.map { it.trackId })

        deferred.complete(page("n1", "n2"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("n1", "n2"), viewModel.state.value.tracks.map { it.trackId })
    }

    @Test
    fun 새_검색이_시작되면_진행_중인_이전_요청은_취소된다() = runTest(dispatcher) {
        var cancelled = false
        coEvery { repository.getTracks(null, 0) } returns page("a")
        coEvery { repository.getTracks("first", 0) } coAnswers {
            try {
                CompletableDeferred<TrackPageResponse>().await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
        }
        coEvery { repository.getTracks("second", 0) } returns page("s")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateKeyword("first")
        advanceTimeBy(301)
        runCurrent()
        viewModel.updateKeyword("second")
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(listOf("s"), viewModel.state.value.tracks.map { it.trackId })
    }

    @Test
    fun 늦게_도착한_이전_응답은_폐기된다() = runTest(dispatcher) {
        val late = CompletableDeferred<TrackPageResponse>()
        coEvery { repository.getTracks(null, 0) } returns page("a")
        // 취소에 협조하지 않는 요청을 흉내 낸다
        coEvery { repository.getTracks("first", 0) } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { late.await() }
        }
        coEvery { repository.getTracks("second", 0) } returns page("s")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateKeyword("first")
        advanceTimeBy(301)
        runCurrent()
        viewModel.updateKeyword("second")
        advanceUntilIdle()
        late.complete(page("late"))
        advanceUntilIdle()

        assertEquals(listOf("s"), viewModel.state.value.tracks.map { it.trackId })
    }

    @Test
    fun 다음_페이지는_이어_붙고_중복_trackId는_제외된다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns page("a", "b", hasNext = true)
        coEvery { repository.getTracks(null, 1) } returns page("b", "c", hasNext = false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("a", "b", "c"), state.tracks.map { it.trackId })
        assertFalse(state.hasNext)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun hasNext가_false면_다음_페이지를_요청하지_않는다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getTracks(any(), 1) }
    }

    @Test
    fun 다음_페이지_로딩_중_중복_호출은_무시된다() = runTest(dispatcher) {
        val deferred = CompletableDeferred<TrackPageResponse>()
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = true)
        coEvery { repository.getTracks(null, 1) } coAnswers { deferred.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        viewModel.loadNextPage()
        runCurrent()
        assertTrue(viewModel.state.value.isLoadingMore)
        deferred.complete(page("b"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTracks(null, 1) }
    }

    @Test
    fun 새로고침은_첫_페이지로_교체하고_로딩_중에는_이전_목록을_유지한다() = runTest(dispatcher) {
        val deferred = CompletableDeferred<TrackPageResponse>()
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = true)
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { repository.getTracks(null, 0) } coAnswers { deferred.await() }

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)
        assertEquals(listOf("a"), viewModel.state.value.tracks.map { it.trackId })

        deferred.complete(page("x", "y"))
        advanceUntilIdle()
        assertEquals(listOf("x", "y"), viewModel.state.value.tracks.map { it.trackId })
        assertFalse(viewModel.state.value.hasNext)
    }

    @Test
    fun 새로고침_후_다음_페이지는_page_1부터_다시_시작한다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = true)
        coEvery { repository.getTracks(null, 1) } returns page("b", hasNext = true)
        coEvery { repository.getTracks(null, 2) } returns page("c")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getTracks(null, 1) }
        coVerify(exactly = 0) { repository.getTracks(null, 2) }
    }

    @Test
    fun 첫_페이지_오류는_error를_설정하고_재시도하지_않는다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } throws ApiException(400, ErrorCode.INVALID_REQUEST)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(ErrorCode.INVALID_REQUEST, state.error?.code)
        coVerify(exactly = 1) { repository.getTracks(null, 0) }
        // 같은 id가 이미 발행되어 대기 중이므로 재발행은 거부된다 = 스낵바 1건이 예약되어 있다
        assertFalse(messageBus.tryReserve(ErrorCode.INVALID_REQUEST.name))
    }

    @Test
    fun 다음_페이지_오류는_목록을_보존하고_error를_설정하지_않는다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = true)
        coEvery { repository.getTracks(null, 1) } throws ApiException(null, ErrorCode.NETWORK_ERROR)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("a"), state.tracks.map { it.trackId })
        assertNull(state.error)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasNext)
        assertFalse(messageBus.tryReserve(ErrorCode.NETWORK_ERROR.name))
    }

    @Test
    fun updateTrack은_같은_id_행만_교체하고_없는_id는_무시한다() = runTest(dispatcher) {
        coEvery { repository.getTracks(null, 0) } returns TrackPageResponse(
            listOf(track("a", TrackStatus.PROCESSING), track("b")), 0, 20, 2, 1, false,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateTrack(track("a", TrackStatus.READY))
        viewModel.updateTrack(track("zzz"))

        val tracks = viewModel.state.value.tracks
        assertEquals(listOf("a", "b"), tracks.map { it.trackId })
        assertEquals(TrackStatus.READY, tracks[0].status)
    }

    @Test
    fun 검색_중_도착한_다음_페이지_응답은_폐기된다() = runTest(dispatcher) {
        val next = CompletableDeferred<TrackPageResponse>()
        coEvery { repository.getTracks(null, 0) } returns page("a", hasNext = true)
        coEvery { repository.getTracks(null, 1) } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { next.await() }
        }
        coEvery { repository.getTracks("q", 0) } returns page("q1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        runCurrent()
        viewModel.updateKeyword("q")
        advanceUntilIdle()
        next.complete(page("stale"))
        advanceUntilIdle()

        assertEquals(listOf("q1"), viewModel.state.value.tracks.map { it.trackId })
        assertFalse(viewModel.state.value.isLoadingMore)
        assertNotNull(viewModel.state.value)
    }
}
