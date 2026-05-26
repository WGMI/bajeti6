package com.example.bajeti.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.auth.types.VerificationType
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.sendEmailCode
import com.clerk.api.signup.verifyCode
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

sealed interface AuthScreenState {
    data object Login : AuthScreenState
    data object SignUp : AuthScreenState
    data object VerifyEmail : AuthScreenState
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SignUpUiState(
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

    private val _authScreen = MutableStateFlow<AuthScreenState>(AuthScreenState.Login)
    val authScreen: StateFlow<AuthScreenState> = _authScreen.asStateFlow()

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _signUpUiState = MutableStateFlow(SignUpUiState())
    val signUpUiState: StateFlow<SignUpUiState> = _signUpUiState.asStateFlow()

    private var pendingSignUp: SignUp? = null

    fun navigateToSignUp() {
        _authScreen.value = AuthScreenState.SignUp
        _signUpUiState.value = SignUpUiState()
    }

    fun navigateToLogin() {
        _authScreen.value = AuthScreenState.Login
        _loginUiState.value = LoginUiState()
    }

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

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _signUpUiState.value = SignUpUiState(isLoading = true)
            try {
                when (val result = Clerk.auth.signUp {
                    this.email = email
                    this.password = password
                }) {
                    is ClerkResult.Success -> {
                        val signUp = result.value
                        pendingSignUp = signUp
                        when (val sendResult = signUp.sendEmailCode()) {
                            is ClerkResult.Success -> {
                                _signUpUiState.value = SignUpUiState()
                                _authScreen.value = AuthScreenState.VerifyEmail
                            }
                            is ClerkResult.Failure -> {
                                _signUpUiState.value = SignUpUiState(
                                    error = sendResult.clerkMessage("Failed to send verification code.")
                                )
                            }
                            else -> {
                                _signUpUiState.value = SignUpUiState(error = "Failed to send verification code.")
                            }
                        }
                    }
                    is ClerkResult.Failure -> {
                        _signUpUiState.value = SignUpUiState(
                            error = result.clerkMessage("Sign-up failed. Try again.")
                        )
                    }
                    else -> {
                        _signUpUiState.value = SignUpUiState(error = "Sign-up failed. Try again.")
                    }
                }
            } catch (e: Exception) {
                _signUpUiState.value = SignUpUiState(error = e.message ?: "Sign-up failed.")
            }
        }
    }

    fun signUpWithGoogle() {
        viewModelScope.launch {
            _signUpUiState.value = SignUpUiState(isLoading = true)
            try {
                Clerk.auth.signUpWithOAuth(OAuthProvider.GOOGLE)
                _signUpUiState.value = SignUpUiState()
            } catch (e: Exception) {
                _signUpUiState.value = SignUpUiState(error = e.message ?: "Google sign-up failed.")
            }
        }
    }

    fun verifyEmail(code: String) {
        val signUp = pendingSignUp ?: run {
            _signUpUiState.value = SignUpUiState(error = "Session expired. Please sign up again.")
            _authScreen.value = AuthScreenState.SignUp
            return
        }
        viewModelScope.launch {
            _signUpUiState.value = SignUpUiState(isLoading = true)
            try {
                when (val result = signUp.verifyCode(code, VerificationType.EMAIL)) {
                    is ClerkResult.Success -> {
                        pendingSignUp = null
                        _signUpUiState.value = SignUpUiState()
                    }
                    is ClerkResult.Failure -> {
                        _signUpUiState.value = SignUpUiState(
                            error = result.clerkMessage("Incorrect code. Please try again.")
                        )
                    }
                    else -> {
                        _signUpUiState.value = SignUpUiState(error = "Verification failed. Please try again.")
                    }
                }
            } catch (e: Exception) {
                _signUpUiState.value = SignUpUiState(error = e.message ?: "Verification failed.")
            }
        }
    }

    fun resendVerificationCode() {
        val signUp = pendingSignUp ?: return
        viewModelScope.launch {
            try {
                signUp.sendEmailCode()
            } catch (_: Exception) { }
        }
    }

    fun clearLoginError() {
        _loginUiState.value = _loginUiState.value.copy(error = null)
    }

    fun clearSignUpError() {
        _signUpUiState.value = _signUpUiState.value.copy(error = null)
    }

    // Keep backward compat with existing LoginScreen usage
    fun clearError() = clearLoginError()

    private fun ClerkResult.Failure<ClerkErrorResponse>.clerkMessage(fallback: String): String =
        error?.errors?.firstOrNull()?.message ?: throwable?.message ?: fallback
}
