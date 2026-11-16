package com.example.musicapp.feature.auth

import com.example.musicapp.data.auth.AuthRepository
import com.example.musicapp.data.auth.AuthState
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val stateFlow = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    private val repository = mockk<AuthRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.state } returns stateFlow
        coEvery { repository.logout() } returns Unit
        coEvery { repository.refreshUser() } returns Unit
        coEvery { repository.login(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun authState는_Repository_상태를_그대로_노출한다() {
        val viewModel = AuthViewModel(repository)
        assertSame(stateFlow, viewModel.authState)
    }

    @Test
    fun 로그아웃_액션은_Repository_logout을_부른다() = runTest(dispatcher) {
        AuthViewModel(repository).handleLogout()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.logout() }
    }

    @Test
    fun 포그라운드_복귀시_Loading이면_재조회하지_않는다() = runTest(dispatcher) {
        stateFlow.value = AuthState.Loading
        AuthViewModel(repository).handleForeground()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.refreshUser() }
    }

    @Test
    fun 포그라운드_복귀시_Loading이_아니면_재조회한다() = runTest(dispatcher) {
        AuthViewModel(repository).handleForeground()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.refreshUser() }
    }

    @Test
    fun 로그인_액션은_토큰을_Repository에_넘긴다() = runTest(dispatcher) {
        AuthViewModel(repository).handleLogin("token-x")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.login("token-x") }
    }
}
