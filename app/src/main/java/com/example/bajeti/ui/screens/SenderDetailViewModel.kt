package com.example.bajeti.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateTransactionRequest
import com.example.bajeti.data.DeviceSmsMessage
import com.example.bajeti.data.PreviewTransaction
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.data.SmsImportResult
import com.example.bajeti.data.SmsPreferences
import com.example.bajeti.data.SmsPreviewRequest
import com.example.bajeti.data.SmsRepository
import com.example.bajeti.data.smsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BajetiSmsDebug"

sealed interface PreviewState {
    object Idle : PreviewState
    data class Loading(val messageId: Long) : PreviewState
    data class Ignored(val reason: String) : PreviewState
    data class Ready(
        val messageId: Long,
        val preview: PreviewTransaction,
        val editedAmount: String,
        val editedDate: String,
        val editedNotes: String,
        val editedCategoryId: String?,
        val needsCategory: Boolean,
        val isSaving: Boolean = false,
        val smsIdempotencyKey: String? = null,
    ) : PreviewState
    object Saved : PreviewState
    data class Duplicate(val message: String) : PreviewState
    data class Error(val message: String) : PreviewState
}

data class SenderDetailUiState(
    val sender: String = "",
    val messages: List<DeviceSmsMessage> = emptyList(),
    val isLoading: Boolean = true,
    val categories: List<SmsCategory> = emptyList(),
    val previewState: PreviewState = PreviewState.Idle,
    val isImporting: Boolean = false,
    val importProgress: String? = null,
    val importResult: SmsImportResult? = null,
    val startDateMillis: Long = -1L,
)

