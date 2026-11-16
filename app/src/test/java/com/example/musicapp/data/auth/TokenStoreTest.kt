package com.example.musicapp.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 메모리 DataStore. 실제 파일 DataStore는 Windows에서 덮어쓰기 rename이 실패해(FileMoves) 단위 테스트에 쓰지 않는다.
 * 같은 인스턴스를 두 TokenStore가 공유하면 앱 재시작(저장된 값 유지)을 흉내 낼 수 있다.
 */
private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class TokenStoreTest {

    @Test
    fun 빈_저장소는_null이다() = runTest {
        val store = TokenStore(FakeDataStore())
        assertNull(store.getCachedToken())
        assertNull(store.awaitToken())
    }

    @Test
    fun 저장하면_캐시와_Flow와_await가_새_값이다() = runTest {
        val store = TokenStore(FakeDataStore())
        store.saveToken("tkn-1")
        assertEquals("tkn-1", store.getCachedToken())
        assertEquals("tkn-1", store.awaitToken())
        store.token.test {
            assertEquals("tkn-1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 삭제하면_모두_null이다() = runTest {
        val store = TokenStore(FakeDataStore())
        store.saveToken("tkn-1")
        store.clearToken()
        assertNull(store.getCachedToken())
        assertNull(store.awaitToken())
        store.token.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 재시작해도_저장된_토큰을_읽는다() = runTest {
        val dataStore = FakeDataStore()
        TokenStore(dataStore).saveToken("tkn-1")

        // 새 인스턴스는 로드 전 getCachedToken()이 블로킹 로드로 값을 돌려준다
        assertEquals("tkn-1", TokenStore(dataStore).getCachedToken())
    }

    @Test
    fun 로드_전_저장해도_이후_읽기는_새_값이다() = runTest {
        val dataStore = FakeDataStore()
        TokenStore(dataStore).saveToken("old")

        val store = TokenStore(dataStore)
        store.saveToken("new")
        assertEquals("new", store.getCachedToken())
        assertEquals("new", store.awaitToken())
    }

    @Test
    fun toString에_토큰이_노출되지_않는다() = runTest {
        val store = TokenStore(FakeDataStore())
        store.saveToken("secret-token-value")
        assertFalse(store.toString().contains("secret-token-value"))
        assertFalse(store.token.toString().contains("secret-token-value"))
    }
}
