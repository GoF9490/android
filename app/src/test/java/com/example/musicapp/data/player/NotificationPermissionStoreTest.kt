package com.example.musicapp.data.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStoreTest {

    @Test
    fun API_33_이상에서_미허용_미요청일_때만_요청한다() {
        assertEquals(true, shouldRequestNotificationPermission(33, granted = false, alreadyAsked = false))
        assertEquals(true, shouldRequestNotificationPermission(37, granted = false, alreadyAsked = false))
        assertEquals(false, shouldRequestNotificationPermission(32, granted = false, alreadyAsked = false))
        assertEquals(false, shouldRequestNotificationPermission(34, granted = true, alreadyAsked = false))
        assertEquals(false, shouldRequestNotificationPermission(34, granted = false, alreadyAsked = true))
    }

    @Test
    fun 빈_저장소는_묻지_않은_상태다() = runTest {
        assertEquals(false, NotificationPermissionStore(FakeDataStore()).hasAsked())
    }

    @Test
    fun 기록한_뒤에는_다른_인스턴스에서도_물은_상태다() = runTest {
        val dataStore = FakeDataStore()
        NotificationPermissionStore(dataStore).markAsked()
        assertEquals(true, NotificationPermissionStore(dataStore).hasAsked())
    }
}
