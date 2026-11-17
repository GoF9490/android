package com.example.musicapp.feature.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.ui.getErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

enum class UploadPhase { Editing, Uploading, Processing }

/** 업로드 다이얼로그 상태. [embeddedCover]는 미리보기 전용이며 서버로 보내지 않는다. */
data class UploadState(
    val phase: UploadPhase = UploadPhase.Editing,
    val file: SelectedFile? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** 파일 선택 전에는 빈 문자열. 값이 아니라 표시용이다. */
    val titlePlaceholder: String = "",
    val artistPlaceholder: String = UNKNOWN_ARTIST_PLACEHOLDER,
    val embeddedCover: ByteArray? = null,
    /** 사용자가 직접 고른 자켓. */
    val artwork: SelectedFile? = null,
    val isReadingTags: Boolean = false,
    /** 0..100 */
    val progress: Int = 0,
    /** 인라인 오류 문구(PRD §3.4). 입력값은 오류와 무관하게 유지된다. */
    val errors: List<String> = emptyList(),
) {
    val canSave: Boolean get() = file != null && phase == UploadPhase.Editing

    override fun equals(other: Any?): Boolean =
        other is UploadState && phase == other.phase && file == other.file && title == other.title &&
            artist == other.artist && album == other.album && titlePlaceholder == other.titlePlaceholder &&
            artistPlaceholder == other.artistPlaceholder && embeddedCover.contentEquals(other.embeddedCover) &&
            artwork == other.artwork && isReadingTags == other.isReadingTags && progress == other.progress &&
            errors == other.errors

    override fun hashCode(): Int = listOf(
        phase, file, title, artist, album, titlePlaceholder, artistPlaceholder,
        embeddedCover?.contentHashCode(), artwork, isReadingTags, progress, errors,
    ).hashCode()

    override fun toString(): String =
        "UploadState(phase=$phase, file=$file, progress=$progress, cover=${embeddedCover?.size ?: 0}B, errors=$errors)"
}

sealed interface UploadEvent {
    /** 202 수신. UI는 다이얼로그를 닫고 목록을 새로고침한다. */
    data object Completed : UploadEvent
}

const val UNKNOWN_ARTIST_PLACEHOLDER = "Unknown Artist"
const val TAG_READ_TIMEOUT_MILLIS = 5_000L

