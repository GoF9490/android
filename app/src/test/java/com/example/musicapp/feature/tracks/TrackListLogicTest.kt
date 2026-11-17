package com.example.musicapp.feature.tracks

import com.example.musicapp.data.api.model.TrackStatus
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class TrackListLogicTest {

    @Test
    fun `빈 목록이면 더 불러오지 않는다`() {
        assertFalse(shouldLoadMore(lastVisibleIndex = -1, totalCount = 0))
    }

    @Test
    fun `끝에서 5개 전 경계에서 불러온다`() {
        assertFalse(shouldLoadMore(lastVisibleIndex = 13, totalCount = 20))
        assertTrue(shouldLoadMore(lastVisibleIndex = 15, totalCount = 20))
        assertTrue(shouldLoadMore(lastVisibleIndex = 19, totalCount = 20))
    }

    @Test
    fun `항목이 임계값보다 적으면 보이는 즉시 불러온다`() {
        assertTrue(shouldLoadMore(lastVisibleIndex = 0, totalCount = 3))
    }

    @Test
    fun `빈 문구는 검색어 유무로 갈린다`() {
        assertEquals("업로드된 곡이 없습니다.", getEmptyMessage(""))
        assertEquals("업로드된 곡이 없습니다.", getEmptyMessage("   "))
        assertEquals("검색 결과가 없습니다.", getEmptyMessage("아이유"))
    }

    @Test
    fun `상태별 뱃지 문구`() {
        assertEquals("변환 중...", getStatusLabel(TrackStatus.PENDING))
        assertEquals("변환 중...", getStatusLabel(TrackStatus.PROCESSING))
        assertNull(getStatusLabel(TrackStatus.READY))
        assertEquals("변환 실패", getStatusLabel(TrackStatus.FAILED))
        assertEquals("변환 실패", getStatusLabel(TrackStatus.UNKNOWN))
    }
}
