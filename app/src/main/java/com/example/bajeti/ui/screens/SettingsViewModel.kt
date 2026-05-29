package com.example.bajeti.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateCategoryRequest
import com.example.bajeti.data.DeleteCategoryConflictResponse
import com.example.bajeti.data.DeleteCategoryRequest
import com.example.bajeti.data.SettingsOptionsResponse
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.data.UpdateCategoryRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
    val categories: List<SmsCategory> = emptyList(),
    val categoriesLoading: Boolean = true,
    val isSavingCategory: Boolean = false,
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
        loadCategories()
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
            try { Clerk.auth.signOut() } catch (_: Exception) {}
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

    // ── Categories ────────────────────────────────────────────────────────────

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(categoriesLoading = true)
            try {
                val token = getJwtToken() ?: return@launch
                val cats = ApiClient.summaryApi.getCategories("Bearer $token")
                _uiState.value = _uiState.value.copy(categories = cats, categoriesLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(categoriesLoading = false)
            }
        }
    }

    fun createCategory(name: String, type: String, onDone: (SmsCategory?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingCategory = true)
            try {
                val token = getJwtToken() ?: run { onDone(null); return@launch }
                val cat = ApiClient.summaryApi.createCategory(
                    "Bearer $token",
                    CreateCategoryRequest(name = name.trim(), type = type),
                )
                _uiState.value = _uiState.value.copy(
                    categories = (_uiState.value.categories + cat)
                        .sortedWith(compareBy({ typeOrder(it.type) }, { it.name })),
                    isSavingCategory = false,
                )
                onDone(cat)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSavingCategory = false)
                onDone(null)
            }
        }
    }

    fun updateCategory(id: String, name: String, type: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingCategory = true)
            try {
                val token = getJwtToken() ?: run { onDone(false); return@launch }
                val updated = ApiClient.summaryApi.updateCategory(
                    "Bearer $token",
                    id,
                    UpdateCategoryRequest(name = name.trim(), type = type),
                )
                _uiState.value = _uiState.value.copy(
                    categories = _uiState.value.categories
                        .map { if (it.id == id) updated else it }
                        .sortedWith(compareBy({ typeOrder(it.type) }, { it.name })),
                    isSavingCategory = false,
                )
                onDone(true)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSavingCategory = false)
                onDone(false)
            }
        }
    }

    /**
     * Attempts a plain delete (no body). If the server returns 409 (category has transactions),
     * calls [onConflict] with the transaction count instead of [onDone].
     */
    fun deleteCategory(
        id: String,
        onConflict: (transactionCount: Int) -> Unit,
        onDone: (success: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: run { onDone(false); return@launch }
                ApiClient.summaryApi.deleteCategory("Bearer $token", id, DeleteCategoryRequest())
                _uiState.value = _uiState.value.copy(
                    categories = _uiState.value.categories.filter { it.id != id }
                )
                onDone(true)
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    val count = try {
                        val body = e.response()?.errorBody()?.string() ?: ""
                        Gson().fromJson(body, DeleteCategoryConflictResponse::class.java)
                            .transactionCount ?: 0
                    } catch (_: Exception) { 0 }
                    onConflict(count)
                } else {
                    onDone(false)
                }
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun deleteCategoryWithReassign(id: String, reassignToId: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: run { onDone(false); return@launch }
                ApiClient.summaryApi.deleteCategory(
                    "Bearer $token", id,
                    DeleteCategoryRequest(reassignToCategoryId = reassignToId),
                )
                _uiState.value = _uiState.value.copy(
                    categories = _uiState.value.categories.filter { it.id != id }
                )
                onDone(true)
            } catch (_: Exception) { onDone(false) }
        }
    }

    fun deleteCategoryAndTransactions(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: run { onDone(false); return@launch }
                ApiClient.summaryApi.deleteCategory(
                    "Bearer $token", id,
                    DeleteCategoryRequest(deleteTransactions = true),
                )
                _uiState.value = _uiState.value.copy(
                    categories = _uiState.value.categories.filter { it.id != id }
                )
                onDone(true)
            } catch (_: Exception) { onDone(false) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            } catch (_: Exception) {}
        }
    }

    private suspend fun getJwtToken(): String? = try {
        when (val result = Clerk.auth.getToken()) {
            is ClerkResult.Success -> result.value
            else -> null
        }
    } catch (_: Exception) { null }

    private fun typeOrder(type: String) = when (type) {
        "expense"  -> 0
        "income"   -> 1
        "transfer" -> 2
        else       -> 3
    }
}
