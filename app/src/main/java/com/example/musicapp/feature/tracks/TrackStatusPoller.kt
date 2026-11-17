package com.example.musicapp.feature.tracks

import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 화면에 보이는 변환 중(PENDING/PROCESSING) 행의 상태를 주기적으로 조회한다(PRD §6.3).
 *
 * - [run]은 단일 루프다. 한 틱에 대상 행을 순차 조회하고, 조회가 끝난 뒤 [intervalMillis]를 기다린다.
 *   따라서 요청이 겹치지 않아 재시도 인터셉터(최대 2회)와 합쳐져도 요청이 몰리지 않는다.
 * - 호출부는 `repeatOnLifecycle(STARTED)` 안에서 [run]을 실행해 백그라운드에서는 취소되게 한다.
 * - 30분 상한은 [elapsedRealtimeMillis] 기준 벽시계다. 행이 처음 대상이 된 시각부터 잰다.
 * - 5xx·네트워크 오류는 알리지 않고 다음 틱에서 다시 시도한다. 404는 [onTrackNotFound]를 한 번 호출한다.
 */
class TrackStatusPoller(
    private val getTrack: suspend (String) -> TrackResponse,
    private val getTracks: () -> List<TrackResponse>,
    private val onTrackUpdated: (TrackResponse) -> Unit,
    private val onTrackNotFound: () -> Unit,
    private val elapsedRealtimeMillis: () -> Long,
    private val intervalMillis: Long = POLL_INTERVAL_MILLIS,
    private val maxDurationMillis: Long = MAX_DURATION_MILLIS,
) {

    private val visibleIds = MutableStateFlow<Set<String>>(emptySet())
    private val mutableExpiredIds = MutableStateFlow<Set<String>>(emptySet())

    /** 30분 상한을 넘겨 자동 폴링이 멈춘 행. 이 행에는 `상태 새로고침` 버튼을 보여 준다. */
    val expiredIds: StateFlow<Set<String>> = mutableExpiredIds.asStateFlow()

    private val startedAt = HashMap<String, Long>()
    private val notFoundIds = HashSet<String>()

    /** 화면에 보이는 행의 trackId 집합을 갱신한다. */
    fun updateVisibleIds(ids: Set<String>) {
        visibleIds.value = ids
    }

    /** 취소될 때까지 폴링한다. 취소 예외는 그대로 전파된다. */
    suspend fun run() {
        while (true) {
            pollOnce()
            delay(intervalMillis)
        }
    }

    /** 한 틱. 대상 행을 순차 조회한다. */
    internal suspend fun pollOnce() {
        val now = elapsedRealtimeMillis()
        val visible = visibleIds.value
        val targets = getTracks().filter { it.trackId in visible && isConverting(it.status) }
        val targetIds = targets.mapTo(HashSet()) { it.trackId }

        // 보이지 않거나 더 이상 변환 중이 아닌 행의 추적 정보는 버린다
        startedAt.keys.retainAll(targetIds)
        notFoundIds.retainAll(targetIds)
        mutableExpiredIds.update { it intersect targetIds }

        for (track in targets) {
            val id = track.trackId
            if (id in notFoundIds || id in mutableExpiredIds.value) continue
            val start = startedAt.getOrPut(id) { now }
            if (now - start >= maxDurationMillis) {
                mutableExpiredIds.update { it + id }
                continue
            }
            fetchAndApply(id)
        }
    }

    /** 만료된 행을 한 번 조회하고, 아직 변환 중이면 폴링을 재개한다(30분 타이머 재시작). */
    suspend fun refreshOne(trackId: String) {
        val updated = fetchAndApply(trackId) ?: return
        if (isConverting(updated.status)) {
            mutableExpiredIds.update { it - trackId }
            startedAt[trackId] = elapsedRealtimeMillis()
        }
    }

    /** 조회해 반영한다. 오류(404 포함)면 null. */
    private suspend fun fetchAndApply(trackId: String): TrackResponse? {
        return try {
            getTrack(trackId).also(onTrackUpdated)
        } catch (e: ApiException) {
            if (e.code == ErrorCode.TRACK_NOT_FOUND) {
                notFoundIds += trackId
                onTrackNotFound()
            }
            // 그 밖의 오류는 조용히 넘기고 다음 틱에서 다시 시도한다
            null
        }
    }

    private fun isConverting(status: TrackStatus) =
        status == TrackStatus.PENDING || status == TrackStatus.PROCESSING

    companion object {
        const val POLL_INTERVAL_MILLIS = 5_000L
        const val MAX_DURATION_MILLIS = 30 * 60 * 1_000L
    }
}
