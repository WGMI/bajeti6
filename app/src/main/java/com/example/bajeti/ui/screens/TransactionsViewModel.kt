package com.example.bajeti.ui.screens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateTransactionRequest
import com.example.bajeti.data.Transaction
import com.example.bajeti.data.UpdateTransactionRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val currency: String = "USD",
    val typeFilter: String? = null,
    val search: String = "",
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
)

private const val TAG = "TransactionsViewModel"

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        TransactionsUiState(currency = prefs.getString("currency", "USD") ?: "USD")
    )
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        load(reset = true)
    }

    fun setTypeFilter(type: String?) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
        load(reset = true)
    }

    fun setSearch(query: String) {
        _uiState.value = _uiState.value.copy(search = query)
        load(reset = true)
    }

    fun setDateFrom(date: String?) {
        _uiState.value = _uiState.value.copy(dateFrom = date)
        load(reset = true)
    }

    fun setDateTo(date: String?) {
        _uiState.value = _uiState.value.copy(dateTo = date)
        load(reset = true)
    }

    fun clearFilters() {
        Log.d(TAG, "clearFilters()")
        _uiState.value = _uiState.value.copy(typeFilter = null, search = "", dateFrom = null, dateTo = null)
        load(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        Log.d(TAG, "loadMore() called — isLoadingMore=${state.isLoadingMore} hasMore=${state.hasMore} cursor=${state.nextCursor}")
        if (state.isLoadingMore || !state.hasMore) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    fun createTransaction(request: CreateTransactionRequest, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                ApiClient.smsApi.createTransaction("Bearer $token", request)
                onDone(true)
                reloadAfterAction()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create transaction", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update transaction $id", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete transaction $id", e)
                onDone(false)
            }
        }
    }

    private fun reloadAfterAction() {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: error("Not authenticated")
                val s = _uiState.value
                val response = ApiClient.summaryApi.getTransactions(
                    authorization = "Bearer $token",
                    limit = 20,
                    cursor = null,
                    type = s.typeFilter,
                    dateFrom = s.dateFrom,
                    dateTo = s.dateTo,
                    search = s.search.takeIf { it.isNotBlank() },
                )
                _uiState.value = _uiState.value.copy(
                    transactions = response.transactions,
                    nextCursor = response.nextCursor,
                    hasMore = response.nextCursor != null,
                    totalIncome = response.totalIncome,
                    totalExpense = response.totalExpense,
                    error = null,
                )
            } catch (_: Exception) {}
        }
    }

    private fun load(reset: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val state = _uiState.value
            Log.d(TAG, "load(reset=$reset) — existing count=${state.transactions.size} cursor=${state.nextCursor}")
            if (reset) {
                _uiState.value = state.copy(
                    isLoading = true,
                    error = null,
                    transactions = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                )
            } else {
                _uiState.value = state.copy(isLoadingMore = true)
            }

            try {
                val token = getJwtToken() ?: error("Not authenticated")
                val cursor = if (reset) null else _uiState.value.nextCursor
                val s = _uiState.value
                Log.d(TAG, "Fetching — limit=20 cursor=$cursor type=${s.typeFilter} search=${s.search} dateFrom=${s.dateFrom} dateTo=${s.dateTo}")
                val response = ApiClient.summaryApi.getTransactions(
                    authorization = "Bearer $token",
                    limit = 20,
                    cursor = cursor,
                    type = s.typeFilter,
                    dateFrom = s.dateFrom,
                    dateTo = s.dateTo,
                    search = s.search.takeIf { it.isNotBlank() },
                )
                val current = if (reset) emptyList() else _uiState.value.transactions
                val merged = current + response.transactions
                Log.d(TAG, "Fetched ${response.transactions.size} tx — nextCursor=${response.nextCursor} total now=${merged.size}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                    transactions = merged,
                    nextCursor = response.nextCursor,
                    hasMore = response.nextCursor != null,
                    totalIncome = response.totalIncome,
                    totalExpense = response.totalExpense,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transactions", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load transactions",
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
}
