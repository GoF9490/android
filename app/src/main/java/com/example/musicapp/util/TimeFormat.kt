package com.example.musicapp.util

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/**
 * 목록용 재생 시간 표기. 1시간 미만은 `m:ss`, 1시간 이상은 `h:mm:ss`. 음수는 `0:00`.
 */
fun formatDuration(seconds: Int): String =
    formatTotalSeconds(seconds.toLong(), padMinutes = false)

/**
 * 플레이어용 시각 표기. 1시간 미만은 `mm:ss`, 1시간 이상은 `h:mm:ss`.
 * 음수·비정상 값(예: C.TIME_UNSET)은 `00:00`. 밀리초는 초 단위로 내림한다.
 */
fun formatClock(ms: Long): String =
    formatTotalSeconds(ms / 1000, padMinutes = true)

// 시/분/초로 나눠 문자열을 만든다. 로케일과 무관하게 아라비아 숫자를 쓴다.
private fun formatTotalSeconds(totalSeconds: Long, padMinutes: Boolean): String {
    val safeSeconds = if (totalSeconds < 0) 0L else totalSeconds
    val hours = safeSeconds / SECONDS_PER_HOUR
    val minutes = (safeSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = safeSeconds % SECONDS_PER_MINUTE
    val secondsText = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secondsText"
    } else {
        val minutesText = if (padMinutes) minutes.toString().padStart(2, '0') else minutes.toString()
        "$minutesText:$secondsText"
    }
}
