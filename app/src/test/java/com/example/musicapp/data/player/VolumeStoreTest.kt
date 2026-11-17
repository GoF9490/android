package com.example.musicapp.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 메모리 DataStore(실제 파일 DataStore는 Windows에서 rename이 실패해 쓰지 않는다). */
internal class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class VolumeStoreTest {

    @Test
    fun 정상_값은_그대로_쓴다() {
        assertEquals(0f, normalizeVolume(0f), 0f)
        assertEquals(0.3f, normalizeVolume(0.3f), 0f)
        assertEquals(1f, normalizeVolume(1f), 0f)
    }

    @Test
    fun 없음과_손상_값은_1점0이다() {
        assertEquals(1f, normalizeVolume(null), 0f)
        assertEquals(1f, normalizeVolume(Float.NaN), 0f)
        assertEquals(1f, normalizeVolume(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, normalizeVolume(-0.1f), 0f)
        assertEquals(1f, normalizeVolume(7f), 0f)
    }

    @Test
    fun 빈_저장소는_1점0이다() = runTest {
        assertEquals(1f, VolumeStore(FakeDataStore()).load(), 0f)
    }

    @Test
    fun 저장한_값을_다른_인스턴스가_복원한다() = runTest {
        val dataStore = FakeDataStore()
        VolumeStore(dataStore).save(0.4f)
        assertEquals(0.4f, VolumeStore(dataStore).load(), 0f)
    }

    @Test
    fun 저장소의_손상_값은_1점0으로_복원한다() = runTest {
        val dataStore = FakeDataStore()
        dataStore.edit { it[floatPreferencesKey("volume")] = Float.NaN }
        assertEquals(1f, VolumeStore(dataStore).load(), 0f)
        dataStore.edit { it[floatPreferencesKey("volume")] = 5f }
        assertEquals(1f, VolumeStore(dataStore).load(), 0f)
    }

    @Test
    fun 저장은_범위로_보정한다() = runTest {
        val dataStore = FakeDataStore()
        VolumeStore(dataStore).save(3f)
        assertEquals(1f, VolumeStore(dataStore).load(), 0f)
    }
}
