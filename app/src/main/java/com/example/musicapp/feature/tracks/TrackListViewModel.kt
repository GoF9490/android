package com.example.musicapp.feature.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.MessageKind
import com.example.musicapp.ui.UiMessage
import com.example.musicapp.ui.getErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 목록 화면 상태. 조회에 쓰인 trim된 검색어는 ViewModel 내부에서만 보관한다. */
data class TrackListState(
    val tracks: List<TrackResponse> = emptyList(),
    /** 첫 페이지(검색·새로고침 포함) 조회 중. 이전 목록은 유지된다. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    /** 입력값 그대로(즉시 반영, 200자 제한). */
    val keyword: String = "",
    /** 첫 페이지 조회 오류. 성공하면 null. */
    val error: ApiException? = null,
)

/**
 * 목록 상태를 직접 관리한다(Paging3 미사용, PRD §6.1).
 * 첫 페이지 조회가 시작될 때마다 세대 번호를 올려, 취소 시점을 놓친 늦은 응답은 반영하지 않는다.
 */
@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val messageBus: MessageBus,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TrackListState())
    val state: StateFlow<TrackListState> = mutableState.asStateFlow()

    private val keywordInput = MutableStateFlow("")

    /** 마지막으로 조회에 쓴 검색어(trim 후, 빈 값이면 null). */
    private var activeKeyword: String? = null
    private var currentPage = 0
    private var generation = 0
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null

    /** 변환 상태 폴링(PRD §6.3). 구동은 화면이 `repeatOnLifecycle(STARTED)`에서 `run()`으로 한다. */
    val poller = TrackStatusPoller(
        getTrack = repository::getTrack,
        getTracks = { mutableState.value.tracks },
        onTrackUpdated = ::updateTrack,
        onTrackNotFound = ::handleTrackNotFound,
        elapsedRealtimeMillis = { System.nanoTime() / 1_000_000 },
    )

    init {
        observeKeyword()
        loadFirstPage()
    }

    @OptIn(FlowPreview::class)
    private fun observeKeyword() {
        viewModelScope.launch {
            keywordInput
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                // 이미 조회한 검색어와 같으면 다시 조회하지 않는다(초기 빈 값 포함)
                .filter { it != (activeKeyword ?: "") }
                .collect { trimmed ->
                    activeKeyword = trimmed.ifEmpty { null }
                    loadFirstPage()
                }
        }
    }

    /** 검색 입력. 상태에는 즉시 반영하고 조회는 디바운스 뒤에 한다. */
    fun updateKeyword(input: String) {
        val limited = input.take(MAX_KEYWORD_LENGTH)
        mutableState.update { it.copy(keyword = limited) }
        keywordInput.value = limited
    }

    fun refresh() {
        loadFirstPage()
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (!current.hasNext || current.isLoading || current.isLoadingMore || current.error != null) return

        val requestGeneration = generation
        val requestPage = currentPage + 1
        mutableState.update { it.copy(isLoadingMore = true) }
        nextPageJob = viewModelScope.launch {
            try {
                val result = repository.getTracks(activeKeyword, requestPage)
                if (requestGeneration != generation) return@launch
                currentPage = requestPage
                mutableState.update {
                    val known = it.tracks.mapTo(HashSet()) { track -> track.trackId }
                    it.copy(
                        tracks = it.tracks + result.content.filter { track -> track.trackId !in known },
                        isLoadingMore = false,
                        hasNext = result.hasNext,
                    )
                }
            } catch (e: ApiException) {
                if (requestGeneration != generation) return@launch
                // 목록은 보존하고 스낵바만 띄운다. 다음 스크롤에서 다시 시도할 수 있다.
                mutableState.update { it.copy(isLoadingMore = false) }
                postError(e)
            }
        }
    }

    /** 행 단위 갱신(폴링·업로드 반영용). 목록에 없는 트랙은 무시한다. */
    fun updateTrack(track: TrackResponse) {
        mutableState.update { state ->
            if (state.tracks.none { it.trackId == track.trackId }) {
                state
            } else {
                state.copy(tracks = state.tracks.map { if (it.trackId == track.trackId) track else it })
            }
        }
    }

    /** 폴링 중 404: 목록을 새로고침하고 안내 스낵바를 띄운다. */
    private fun handleTrackNotFound() {
        postError(ApiException(404, ErrorCode.TRACK_NOT_FOUND))
        refresh()
    }

    private fun loadFirstPage() {
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        generation++
        val requestGeneration = generation
        mutableState.update { it.copy(isLoading = true, isLoadingMore = false) }
        firstPageJob = viewModelScope.launch {
            try {
                val result = repository.getTracks(activeKeyword, 0)
                if (requestGeneration != generation) return@launch
                currentPage = 0
                mutableState.update {
                    it.copy(
                        tracks = result.content,
                        isLoading = false,
                        hasNext = result.hasNext,
                        error = null,
                    )
                }
            } catch (e: ApiException) {
                if (requestGeneration != generation) return@launch
                mutableState.update { it.copy(isLoading = false, error = e) }
                postError(e)
            }
        }
    }

    private fun postError(error: ApiException) {
        messageBus.post(
            UiMessage(
                id = error.code.name,
                text = getErrorMessage(error.code),
                kind = MessageKind.ERROR,
            ),
        )
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MAX_KEYWORD_LENGTH = 200
    }
}
