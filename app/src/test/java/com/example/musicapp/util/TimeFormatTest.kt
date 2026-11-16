package com.example.musicapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatDuration_경계값을_올바르게_표기한다() {
        val cases = listOf(
            0 to "0:00",
            1 to "0:01",
            59 to "0:59",
            60 to "1:00",
            61 to "1:01",
            599 to "9:59",
            600 to "10:00",
            3599 to "59:59",
            3600 to "1:00:00",
            3661 to "1:01:01",
            36000 to "10:00:00",
            -1 to "0:00",
        )
        cases.forEach { (input, expected) ->
            assertEquals("input=$input", expected, formatDuration(input))
        }
    }

    @Test
    fun formatClock_경계값을_올바르게_표기한다() {
        val cases = listOf(
            0L to "00:00",
            999L to "00:00",
            1000L to "00:01",
            59_999L to "00:59",
            60_000L to "01:00",
            75_000L to "01:15",
            3_599_000L to "59:59",
            3_600_000L to "1:00:00",
            3_661_000L to "1:01:01",
        )
        cases.forEach { (input, expected) ->
            assertEquals("input=$input", expected, formatClock(input))
        }
    }

    @Test
    fun formatClock_음수와_비정상_값은_0으로_표기한다() {
        assertEquals("00:00", formatClock(-1))
        assertEquals("00:00", formatClock(Long.MIN_VALUE))
        // C.TIME_UNSET과 같은 값
        assertEquals("00:00", formatClock(Long.MIN_VALUE + 1))
    }

    @Test
    fun formatClock_최대값도_예외없이_문자열을_반환한다() {
        assertTrue(formatClock(Long.MAX_VALUE).isNotEmpty())
    }
}
