package com.example.musicapp.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekLogicTest {

    @Test
    fun 플레이어_길이가_있으면_그_값을_쓴다() {
        assertEquals(215_000L, resolveDurationMs(215_000L, 220))
    }

    @Test
    fun 플레이어_길이가_없거나_음수면_서버_길이를_쓴다() {
        assertEquals(220_000L, resolveDurationMs(0L, 220))
        assertEquals(220_000L, resolveDurationMs(-9_223_372_036_854_775_807L, 220))
    }

    @Test
    fun 서버_길이가_음수면_0이다() {
        assertEquals(0L, resolveDurationMs(0L, -5))
    }

    @Test
    fun 비율을_위치로_바꾼다() {
        assertEquals(0L, fractionToPositionMs(0f, 100_000L))
        assertEquals(50_000L, fractionToPositionMs(0.5f, 100_000L))
        assertEquals(100_000L, fractionToPositionMs(1f, 100_000L))
    }

    @Test
    fun 범위를_벗어난_비율과_길이_0은_보정한다() {
        assertEquals(0L, fractionToPositionMs(-1f, 100_000L))
        assertEquals(100_000L, fractionToPositionMs(2f, 100_000L))
        assertEquals(0L, fractionToPositionMs(Float.NaN, 100_000L))
        assertEquals(0L, fractionToPositionMs(0.5f, 0L))
    }

    @Test
    fun 위치를_비율로_바꾼다() {
        assertEquals(0.5f, positionToFraction(50_000L, 100_000L), 0.0001f)
        assertEquals(1f, positionToFraction(120_000L, 100_000L), 0f)
        assertEquals(0f, positionToFraction(10L, 0L), 0f)
        assertEquals(0f, positionToFraction(-10L, 100_000L), 0f)
    }
}
