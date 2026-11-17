package com.example.musicapp.feature.upload

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** 음원 태그 추출 결과. 값은 trim 후 빈 문자열이면 null. [embeddedPicture]는 미리보기 전용이다. */
class AudioTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val embeddedPicture: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioTags && title == other.title && artist == other.artist && album == other.album &&
            embeddedPicture.contentEquals(other.embeddedPicture)

    override fun hashCode(): Int =
        listOf(title, artist, album, embeddedPicture?.contentHashCode()).hashCode()

    override fun toString(): String =
        "AudioTags(title=$title, artist=$artist, album=$album, picture=${embeddedPicture?.size ?: 0}B)"

    companion object {
        val EMPTY = AudioTags(null, null, null, null)
    }
}

interface AudioTagReader {
    /** 태그를 읽는다. 실패하면 [AudioTags.EMPTY]를 반환하며 예외를 던지지 않는다(취소 제외). */
    suspend fun readTags(uri: Uri): AudioTags
}

class MediaAudioTagReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioTagReader {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun readTags(uri: Uri): AudioTags = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            AudioTags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).cleanTag(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).cleanTag(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).cleanTag(),
                embeddedPicture = retriever.embeddedPicture?.takeIf { it.isNotEmpty() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AudioTags.EMPTY
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // release 실패는 무시한다
            }
        }
    }

    private fun String?.cleanTag(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
