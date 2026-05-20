package com.example.bajeti.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateTransactionRequest
import com.example.bajeti.data.SummaryResponse
import com.example.bajeti.data.Transaction
import com.example.bajeti.data.UpdateTransactionRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Calendar

data class OverviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: SummaryResponse? = null,
    val transactions: List<Transaction> = emptyList(),
    val currency: String = "USD",
)

class OverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        OverviewUiState(currency = prefs.getString("currency", "USD") ?: "USD")
    )
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        fetchData()
    }

    fun refresh() = fetchData()

    fun createTransaction(request: CreateTransactionRequest, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                ApiClient.smsApi.createTransaction("Bearer $token", request)
                onDone(true)
                reloadAfterAction()
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun updateTransaction(id: String, request: UpdateTransactionRequest, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                ApiClient.smsApi.updateTransaction("Bearer $token", id, request)
                onDone(true)
                reloadAfterAction()
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun deleteTransaction(id: String, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                ApiClient.smsApi.deleteTransaction("Bearer $token", id)
                onDone(true)
                reloadAfterAction()
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    private fun reloadAfterAction() {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                coroutineScope {
                    val summaryDeferred = async {
                        ApiClient.summaryApi.getSummary(
                            authorization = "Bearer $token",
                            month = currentMonth(),
                            trendMonths = 6,
                        )
                    }
                    val txDeferred = async {
                        ApiClient.summaryApi.getTransactions(
                            authorization = "Bearer $token",
                            limit = 20,
                            dateFrom = dateSevenDaysAgo(),
                            dateTo = today(),
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        summary = summaryDeferred.await(),
                        transactions = txDeferred.await().transactions,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun fetchData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                withTimeout(30_000L) {
                    val token = getJwtToken() ?: error("Not authenticated")
                    coroutineScope {
                        val summaryDeferred = async {
                            ApiClient.summaryApi.getSummary(
                                authorization = "Bearer $token",
                                month = currentMonth(),
                                trendMonths = 6,
                            )
                        }
                        val txDeferred = async {
                            ApiClient.summaryApi.getTransactions(
                                authorization = "Bearer $token",
                                limit = 20,
                                dateFrom = dateSevenDaysAgo(),
                                dateTo = today(),
                            )
                        }
                        val summary = summaryDeferred.await()
                        val txResponse = txDeferred.await()
                        android.util.Log.d("OverviewViewModel", "Transactions: ${txResponse.transactions}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            summary = summary,
                            transactions = txResponse.transactions,
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Request timed out. Check your connection.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load data",
                )
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

    private fun currentMonth(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun today(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun dateSevenDaysAgo(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
