package com.example.musicapp.feature.upload

import android.net.Uri
import app.cash.turbine.test
import com.example.musicapp.data.api.ApiException
import com.example.musicapp.data.api.ErrorCode
import com.example.musicapp.data.api.model.TrackResponse
import com.example.musicapp.data.api.model.TrackStatus
import com.example.musicapp.data.tracks.TrackRepository
import com.example.musicapp.ui.getErrorMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TrackRepository>()
    private val files = mutableMapOf<Uri, SelectedFile>()
    private val tagBehaviors = mutableMapOf<Uri, suspend () -> AudioTags>()

    private val tagReader = object : AudioTagReader {
        override suspend fun readTags(uri: Uri): AudioTags = tagBehaviors.getValue(uri).invoke()
    }
    private val resolver = object : SelectedFileResolver {
        override suspend fun resolve(uri: Uri): SelectedFile = files.getValue(uri)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = UploadViewModel(repository, tagReader, resolver)

    private fun audio(
        name: String = "song.mp3",
        size: Long? = 1000L,
        tags: suspend () -> AudioTags = { AudioTags.EMPTY },
    ): Uri {
        val uri = mockk<Uri>()
        files[uri] = SelectedFile(uri, name, size, "audio/mpeg")
        tagBehaviors[uri] = tags
        return uri
    }

    private fun image(mime: String? = "image/png", size: Long? = 100L): Uri {
        val uri = mockk<Uri>()
        files[uri] = SelectedFile(uri, "cover.png", size, mime)
        return uri
    }

    private fun track() = TrackResponse(
        trackId = "t1",
        title = "제목",
        artist = "가수",
        durationSeconds = 0,
        status = TrackStatus.PENDING,
        originalFilename = "song.mp3",
        fileExtension = "mp3",
        fileSizeBytes = 1L,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun stubUpload(block: suspend (onProgress: (Int) -> Unit) -> TrackResponse) {
        coEvery { repository.uploadTrack(any(), any(), any(), any(), any(), any()) } coAnswers {
            block(arg<(Int) -> Unit>(5))
        }
    }

    @Test
    fun 태그가_있으면_입력칸에_채우고_내장_커버를_노출한다() = runTest(dispatcher) {
        val cover = byteArrayOf(1, 2, 3)
        val vm = createViewModel()
        vm.onFileSelected(audio { AudioTags("노래", "가수", "앨범", cover) })
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("노래", s.title)
        assertEquals("가수", s.artist)
        assertEquals("앨범", s.album)
        assertArrayEquals(cover, s.embeddedCover)
        assertFalse(s.isReadingTags)
    }

    @Test
    fun 태그가_없으면_입력칸은_비우고_placeholder만_표시한다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio(name = "내 노래.wav"))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("", s.title)
        assertEquals("", s.artist)
        assertEquals("내 노래", s.titlePlaceholder)
        assertEquals("Unknown Artist", s.artistPlaceholder)
        assertTrue(s.canSave)
    }

    @Test
    fun 파일명_확장자는_마지막_점_기준으로_제거한다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio(name = "song.v2.mp3"))
        advanceUntilIdle()
        assertEquals("song.v2", vm.state.value.titlePlaceholder)

        vm.onFileSelected(audio(name = "noext"))
        advanceUntilIdle()
        assertEquals("noext", vm.state.value.titlePlaceholder)

        vm.onFileSelected(audio(name = ".hidden"))
        advanceUntilIdle()
        assertEquals(".hidden", vm.state.value.titlePlaceholder)
    }

    @Test
    fun 태그_추출이_5초를_넘으면_값_없음으로_처리하고_업로드할_수_있다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio { awaitCancellation() })
        runCurrent()
        assertTrue(vm.state.value.isReadingTags)

        advanceTimeBy(4_999)
        assertTrue(vm.state.value.isReadingTags)
        advanceTimeBy(2)
        runCurrent()

        val s = vm.state.value
        assertFalse(s.isReadingTags)
        assertEquals("", s.title)
        assertNull(s.embeddedCover)
        assertTrue(s.canSave)
    }

    @Test
    fun 태그_추출이_예외로_실패해도_값_없음으로_처리한다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio { throw IllegalStateException("실패") })
        advanceUntilIdle()

        assertFalse(vm.state.value.isReadingTags)
        assertEquals("", vm.state.value.title)
        assertTrue(vm.state.value.errors.isEmpty())
    }

    @Test
    fun 재선택하면_이전_파일의_늦은_태그_결과를_무시하고_칸과_내장_커버를_초기화한다() = runTest(dispatcher) {
        val slow = CompletableDeferred<AudioTags>()
        val vm = createViewModel()
        vm.onFileSelected(audio(name = "a.mp3") { slow.await() })
        runCurrent()
        vm.onFileSelected(audio(name = "b.mp3") { AudioTags("B제목", null, null, null) })
        advanceUntilIdle()
        slow.complete(AudioTags("A제목", "A가수", null, byteArrayOf(9)))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("B제목", s.title)
        assertEquals("", s.artist)
        assertNull(s.embeddedCover)
        assertEquals("b", s.titlePlaceholder)
    }

    @Test
    fun 재선택하면_내장_커버는_초기화되고_사용자_자켓은_유지된다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio { AudioTags(null, null, null, byteArrayOf(1)) })
        vm.onArtworkSelected(image())
        advanceUntilIdle()
        assertTrue(vm.state.value.embeddedCover != null)

        vm.onFileSelected(audio())
        advanceUntilIdle()

        assertNull(vm.state.value.embeddedCover)
        assertEquals("cover.png", vm.state.value.artwork?.displayName)
    }

    @Test
    fun 사용자가_수정한_칸은_늦게_도착한_태그로_덮어쓰지_않는다() = runTest(dispatcher) {
        val slow = CompletableDeferred<AudioTags>()
        val vm = createViewModel()
        vm.onFileSelected(audio { slow.await() })
        runCurrent()
        vm.updateTitle("내가 쓴 제목")
        vm.updateAlbum("") // 지운 칸도 수정으로 본다
        slow.complete(AudioTags("태그 제목", "태그 가수", "태그 앨범", null))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("내가 쓴 제목", s.title)
        assertEquals("태그 가수", s.artist)
        assertEquals("", s.album)
    }

    @Test
    fun 재선택해도_수정한_칸은_유지되고_수정하지_않은_칸은_새_태그로_바뀐다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio { AudioTags("A", "A가수", null, null) })
        advanceUntilIdle()
        vm.updateTitle("수정")

        vm.onFileSelected(audio { AudioTags("B", "B가수", null, null) })
        advanceUntilIdle()

        assertEquals("수정", vm.state.value.title)
        assertEquals("B가수", vm.state.value.artist)
    }

    @Test
    fun 음원이_100MB를_넘으면_오류를_표시하고_업로드하지_않는다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio(size = MAX_AUDIO_BYTES + 1))
        advanceUntilIdle()
        assertEquals(listOf(UploadValidationError.AudioTooLarge.message), vm.state.value.errors)

        vm.upload()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.uploadTrack(any(), any(), any(), any(), any(), any()) }
        assertEquals(UploadPhase.Editing, vm.state.value.phase)
    }

    @Test
    fun 자켓이_형식_오류면_설정되지_않고_오류_문구를_표시한다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio())
        vm.onArtworkSelected(image(mime = "image/gif"))
        advanceUntilIdle()

        assertNull(vm.state.value.artwork)
        assertEquals(listOf(UploadValidationError.ArtworkInvalidType.message), vm.state.value.errors)
    }

    @Test
    fun 글자_수가_초과되면_사전_검증_오류로_업로드하지_않는다() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.updateTitle("가".repeat(MAX_TITLE_LENGTH + 1))
        vm.upload()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.uploadTrack(any(), any(), any(), any(), any(), any()) }
        assertEquals(listOf(UploadValidationError.TitleTooLong.message), vm.state.value.errors)
        assertEquals(UploadPhase.Editing, vm.state.value.phase)
    }

    @Test
    fun 성공하면_진행률을_거쳐_Processing이_되고_완료_이벤트가_한_번_발생한다() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        stubUpload { onProgress ->
            onProgress(40)
            gate.await()
            track()
        }
        // 가수 칸은 비워 두어 null이 전달되는지(placeholder 미전송) 확인한다
        val vm = createViewModel()
        vm.onFileSelected(audio { AudioTags("노래", null, null, byteArrayOf(1)) })
        advanceUntilIdle()

        vm.events.test {
            vm.upload()
            runCurrent()
            assertEquals(UploadPhase.Uploading, vm.state.value.phase)
            assertEquals(40, vm.state.value.progress)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(UploadEvent.Completed, awaitItem())
            expectNoEvents()
        }
        assertEquals(UploadState(), vm.state.value)
        coVerify {
            repository.uploadTrack(match { it.displayName == "song.mp3" }, "노래", null, null, null, any())
        }
    }

    @Test
    fun 진행률이_100이_되면_응답_전에는_Processing이다() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        stubUpload { onProgress ->
            onProgress(100)
            gate.await()
            track()
        }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.upload()
        runCurrent()

        assertEquals(UploadPhase.Processing, vm.state.value.phase)
        assertEquals(100, vm.state.value.progress)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun 서버_오류면_Editing으로_돌아가고_문구를_표시하며_입력값을_유지한다() = runTest(dispatcher) {
        stubUpload { throw ApiException(413, ErrorCode.PAYLOAD_TOO_LARGE) }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.updateTitle("제목")
        vm.onArtworkSelected(image())
        advanceUntilIdle()

        vm.events.test {
            vm.upload()
            advanceUntilIdle()
            expectNoEvents()
        }
        val s = vm.state.value
        assertEquals(UploadPhase.Editing, s.phase)
        assertEquals(listOf(getErrorMessage(ErrorCode.PAYLOAD_TOO_LARGE)), s.errors)
        assertEquals("제목", s.title)
        assertEquals("cover.png", s.artwork?.displayName)
        assertEquals(0, s.progress)
    }

    @Test
    fun 네트워크_오류도_문구를_표시한다() = runTest(dispatcher) {
        stubUpload { throw ApiException(null, ErrorCode.NETWORK_ERROR) }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.upload()
        advanceUntilIdle()

        assertEquals(listOf(getErrorMessage(ErrorCode.NETWORK_ERROR)), vm.state.value.errors)
    }

    @Test
    fun 취소하면_Editing으로_복귀하고_입력값은_유지되며_늦은_진행률은_무시한다() = runTest(dispatcher) {
        var callback: ((Int) -> Unit)? = null
        stubUpload { onProgress ->
            callback = onProgress
            onProgress(10)
            awaitCancellation()
        }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.updateTitle("제목")

        vm.events.test {
            vm.upload()
            runCurrent()
            assertEquals(UploadPhase.Uploading, vm.state.value.phase)

            vm.cancelUpload()
            advanceUntilIdle()
            callback?.invoke(80)

            val s = vm.state.value
            assertEquals(UploadPhase.Editing, s.phase)
            assertEquals(0, s.progress)
            assertTrue(s.errors.isEmpty())
            assertEquals("제목", s.title)
            expectNoEvents()
        }
    }

    @Test
    fun 업로드_중에는_입력과_파일_변경과_중복_업로드를_무시한다() = runTest(dispatcher) {
        stubUpload { awaitCancellation() }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.updateTitle("원래")
        vm.upload()
        runCurrent()

        vm.updateTitle("변경")
        vm.onFileSelected(audio(name = "other.mp3"))
        vm.upload()
        advanceUntilIdle()

        assertEquals("원래", vm.state.value.title)
        assertEquals("song.mp3", vm.state.value.file?.displayName)
        coVerify(exactly = 1) { repository.uploadTrack(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun 초기화하면_초기_상태로_돌아가고_전송을_취소한다() = runTest(dispatcher) {
        stubUpload { awaitCancellation() }
        val vm = createViewModel()
        vm.onFileSelected(audio())
        advanceUntilIdle()
        vm.updateTitle("제목")
        vm.upload()
        runCurrent()

        vm.reset()
        advanceUntilIdle()

        assertEquals(UploadState(), vm.state.value)
        // 초기화 후에는 수정 표시도 사라져 새 태그가 채워진다
        vm.onFileSelected(audio { AudioTags("새 제목", null, null, null) })
        advanceUntilIdle()
        assertEquals("새 제목", vm.state.value.title)
    }
}
