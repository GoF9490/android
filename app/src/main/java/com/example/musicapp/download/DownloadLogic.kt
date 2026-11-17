package com.example.musicapp.download

import com.example.musicapp.util.resolveMediaUrl

/** 원본 다운로드 URL을 만든다. */
fun buildDownloadUrl(baseUrl: String, trackId: String): String =
    resolveMediaUrl(baseUrl, "/api/tracks/$trackId/download")

/**
 * 저장 파일명으로 쓸 수 없는 문자(경로 구분자·제어문자)를 `_`로 바꾼다.
 * 결과가 비었거나 점으로만 이루어지면 trackId 기반 기본 이름을 쓴다.
 */
fun sanitizeFilename(name: String?, trackId: String): String {
    val cleaned = (name ?: "")
        .map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }
        .joinToString("")
        .trim()
    if (cleaned.isEmpty() || cleaned.all { it == '.' }) return "track-$trackId"
    return cleaned
}

/** API 28 이하에서 저장 권한이 아직 없으면 요청이 필요하다. */
fun needsStoragePermission(sdkInt: Int, granted: Boolean): Boolean = sdkInt <= 28 && !granted
