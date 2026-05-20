package com.example.bajeti.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.SettingsOptionsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val options: SettingsOptionsResponse = SettingsOptionsResponse(
        currency = listOf("USD", "EUR", "GBP", "TZS", "KES", "NGN", "ZAR", "INR"),
        dateFormat = listOf("short", "medium", "long"),
        firstDayOfWeek = listOf("sunday", "monday"),
        theme = listOf("system", "light", "dark"),
    ),
    val selectedCurrency: String = "USD",
    val selectedTheme: String = "system",
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        fetchOptions()
    }

    fun selectCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(selectedCurrency = currency)
    }

    fun selectTheme(theme: String) {
        _uiState.value = _uiState.value.copy(selectedTheme = theme)
    }

    fun completeOnboarding() {
        val state = _uiState.value
        prefs.edit()
            .putString("currency", state.selectedCurrency)
            .putString("theme", state.selectedTheme)
            .putBoolean("onboarding_done", true)
            .apply()
    }

    private fun fetchOptions() {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: return@launch
                val response = ApiClient.summaryApi.getSettingsOptions("Bearer $token")
                _uiState.value = _uiState.value.copy(
                    options = response.copy(
                        currency = response.currency.ifEmpty { listOf("USD") },
                        theme = response.theme.ifEmpty { listOf("system", "light", "dark") },
                    )
                )
            } catch (_: Exception) {
                // keep fallback defaults on failure
            }
        }
    }

    private suspend fun getJwtToken(): String? = try {
        when (val result = Clerk.auth.getToken()) {
            is ClerkResult.Success -> result.value
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