/**
 * 업로드 상태 관리(PRD §5). 전송은 viewModelScope에서 진행되어 화면 회전에 끊기지 않는다.
 * 파일을 다시 고르면 세대 번호를 올려 이전 파일의 늦은 태그 결과를 무시한다.
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val tagReader: AudioTagReader,
    private val fileResolver: SelectedFileResolver,
) : ViewModel() {

    private val mutableState = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = mutableState.asStateFlow()

    private val eventChannel = Channel<UploadEvent>(Channel.BUFFERED)
    val events: Flow<UploadEvent> = eventChannel.receiveAsFlow()

    private var titleEdited = false
    private var artistEdited = false
    private var albumEdited = false

    private var fileGeneration = 0
    private var uploadGeneration = 0
    private var fileJob: Job? = null
    private var artworkJob: Job? = null
    private var uploadJob: Job? = null

    fun onFileSelected(uri: Uri) {
        if (mutableState.value.phase != UploadPhase.Editing) return
        fileJob?.cancel()
        fileGeneration++
        val generation = fileGeneration
        fileJob = viewModelScope.launch {
            val file = fileResolver.resolve(uri)
            if (generation != fileGeneration) return@launch
            mutableState.update {
                it.copy(
                    file = file,
                    titlePlaceholder = removeExtension(file.displayName),
                    // 직접 수정하지 않은 칸만 초기화한다(PRD §5.2)
                    title = if (titleEdited) it.title else "",
                    artist = if (artistEdited) it.artist else "",
                    album = if (albumEdited) it.album else "",
                    embeddedCover = null,
                    isReadingTags = true,
                    errors = listOfNotNull(UploadValidator.validateAudio(file.sizeBytes)?.message),
                )
            }
            val tags = readTagsSafely(uri)
            if (generation != fileGeneration) return@launch
            mutableState.update {
                it.copy(
                    title = if (titleEdited) it.title else tags.title.orEmpty(),
                    artist = if (artistEdited) it.artist else tags.artist.orEmpty(),
                    album = if (albumEdited) it.album else tags.album.orEmpty(),
                    embeddedCover = tags.embeddedPicture,
                    isReadingTags = false,
                )
            }
        }
    }

    /** 실패·시간 초과는 "값 없음"과 같다. 취소는 그대로 전파한다. */
    private suspend fun readTagsSafely(uri: Uri): AudioTags {
        return try {
            withTimeoutOrNull(TAG_READ_TIMEOUT_MILLIS) { tagReader.readTags(uri) } ?: AudioTags.EMPTY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AudioTags.EMPTY
        }
    }

    fun onArtworkSelected(uri: Uri) {
        if (mutableState.value.phase != UploadPhase.Editing) return
        artworkJob?.cancel()
        artworkJob = viewModelScope.launch {
            val artwork = fileResolver.resolve(uri)
            val error = UploadValidator.validateArtwork(artwork.sizeBytes, artwork.mimeType)
            mutableState.update {
                if (error != null) it.copy(errors = listOf(error.message)) else it.copy(artwork = artwork, errors = emptyList())
            }
        }
    }

    fun clearArtwork() {
        if (mutableState.value.phase != UploadPhase.Editing) return
        artworkJob?.cancel()
        mutableState.update { it.copy(artwork = null) }
    }

    fun updateTitle(value: String) {
        if (mutableState.value.phase != UploadPhase.Editing) return
        titleEdited = true
        mutableState.update { it.copy(title = value) }
    }

    fun updateArtist(value: String) {
        if (mutableState.value.phase != UploadPhase.Editing) return
        artistEdited = true
        mutableState.update { it.copy(artist = value) }
    }

    fun updateAlbum(value: String) {
        if (mutableState.value.phase != UploadPhase.Editing) return
        albumEdited = true
        mutableState.update { it.copy(album = value) }
    }

    fun upload() {
        val current = mutableState.value
        val file = current.file
        if (file == null || current.phase != UploadPhase.Editing) return

        val validationErrors = UploadValidator.validate(
            audioSizeBytes = file.sizeBytes,
            title = current.title,
            artist = current.artist,
            album = current.album,
            artwork = current.artwork?.toArtworkInfo(),
        )
        if (validationErrors.isNotEmpty()) {
            mutableState.update { it.copy(errors = validationErrors.map { error -> error.message }) }
            return
        }

        // 태그를 아직 읽는 중이어도 막지 않는다. 늦은 결과가 전송 중 입력칸을 바꾸지 않게 차단한다.
        fileJob?.cancel()
        fileGeneration++
        uploadGeneration++
        val generation = uploadGeneration
        mutableState.update {
            it.copy(phase = UploadPhase.Uploading, progress = 0, errors = emptyList(), isReadingTags = false)
        }
        uploadJob = viewModelScope.launch {
            try {
                // placeholder·내장 커버는 전달하지 않는다. 빈 값은 null로 넘겨 파트를 생략한다.
                repository.uploadTrack(
                    file = file,
                    title = current.title.takeIf { it.isNotBlank() },
                    artist = current.artist.takeIf { it.isNotBlank() },
                    album = current.album.takeIf { it.isNotBlank() },
                    artwork = current.artwork,
                    onProgress = { percent -> handleProgress(generation, percent) },
                )
                if (generation != uploadGeneration) return@launch
                resetInputFlags()
                mutableState.value = UploadState()
                eventChannel.trySend(UploadEvent.Completed)
            } catch (e: ApiException) {
                if (generation != uploadGeneration) return@launch
                mutableState.update {
                    it.copy(phase = UploadPhase.Editing, progress = 0, errors = listOf(getErrorMessage(e.code)))
                }
            }
        }
    }

    /** OkHttp 쓰기 스레드에서 호출된다. 취소·재시작된 업로드의 늦은 콜백은 버린다. */
    private fun handleProgress(generation: Int, percent: Int) {
        if (generation != uploadGeneration) return
        mutableState.update {
            if (it.phase == UploadPhase.Editing || percent <= it.progress) {
                it
            } else {
                it.copy(
                    progress = percent.coerceAtMost(100),
                    phase = if (percent >= 100) UploadPhase.Processing else UploadPhase.Uploading,
                )
            }
        }
    }

    /** 전송만 중단하고 입력값은 유지한 채 `Editing`으로 돌아간다. */
    fun cancelUpload() {
        if (mutableState.value.phase == UploadPhase.Editing) return
        uploadGeneration++
        uploadJob?.cancel()
        mutableState.update { it.copy(phase = UploadPhase.Editing, progress = 0, errors = emptyList()) }
    }

    /** 다이얼로그가 닫힐 때 상태를 초기화한다. 진행 중인 작업은 모두 취소한다. */
    fun reset() {
        fileJob?.cancel()
        artworkJob?.cancel()
        uploadJob?.cancel()
        fileGeneration++
        uploadGeneration++
        resetInputFlags()
        mutableState.value = UploadState()
    }

    private fun resetInputFlags() {
        titleEdited = false
        artistEdited = false
        albumEdited = false
    }

    /** 마지막 `.` 뒤를 확장자로 본다. `.`이 없거나 맨 앞이면 전체를 쓴다. */
    private fun removeExtension(name: String): String {
        val index = name.lastIndexOf('.')
        return if (index > 0) name.substring(0, index) else name
    }
}
