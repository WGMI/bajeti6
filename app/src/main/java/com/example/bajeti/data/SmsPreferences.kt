package com.example.bajeti.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.smsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sms_prefs")

class SmsPreferences(private val dataStore: DataStore<Preferences>) {

    private val WATCHED_SENDERS = stringSetPreferencesKey("watched_senders")

    val watchedSendersFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[WATCHED_SENDERS] ?: emptySet()
    }

    suspend fun addSender(sender: String) {
        dataStore.edit { prefs ->
            prefs[WATCHED_SENDERS] = (prefs[WATCHED_SENDERS] ?: emptySet()) + sender
        }
    }

    suspend fun removeSender(sender: String) {
        dataStore.edit { prefs ->
            prefs[WATCHED_SENDERS] = (prefs[WATCHED_SENDERS] ?: emptySet()) - sender
        }
    }

    suspend fun getCursor(sender: String): Long =
        dataStore.data.first()[longPreferencesKey("cursor_${normalizeSender(sender)}")] ?: -1L

    suspend fun saveCursor(sender: String, id: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("cursor_${normalizeSender(sender)}")] = id
        }
    }

    suspend fun getStartDate(sender: String): Long =
        dataStore.data.first()[longPreferencesKey("start_date_${normalizeSender(sender)}")] ?: -1L

    suspend fun saveStartDate(sender: String, millis: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("start_date_${normalizeSender(sender)}")] = millis
        }
    }

    suspend fun clearStartDate(sender: String) {
        dataStore.edit { prefs ->
            prefs.remove(longPreferencesKey("start_date_${normalizeSender(sender)}"))
        }
    }
}

internal fun normalizeSender(sender: String): String {
    val trimmed = sender.trim()
    val digitsOnly = trimmed.filter { it.isDigit() }
    return if (digitsOnly.length >= 7 && trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
        // Phone numbers: strip to digits so +254… and 254… share a cursor
        digitsOnly
    } else {
        // Alphanumeric senders: preserve case so "Equity Bank" and "EQUITY BANK" stay separate
        trimmed.replace(Regex("[^a-zA-Z0-9_.]"), "_")
    }
}
