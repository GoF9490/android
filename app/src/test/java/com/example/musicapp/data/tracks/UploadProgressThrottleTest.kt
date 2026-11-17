package com.example.musicapp.data.tracks

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadProgressThrottleTest {

    private var now = 0L
    private val reported = mutableListOf<Int>()
    private val throttle = UploadProgressThrottle(minIntervalMillis = 100, clock = { now }) { reported.add(it) }

    @Test
    fun 같은_퍼센트는_한_번만_보고한다() {
        throttle.report(0, 1000)
        now = 500
        throttle.report(5, 1000)
        throttle.report(9, 1000)
        assertEquals(listOf(0), reported)
    }

    @Test
    fun 간격_안의_변화는_건너뛰고_간격_후에는_통과한다() {
        throttle.report(0, 100)
        now = 50
        throttle.report(10, 100)
        now = 120
        throttle.report(20, 100)
        assertEquals(listOf(0, 20), reported)
    }

    @Test
    fun 시작과_완료는_간격과_무관하게_보고한다() {
        throttle.report(0, 100)
        throttle.report(50, 100)
        throttle.report(100, 100)
        assertEquals(listOf(0, 100), reported)
    }

    @Test
    fun 전체_길이를_모르면_보고하지_않는다() {
        throttle.report(10, -1)
        throttle.report(10, 0)
        assertEquals(emptyList<Int>(), reported)
    }
}
