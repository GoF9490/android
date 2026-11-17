package com.example.musicapp.feature.tracks

import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackStatusPollerTest {

    private fun track(id: String, status: TrackStatus) = TrackResponse(
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

    /** 목록 상태와 서버 응답을 흉내 내는 테스트용 환경. */
    private class Fixture(scope: TestScope, initial: List<TrackResponse>) {
        var tracks: List<TrackResponse> = initial
        val fetched = mutableListOf<String>()
        var notFoundCount = 0

        /** 호출 횟수별 응답. 비어 있으면 현재 목록의 값을 그대로 돌려준다. */
        val responses = ArrayDeque<() -> TrackResponse>()

        val poller = TrackStatusPoller(
            getTrack = { id ->
                fetched += id
                responses.removeFirstOrNull()?.invoke() ?: tracks.first { it.trackId == id }
            },
            getTracks = { tracks },
            onTrackUpdated = { updated -> tracks = tracks.map { if (it.trackId == updated.trackId) updated else it } },
            onTrackNotFound = { notFoundCount++ },
            elapsedRealtimeMillis = { scope.testScheduler.currentTime },
        )
    }

    private fun TestScope.fixture(vararg tracks: TrackResponse) =
        Fixture(this, tracks.toList()).also { fixture ->
            fixture.poller.updateVisibleIds(tracks.mapTo(HashSet()) { it.trackId })
        }

    @Test
    fun PROCESSING에서_READY가_되면_반영하고_더_이상_조회하지_않는다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        f.responses += { track("a", TrackStatus.PROCESSING) }
        f.responses += { track("a", TrackStatus.READY) }

        val job = launch { f.poller.run() }
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(TrackStatus.READY, f.tracks.first().status)
        assertEquals(2, f.fetched.size)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, f.fetched.size)
        job.cancel()
    }

    @Test
    fun FAILED가_되면_중단한다() = runTest {
        val f = fixture(track("a", TrackStatus.PENDING))
        f.responses += { track("a", TrackStatus.FAILED) }

        val job = launch { f.poller.run() }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, f.fetched.size)
        assertEquals(TrackStatus.FAILED, f.tracks.first().status)
        job.cancel()
    }

    @Test
    fun 조회는_5초_간격으로_한다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))

        val job = launch { f.poller.run() }
        runCurrent()
        assertEquals(1, f.fetched.size)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, f.fetched.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, f.fetched.size)
        job.cancel()
    }

    @Test
    fun 시작_후_30분이_지나면_조회를_멈추고_만료_목록에_넣는다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))

        val job = launch { f.poller.run() }
        runCurrent()
        // 30분 직전 틱(1795초)까지는 계속 조회한다
        advanceTimeBy(1_795_000)
        runCurrent()
        assertEquals(360, f.fetched.size)
        assertTrue(f.poller.expiredIds.value.isEmpty())

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(360, f.fetched.size)
        assertEquals(setOf("a"), f.poller.expiredIds.value)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(360, f.fetched.size)
        job.cancel()
    }

    @Test
    fun 상태_새로고침은_한_번_조회하고_변환_중이면_폴링을_재개한다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        val job = launch { f.poller.run() }
        advanceTimeBy(1_800_000)
        runCurrent()
        val before = f.fetched.size
        assertEquals(setOf("a"), f.poller.expiredIds.value)

        f.poller.refreshOne("a")
        assertEquals(before + 1, f.fetched.size)
        assertTrue(f.poller.expiredIds.value.isEmpty())

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(before + 2, f.fetched.size)
        job.cancel()
    }

    @Test
    fun 상태_새로고침으로_READY가_되면_폴링을_재개하지_않는다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        val job = launch { f.poller.run() }
        advanceTimeBy(1_800_000)
        runCurrent()
        val before = f.fetched.size

        f.responses += { track("a", TrackStatus.READY) }
        f.poller.refreshOne("a")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(before + 1, f.fetched.size)
        assertTrue(f.poller.expiredIds.value.isEmpty())
        job.cancel()
    }

    @Test
    fun 조회가_404면_콜백을_한_번_호출하고_그_행은_더_조회하지_않는다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        f.responses += { throw ApiException(404, ErrorCode.TRACK_NOT_FOUND) }

        val job = launch { f.poller.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, f.fetched.size)
        assertEquals(1, f.notFoundCount)
        job.cancel()
    }

    @Test
    fun 서버_오류와_네트워크_오류_뒤에도_다음_틱에서_계속_조회한다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        f.responses += { throw ApiException(503, ErrorCode.INTERNAL_ERROR) }
        f.responses += { throw ApiException(null, ErrorCode.NETWORK_ERROR) }
        f.responses += { track("a", TrackStatus.READY) }

        val job = launch { f.poller.run() }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, f.fetched.size)
        assertEquals(TrackStatus.READY, f.tracks.first().status)
        assertEquals(0, f.notFoundCount)
        job.cancel()
    }

    @Test
    fun 화면에서_사라진_행은_조회하지_않고_다시_보이면_재개한다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        val job = launch { f.poller.run() }
        runCurrent()
        assertEquals(1, f.fetched.size)

        f.poller.updateVisibleIds(emptySet())
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, f.fetched.size)

        f.poller.updateVisibleIds(setOf("a"))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(f.fetched.size >= 2)
        job.cancel()
    }

    @Test
    fun READY_행과_UNKNOWN_행은_조회_대상이_아니다() = runTest {
        val f = fixture(track("a", TrackStatus.READY), track("b", TrackStatus.UNKNOWN), track("c", TrackStatus.PENDING))
        val job = launch { f.poller.run() }
        runCurrent()
        assertEquals(listOf("c"), f.fetched)
        job.cancel()
    }

    @Test
    fun 여러_행은_한_틱에_순차로_조회한다() = runTest {
        val f = fixture(track("a", TrackStatus.PENDING), track("b", TrackStatus.PROCESSING))
        val job = launch { f.poller.run() }
        runCurrent()
        assertEquals(listOf("a", "b"), f.fetched)
        job.cancel()
    }

    @Test
    fun 취소_예외는_삼키지_않고_전파한다() = runTest {
        val f = fixture(track("a", TrackStatus.PROCESSING))
        f.responses += { throw CancellationException("취소") }

        var cancelled = false
        val job = launch {
            try {
                f.poller.run()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        runCurrent()
        assertTrue(cancelled)
        assertFalse(job.isActive)
    }
}
