package com.example.bajeti.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.SmsImportResult
import com.example.bajeti.data.SmsPreferences
import com.example.bajeti.data.SmsRepository
import com.example.bajeti.data.smsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmsUiState(
    val isCheckingPermission: Boolean = true,
    val hasPermission: Boolean = false,
    val allSenders: List<String> = emptyList(),
    val watchedSenders: Set<String> = emptySet(),
    val syncCursors: Map<String, Long> = emptyMap(),
    val importingFor: Set<String> = emptySet(),
    val importProgress: Map<String, String> = emptyMap(),
    val lastResult: SmsImportResult? = null,
    val lastResultSender: String? = null,
    val showSenderPicker: Boolean = false,
)

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SmsPreferences(application.smsDataStore)
    private val repo = SmsRepository(application, prefs, ApiClient.smsApi)

    private val _uiState = MutableStateFlow(SmsUiState())
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.watchedSendersFlow.collect { senders ->
                val cursors = buildMap {
                    senders.forEach { sender -> put(sender, prefs.getCursor(sender)) }
                }
                _uiState.update { it.copy(watchedSenders = senders, syncCursors = cursors) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted, isCheckingPermission = false) }
        if (granted) refreshSenders()
    }

    private fun refreshSenders() {
        viewModelScope.launch {
            _uiState.update { it.copy(allSenders = repo.loadSenders()) }
        }
    }

    fun showSenderPicker() {
        refreshSenders()
        _uiState.update { it.copy(showSenderPicker = true) }
    }

    fun dismissSenderPicker() {
        _uiState.update { it.copy(showSenderPicker = false) }
    }

    fun addSender(sender: String) {
        viewModelScope.launch {
            prefs.addSender(sender)
            _uiState.update { it.copy(showSenderPicker = false) }
        }
    }

    fun removeSender(sender: String) {
        viewModelScope.launch {
            prefs.removeSender(sender)
        }
    }

    fun importAll(sender: String) {
        if (sender in _uiState.value.importingFor) return
        viewModelScope.launch {
            val token = getJwtToken() ?: run {
                _uiState.update {
                    it.copy(
                        lastResult = SmsImportResult(error = "Not signed in"),
                        lastResultSender = sender,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(importingFor = it.importingFor + sender) }

            val result = repo.importSender(sender, token) { done, total ->
                _uiState.update { state ->
                    state.copy(importProgress = state.importProgress + (sender to "Batch $done / $total"))
                }
            }

            val newCursor = prefs.getCursor(sender)
            _uiState.update { state ->
                state.copy(
                    importingFor = state.importingFor - sender,
                    importProgress = state.importProgress - sender,
                    syncCursors = state.syncCursors + (sender to newCursor),
                    lastResult = result,
                    lastResultSender = sender,
                )
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(lastResult = null, lastResultSender = null) }
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