class SenderDetailViewModel(
    application: Application,
    private val sender: String,
) : AndroidViewModel(application) {

    private val prefs = SmsPreferences(application.smsDataStore)
    private val repo = SmsRepository(application, prefs, ApiClient.smsApi)

    private val _uiState = MutableStateFlow(SenderDetailUiState(sender = sender))
    val uiState: StateFlow<SenderDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val startDate = prefs.getStartDate(sender)
            _uiState.update { it.copy(startDateMillis = startDate) }
        }
        loadMessages()
        loadCategories()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val messages = repo.loadNewMessages(sender)
            _uiState.update { it.copy(messages = messages, isLoading = false) }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: return@launch
                val categories = ApiClient.smsApi.getCategories("Bearer $token")
                _uiState.update { it.copy(categories = categories) }
            } catch (_: Exception) {}
        }
    }

    fun previewMessage(message: DeviceSmsMessage) {
        Log.d(TAG, "preview: smsId=${message.id} body=${message.body.take(60)}")
        _uiState.update { it.copy(previewState = PreviewState.Loading(message.id)) }
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: run {
                    _uiState.update { it.copy(previewState = PreviewState.Error("Not signed in")) }
                    return@launch
                }
                val response = ApiClient.smsApi.previewSms(
                    authorization = "Bearer $token",
                    request = SmsPreviewRequest(
                        message = message.body,
                        timestamp = message.timestampMillis,
                    ),
                )
                Log.d(TAG, "preview response: status=${response.status} smsIdempotencyKey=${response.smsIdempotencyKey}")
                when (response.status) {
                    "ignored" -> _uiState.update {
                        it.copy(previewState = PreviewState.Ignored(
                            response.reason ?: "Message could not be parsed as a transaction"
                        ))
                    }
                    "ready", "needs_category" -> {
                        val preview = response.preview!!
                        _uiState.update {
                            it.copy(previewState = PreviewState.Ready(
                                messageId = message.id,
                                preview = preview,
                                editedAmount = preview.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
                                editedDate = preview.date,
                                editedNotes = preview.notes,
                                editedCategoryId = preview.categoryId,
                                needsCategory = response.status == "needs_category",
                                smsIdempotencyKey = response.smsIdempotencyKey,
                            ))
                        }
                    }
                    else -> _uiState.update {
                        it.copy(previewState = PreviewState.Error("Unexpected response: ${response.status}"))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(previewState = PreviewState.Error(e.message ?: "Preview failed")) }
            }
        }
    }

    fun updateAmount(value: String) {
        val current = _uiState.value.previewState as? PreviewState.Ready ?: return
        _uiState.update { it.copy(previewState = current.copy(editedAmount = value)) }
    }

    fun updateDate(value: String) {
        val current = _uiState.value.previewState as? PreviewState.Ready ?: return
        _uiState.update { it.copy(previewState = current.copy(editedDate = value)) }
    }

    fun updateNotes(value: String) {
        val current = _uiState.value.previewState as? PreviewState.Ready ?: return
        _uiState.update { it.copy(previewState = current.copy(editedNotes = value)) }
    }

    fun updateCategory(categoryId: String) {
        val current = _uiState.value.previewState as? PreviewState.Ready ?: return
        _uiState.update { it.copy(previewState = current.copy(editedCategoryId = categoryId)) }
    }

    fun saveTransaction() {
        val ready = _uiState.value.previewState as? PreviewState.Ready ?: return
        val amount = ready.editedAmount.toDoubleOrNull() ?: return
        val categoryId = ready.editedCategoryId ?: return
        val idempotencyKey = ready.smsIdempotencyKey ?: "sms-${ready.messageId}"

        Log.d(TAG, "save: smsId=${ready.messageId} key=$idempotencyKey keySource=${if (ready.smsIdempotencyKey != null) "server" else "fallback-local-id"}")

        _uiState.update { it.copy(previewState = ready.copy(isSaving = true)) }
        viewModelScope.launch {
            try {
                val token = getJwtToken() ?: run {
                    _uiState.update { it.copy(previewState = PreviewState.Error("Not signed in")) }
                    return@launch
                }
                val saved = ApiClient.smsApi.createTransaction(
                    authorization = "Bearer $token",
                    request = CreateTransactionRequest(
                        amount = amount,
                        categoryId = categoryId,
                        date = ready.editedDate,
                        notes = ready.editedNotes,
                        type = ready.preview.type,
                        idempotencyKey = idempotencyKey,
                    ),
                )
                Log.d(TAG, "save response: transactionId=${saved.id} status=${saved.status}")
                prefs.addDismissedId(sender, ready.messageId)
                val filteredMessages = { state: SenderDetailUiState ->
                    state.messages.filter { it.id != ready.messageId }
                }
                if (saved.status == "duplicate") {
                    val msg = saved.message ?: "Duplicate SMS ignored: this transaction is already saved."
                    _uiState.update { state ->
                        state.copy(
                            previewState = PreviewState.Duplicate(msg),
                            messages = filteredMessages(state),
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            previewState = PreviewState.Saved,
                            messages = filteredMessages(state),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "save failed: ${e.message}")
                _uiState.update { it.copy(previewState = PreviewState.Error(e.message ?: "Save failed")) }
            }
        }
    }

    fun ignoreMessage(message: DeviceSmsMessage) {
        viewModelScope.launch {
            prefs.addDismissedId(sender, message.id)
            _uiState.update { state ->
                state.copy(messages = state.messages.filter { it.id != message.id })
            }
        }
    }

    fun importAll() {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            val token = getJwtToken() ?: run {
                _uiState.update { it.copy(importResult = SmsImportResult(error = "Not signed in")) }
                return@launch
            }
            _uiState.update { it.copy(isImporting = true) }
            val result = repo.importSender(sender, token) { done, total ->
                _uiState.update { it.copy(importProgress = "Batch $done / $total") }
            }
            val newCursor = prefs.getCursor(sender)
            prefs.pruneDismissedIds(sender, newCursor)
            val refreshed = repo.loadNewMessages(sender)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importProgress = null,
                    importResult = result,
                    messages = refreshed,
                )
            }
        }
    }

    fun dismissImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    fun setStartDate(millis: Long) {
        viewModelScope.launch {
            prefs.saveStartDate(sender, millis)
            _uiState.update { it.copy(startDateMillis = millis, isLoading = true) }
            val messages = repo.loadNewMessages(sender)
            _uiState.update { it.copy(messages = messages, isLoading = false) }
        }
    }

    fun clearStartDate() {
        viewModelScope.launch {
            prefs.clearStartDate(sender)
            _uiState.update { it.copy(startDateMillis = -1L, isLoading = true) }
            val messages = repo.loadNewMessages(sender)
            _uiState.update { it.copy(messages = messages, isLoading = false) }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewState = PreviewState.Idle) }
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

class SenderDetailViewModelFactory(
    private val application: Application,
    private val sender: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SenderDetailViewModel(application, sender) as T
    }
}
