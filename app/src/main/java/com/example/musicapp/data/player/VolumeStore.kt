package com.example.musicapp.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private val KEY_VOLUME = floatPreferencesKey("volume")

const val DEFAULT_VOLUME = 1f

/** 저장된 볼륨 값을 검증한다. 없음·NaN·무한대·0..1 밖의 손상 값은 기본값(1.0)으로 되돌린다. */
fun normalizeVolume(raw: Float?): Float {
    if (raw == null || raw.isNaN() || raw.isInfinite()) return DEFAULT_VOLUME
    if (raw < 0f || raw > 1f) return DEFAULT_VOLUME
    return raw
}

/** 앱 내 플레이어 볼륨(0.0~1.0)을 별도 DataStore 파일에 저장한다. 시스템 미디어 볼륨과 무관하다. */
@Singleton
class VolumeStore @Inject constructor(@Named("player") private val dataStore: DataStore<Preferences>) {

    /** 저장된 볼륨. 읽기에 실패하거나 값이 손상되었으면 1.0이다. */
    suspend fun load(): Float {
        return try {
            normalizeVolume(dataStore.data.first()[KEY_VOLUME])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DEFAULT_VOLUME
        }
    }

    suspend fun save(volume: Float) {
        dataStore.edit { it[KEY_VOLUME] = volume.coerceIn(0f, 1f) }
    }
}
