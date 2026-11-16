package com.example.musicapp.data.auth

import app.cash.turbine.test
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.model.UserResponse
import com.example.musicapp.ui.MessageBus
import com.example.musicapp.ui.UiMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val user = UserResponse(userId = 1, email = "a@example.com", profileImageUrl = null, role = "USER")
    private val nowSeconds = System.currentTimeMillis() / 1000
    private val validToken = fakeJwt("""{"exp":${nowSeconds + 3600}}""")
    private val expiredToken = fakeJwt("""{"exp":${nowSeconds - 10}}""")

    private val api = mockk<MusicApi>()
    private val messageBus = mockk<MessageBus>()
    private val posted = mutableListOf<UiMessage>()
    private var storedToken: String? = null
    private val tokenStore = mockk<TokenStore>()

    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        coEvery { tokenStore.awaitToken() } answers { storedToken }
        coEvery { tokenStore.saveToken(any()) } answers { storedToken = firstArg() }
        coEvery { tokenStore.clearToken() } answers { storedToken = null }
        io.mockk.every { messageBus.post(any()) } answers {
            posted += firstArg<UiMessage>()
            true
        }
        repository = AuthRepository(api, tokenStore, messageBus)
    }

    private fun httpError(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun 초기_상태는_Loading이다() {
        assertEquals(AuthState.Loading, repository.state.value)
    }

    @Test
    fun 기동시_토큰이_없으면_me_없이_LoggedOut() = runTest {
        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        coVerify(exactly = 0) { api.getMe() }
    }

    @Test
    fun 기동시_만료_토큰이면_me_없이_조용히_로그아웃() = runTest {
        storedToken = expiredToken

        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertNull(storedToken)
        coVerify(exactly = 0) { api.getMe() }
        assertTrue(posted.isEmpty())
    }

    @Test
    fun 기동시_유효_토큰이면_me_성공으로_LoggedIn() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } returns user

        repository.state.test {
            assertEquals(AuthState.Loading, awaitItem())
            repository.initialize()
            assertEquals(AuthState.LoggedIn(user), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(validToken, storedToken)
    }

    @Test
    fun me가_401이면_토큰_삭제와_UNAUTHORIZED_스낵바_1회() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws httpError(401)

        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertNull(storedToken)
        assertEquals(listOf("UNAUTHORIZED"), posted.map { it.id })
    }

    @Test
    fun 토큰이_삭제된_뒤_재조회는_me를_부르지_않는다() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws httpError(401)

        repository.initialize()
        repository.refreshUser()

        coVerify(exactly = 1) { api.getMe() }
        assertEquals(1, posted.size)
    }

    @Test
    fun me가_5xx이면_토큰을_유지하고_LoggedOut() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws httpError(503)

        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertEquals(validToken, storedToken)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun me가_네트워크_실패이면_토큰을_유지하고_LoggedOut() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws IOException("offline")

        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertEquals(validToken, storedToken)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun 기타_4xx도_토큰을_유지하고_LoggedOut() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws httpError(403)

        repository.initialize()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertEquals(validToken, storedToken)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun 네트워크_실패_뒤_재조회가_성공하면_LoggedIn() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } throws IOException("offline")
        repository.initialize()

        coEvery { api.getMe() } returns user
        repository.refreshUser()

        assertEquals(AuthState.LoggedIn(user), repository.state.value)
    }

    @Test
    fun LoggedIn에서_유효_토큰이면_재조회는_네트워크를_부르지_않는다() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } returns user
        repository.initialize()

        repository.refreshUser()

        coVerify(exactly = 1) { api.getMe() }
        assertEquals(AuthState.LoggedIn(user), repository.state.value)
    }

    @Test
    fun LoggedIn에서_토큰이_만료되면_조용히_로그아웃() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } returns user
        repository.initialize()
        storedToken = expiredToken

        repository.refreshUser()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertNull(storedToken)
        coVerify(exactly = 1) { api.getMe() }
        assertTrue(posted.isEmpty())
    }

    @Test
    fun 토큰이_없으면_재조회는_me를_부르지_않는다() = runTest {
        repository.initialize()
        repository.refreshUser()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        coVerify(exactly = 0) { api.getMe() }
    }

    @Test
    fun 로그인은_저장한_뒤_me를_조회한다() = runTest {
        coEvery { api.getMe() } returns user

        repository.login(validToken)

        coVerifyOrder {
            tokenStore.saveToken(validToken)
            api.getMe()
        }
        assertEquals(AuthState.LoggedIn(user), repository.state.value)
    }

    @Test
    fun 로그인_직후_me가_401이면_토큰_삭제와_스낵바() = runTest {
        coEvery { api.getMe() } throws httpError(401)

        repository.login(validToken)

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertNull(storedToken)
        assertEquals(listOf("UNAUTHORIZED"), posted.map { it.id })
    }

    @Test
    fun 로그아웃은_서버_호출_없이_토큰만_삭제한다() = runTest {
        storedToken = validToken
        coEvery { api.getMe() } returns user
        repository.initialize()

        repository.logout()

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertNull(storedToken)
        coVerify(exactly = 1) { api.getMe() }
        verify(exactly = 0) { messageBus.post(any()) }
        confirmVerified(api)
    }

    @Test
    fun 취소는_전파되고_로그인_중_Loading에_갇히지_않는다() = runTest {
        coEvery { api.getMe() } throws CancellationException("취소")

        try {
            repository.login(validToken)
            fail("취소가 전파되어야 한다")
        } catch (e: CancellationException) {
            // 기대한 동작
        }

        assertEquals(AuthState.LoggedOut, repository.state.value)
        assertEquals(validToken, storedToken)
    }
}
