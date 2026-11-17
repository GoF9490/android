package com.example.musicapp.data.tracks

/**
 * 업로드 진행률 보고 빈도를 제한한다.
 * 정수 퍼센트가 직전 보고와 다르고 [minIntervalMillis] 이상 지났을 때만 통과시키며,
 * 0%(시작)와 100%(완료)는 시간과 무관하게 통과시킨다. 스레드 안전하지 않다(쓰기 스레드 하나에서만 호출).
 */
class UploadProgressThrottle(
    private val minIntervalMillis: Long = 100L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onProgress: (Int) -> Unit,
) {
    private var lastPercent = -1
    private var lastTime = 0L

    /** [total]이 0 이하(길이를 모름)면 보고하지 않는다. */
    fun report(written: Long, total: Long) {
        if (total <= 0L) return
        val percent = (written * 100 / total).toInt().coerceIn(0, 100)
        if (percent == lastPercent) return
        val now = clock()
        val isEdge = percent == 0 || percent == 100
        if (!isEdge && lastPercent >= 0 && now - lastTime < minIntervalMillis) return
        lastPercent = percent
        lastTime = now
        onProgress(percent)
    }
}
