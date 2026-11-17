package com.example.musicapp.feature.player

/**
 * 표시할 곡 길이(ms). 플레이어 duration이 확정되면(0 초과) 그 값을, 아니면 서버가 준 길이를 쓴다.
 * C.TIME_UNSET 같은 음수도 확정되지 않은 값으로 취급한다.
 */
fun resolveDurationMs(playerMs: Long, serverSeconds: Int): Long =
    if (playerMs > 0L) playerMs else serverSeconds.coerceAtLeast(0) * 1000L

/** 슬라이더 비율(0..1)을 위치(ms)로 바꾼다. 길이가 0 이하이면 0이다. */
fun fractionToPositionMs(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0L || fraction.isNaN()) return 0L
    return (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong()
}

/** 위치(ms)를 슬라이더 비율(0..1)로 바꾼다. 길이가 0 이하이면 0이다. */
fun positionToFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
