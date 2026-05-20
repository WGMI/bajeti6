package com.example.bajeti.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.sso.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppAuthState {
    data object Loading : AppAuthState
    data object SignedIn : AppAuthState
    data object SignedOut : AppAuthState
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel : ViewModel() {

    val appAuthState: StateFlow<AppAuthState> = combine(
        Clerk.isInitialized,
        Clerk.userFlow,
    ) { isInitialized, user ->
        when {
            !isInitialized -> AppAuthState.Loading
            user != null -> AppAuthState.SignedIn
            else -> AppAuthState.SignedOut
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppAuthState.Loading,
    )

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            try {
                Clerk.auth.signInWithPassword {
                    identifier = email
                    this.password = password
                }
                _loginUiState.value = LoginUiState()
            } catch (e: Exception) {
                _loginUiState.value = LoginUiState(error = e.message ?: "Sign-in failed. Check your credentials.")
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            try {
                Clerk.auth.signInWithOAuth(OAuthProvider.GOOGLE)
                _loginUiState.value = LoginUiState()
            } catch (e: Exception) {
                _loginUiState.value = LoginUiState(error = e.message ?: "Google sign-in failed.")
            }
        }
    }

    fun clearError() {
        _loginUiState.value = _loginUiState.value.copy(error = null)
    }
}
