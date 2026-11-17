package com.example.musicapp.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private val KEY_NOTIFICATION_ASKED = booleanPreferencesKey("notificationPermissionAsked")

/** Android 13(API 33) 이상에서만 알림 권한이 런타임 권한이다. */
private const val NOTIFICATION_PERMISSION_MIN_SDK = 33

/**
 * 알림 권한 요청 여부(PRD §7.3). 앱이 한 번 물었으면 결과와 무관하게 다시 묻지 않는다.
 * 허용 여부와 분리해 둔 이유: 시스템 상태(rationale)만으로는 "묻기 전"과 "영구 거부"를 구분할 수 없다.
 */
fun shouldRequestNotificationPermission(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= NOTIFICATION_PERMISSION_MIN_SDK && !granted && !alreadyAsked

@Singleton
class NotificationPermissionStore @Inject constructor(@Named("player") private val dataStore: DataStore<Preferences>) {

    /** 이미 물었는지. 읽기에 실패하면 반복 요청을 막기 위해 true로 본다. */
    suspend fun hasAsked(): Boolean {
        return try {
            dataStore.data.first()[KEY_NOTIFICATION_ASKED] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
    }

    /** 요청 다이얼로그를 띄우는 시점에 기록한다. 저장 실패는 무시한다(재생에 영향 없음). */
    suspend fun markAsked() {
        try {
            dataStore.edit { it[KEY_NOTIFICATION_ASKED] = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 다음 재생에서 다시 시도한다.
        }
    }
}
