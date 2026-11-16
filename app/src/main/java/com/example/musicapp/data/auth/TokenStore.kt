package com.example.musicapp.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val KEY_TOKEN = stringPreferencesKey("jwt")

/**
 * JWT를 DataStore(Preferences)에 저장하고 인터셉터용 동기 조회를 위해 메모리에 캐시한다.
 * 캐시 로드와 쓰기는 한 Mutex로 직렬화한다(오래된 값이 새 값을 덮어쓰지 않도록 collect로 갱신하지 않는다).
 * 토큰은 toString·로그에 나타나지 않는다.
 */
@Singleton
class TokenStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    /** 저장소 값의 변화를 관찰한다. */
    val token: Flow<String?> = dataStore.data.map { it[KEY_TOKEN] }.distinctUntilChanged()

    /** 캐시 로드를 보장한 뒤 현재 토큰을 돌려준다(앱 기동 시 선로드·AuthRepository용). */
    suspend fun awaitToken(): String? {
        ensureLoaded()
        return cached
    }

    /**
     * 인터셉터용 동기 조회. 로드 전 최초 1회만 DataStore를 블로킹으로 읽으므로
     * 메인 스레드에서 호출하지 않는다(OkHttp 스레드 전용).
     */
    fun getCachedToken(): String? {
        if (!loaded) runBlocking { ensureLoaded() }
        return cached
    }

    suspend fun saveToken(token: String) {
        mutex.withLock {
            dataStore.edit { it[KEY_TOKEN] = token }
            cached = token
            loaded = true
        }
    }

    suspend fun clearToken() {
        mutex.withLock {
            dataStore.edit { it.remove(KEY_TOKEN) }
            cached = null
            loaded = true
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (!loaded) {
                cached = dataStore.data.first()[KEY_TOKEN]
                loaded = true
            }
        }
    }
}
