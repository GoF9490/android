package com.example.musicapp.feature.tracks

import com.example.musicapp.data.api.model.TrackStatus

/** 끝에서 [threshold]개 전 항목이 보이면 다음 페이지를 불러온다(PRD §6.1). */
fun shouldLoadMore(lastVisibleIndex: Int, totalCount: Int, threshold: Int = 5): Boolean =
    totalCount > 0 && lastVisibleIndex >= totalCount - threshold

/** 빈 목록 문구(웹과 동일). 검색어가 공백뿐이면 검색 중이 아닌 것으로 본다. */
fun getEmptyMessage(keyword: String): String =
    if (keyword.trim().isNotEmpty()) "검색 결과가 없습니다." else "업로드된 곡이 없습니다."

/** 상태 뱃지 문구. READY는 뱃지가 없으므로 null. UNKNOWN은 일반 오류 취급(PRD §16 #26). */
fun getStatusLabel(status: TrackStatus): String? = when (status) {
    TrackStatus.PENDING, TrackStatus.PROCESSING -> "변환 중..."
    TrackStatus.FAILED, TrackStatus.UNKNOWN -> "변환 실패"
    TrackStatus.READY -> null
}
