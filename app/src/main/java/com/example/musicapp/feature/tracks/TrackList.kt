package com.example.musicapp.feature.tracks

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.ui.getErrorMessage
import com.example.musicapp.util.resolveMediaUrl
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val SKELETON_COUNT = 8

/**
 * 트랙 목록. 상태와 콜백만 받는다(ViewModel 연결은 호출부).
 * [contentPadding]에는 Scaffold 패딩(상단바·하단 플레이어 높이 포함)을 넘긴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackList(
    state: TrackListState,
    baseUrl: String,
    onTrackClick: (TrackResponse) -> Unit,
    onDownloadClick: (TrackResponse) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    expiredIds: Set<String> = emptySet(),
    onVisibleIdsChange: (Set<String>) -> Unit = {},
    onStatusRefreshClick: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentVisibleIdsChange by rememberUpdatedState(onVisibleIdsChange)

    // 화면에 보이는 행을 폴링 대상 판단용으로 알린다(key가 trackId)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNullTo(HashSet()) { it.key as? String } }
            .distinctUntilChanged()
            .collect { currentVisibleIdsChange(it) }
    }
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentCount by rememberUpdatedState(state.tracks.size)

    // 끝에서 5개 전에 도달하면 다음 페이지 요청(중복은 ViewModel 가드가 막는다)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .filter { shouldLoadMore(it, currentCount) }
            .collect { currentLoadMore() }
    }
    // 목록이 더 길어졌는데 아직 화면이 덜 찬 경우도 이어서 불러온다
    LaunchedEffect(state.tracks.size, state.isLoadingMore) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (!state.isLoadingMore && shouldLoadMore(last, state.tracks.size)) onLoadMore()
    }
    // 새 검색 결과가 오면 맨 위부터 보여 준다
    val firstTrackId = state.tracks.firstOrNull()?.trackId
    // 회전·재생성 때는 첫 곡이 같으므로 복원된 스크롤 위치를 유지한다
    var lastFirstTrackId by rememberSaveable { mutableStateOf(firstTrackId) }
    LaunchedEffect(firstTrackId) {
        if (firstTrackId != lastFirstTrackId) {
            lastFirstTrackId = firstTrackId
            listState.scrollToItem(0)
        }
    }

    val error = state.error

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.tracks.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            state.isLoading && state.tracks.isEmpty() -> SkeletonList(contentPadding)
            error != null && state.tracks.isEmpty() -> StateMessage(
                message = getErrorMessage(error.code),
                contentPadding = contentPadding,
                onRetry = onRefresh,
            )
            state.tracks.isEmpty() -> StateMessage(
                message = getEmptyMessage(state.keyword),
                contentPadding = contentPadding,
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(state.tracks, key = { it.trackId }) { track ->
                    TrackRow(
                        track = track,
                        artworkUrl = track.artworkUrl?.let { resolveMediaUrl(baseUrl, it) },
                        onClick = { onTrackClick(track) },
                        onDownloadClick = { onDownloadClick(track) },
                        showStatusRefresh = track.trackId in expiredIds,
                        onStatusRefreshClick = { onStatusRefreshClick(track.trackId) },
                    )
                }
                if (state.isLoadingMore) {
                    item(key = "loading-more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).semantics { contentDescription = "다음 곡 불러오는 중" },
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateMessage(
    message: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("다시 시도") }
        }
    }
}

@Composable
private fun SkeletonList(contentPadding: PaddingValues) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val color = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .semantics { contentDescription = "곡 목록 불러오는 중" },
    ) {
        repeat(SKELETON_COUNT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).background(color, RoundedCornerShape(4.dp)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(color, RoundedCornerShape(4.dp)))
                    Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(color, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
