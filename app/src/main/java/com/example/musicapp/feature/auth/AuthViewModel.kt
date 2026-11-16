package com.example.musicapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicapp.data.auth.AuthRepository
import com.example.musicapp.data.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.state

    fun handleLogout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** 화면이 앞으로 돌아올 때 호출한다. 분기는 Repository가 판단한다. */
    fun handleForeground() {
        if (authState.value == AuthState.Loading) return
        viewModelScope.launch { authRepository.refreshUser() }
    }

    /** 딥링크로 받은 토큰으로 로그인한다(Task 008에서 호출). */
    fun handleLogin(token: String) {
        viewModelScope.launch { authRepository.login(token) }
    }
}
