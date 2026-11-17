package com.example.musicapp.feature.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** SAF·Photo Picker로 고른 파일의 메타 정보. 크기·MIME은 알 수 없으면 null. */
data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
)

private const val FALLBACK_DISPLAY_NAME = "audio"

/** `OpenableColumns`와 `getType`으로 표시 이름·크기·MIME을 조회한다. 조회 실패는 기본값으로 대체한다. */
fun ContentResolver.querySelectedFile(uri: Uri): SelectedFile {
    var name: String? = null
    var size: Long? = null
    try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        // 조회 실패는 기본값으로 계속 진행한다(업로드를 막지 않음)
    }
    val mime = try {
        getType(uri)
    } catch (e: Exception) {
        null
    }
    return SelectedFile(
        uri = uri,
        displayName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: FALLBACK_DISPLAY_NAME,
        sizeBytes = size,
        mimeType = mime,
    )
}
