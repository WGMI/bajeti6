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

data class SettingsUiState(
    val options: SettingsOptionsResponse = SettingsOptionsResponse(
        currency = listOf("USD", "EUR", "GBP", "TZS", "KES", "NGN", "ZAR", "INR"),
        dateFormat = listOf("short", "medium", "long"),
        firstDayOfWeek = listOf("sunday", "monday"),
        theme = listOf("system", "light", "dark"),
    ),
    val selectedCurrency: String = "USD",
    val selectedTheme: String = "system",
    val userName: String = "",
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            selectedCurrency = prefs.getString("currency", "USD") ?: "USD",
            selectedTheme = prefs.getString("theme", "system") ?: "system",
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        fetchOptions()
        observeUser()
    }

    private fun observeUser() {
        viewModelScope.launch {
            Clerk.userFlow.collect { user ->
                val name = buildString {
                    user?.firstName?.let { append(it) }
                    user?.lastName?.let { if (isNotEmpty()) append(" "); append(it) }
                }.ifEmpty {
                    user?.emailAddresses?.firstOrNull()?.emailAddress ?: ""
                }
                _uiState.value = _uiState.value.copy(userName = name)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                Clerk.auth.signOut()
            } catch (_: Exception) {
                // sign-out best-effort
            }
        }
    }

    fun selectCurrency(currency: String) {
        prefs.edit().putString("currency", currency).apply()
        _uiState.value = _uiState.value.copy(selectedCurrency = currency)
    }

    fun selectTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _uiState.value = _uiState.value.copy(selectedTheme = theme)
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
