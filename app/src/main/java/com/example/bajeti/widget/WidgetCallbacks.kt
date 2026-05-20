package com.example.bajeti.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bajeti.data.SmsPreferences
import com.example.bajeti.data.smsDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

// Triggered by the ↺ button — marks the widget as loading and enqueues a one-shot refresh.
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[BajetiGlanceWidget.IS_LOADING_KEY] = true
                this.remove(BajetiGlanceWidget.ERROR_KEY)
            }
        }
        BajetiGlanceWidget().updateAll(context)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
        )
    }
}

// Triggered by the "Import" button next to a single sender row.
// Optimistically marks the sender as importing, then enqueues the background import.
class ImportSenderCallback : ActionCallback {

    companion object {
        val SENDER_KEY = ActionParameters.Key<String>("sender")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sender = parameters[SENDER_KEY] ?: return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val currentJson = this[BajetiGlanceWidget.IMPORTING_SENDERS_KEY] ?: "[]"
                val type = object : TypeToken<List<String>>() {}.type
                val current: List<String> = try {
                    Gson().fromJson(currentJson, type) ?: emptyList()
                } catch (_: Exception) { emptyList() }
                this[BajetiGlanceWidget.IMPORTING_SENDERS_KEY] =
                    Gson().toJson((current + sender).distinct())
            }
        }
        BajetiGlanceWidget().updateAll(context)

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ImportSenderWorker>()
                .setInputData(
                    Data.Builder().putString(ImportSenderWorker.SENDER_KEY, sender).build(),
                )
                .build(),
        )
    }
}

// Triggered by the "Import All" button — marks every watched sender as importing
// then enqueues a single worker that processes them all in sequence.
class ImportAllCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val smsPrefs = SmsPreferences(context.smsDataStore)
        val allSenders = smsPrefs.watchedSendersFlow.first().sorted()
        if (allSenders.isEmpty()) return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[BajetiGlanceWidget.IMPORTING_SENDERS_KEY] = Gson().toJson(allSenders)
            }
        }
        BajetiGlanceWidget().updateAll(context)

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ImportAllWorker>().build(),
        )
    }
}
