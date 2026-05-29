package com.example.bajeti.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.SmsPreferences
import com.example.bajeti.data.SmsRepository
import com.example.bajeti.data.smsDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BajetiWidget"

internal suspend fun refreshWidgetData(context: Context) {
    val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(BajetiGlanceWidget::class.java)
    Log.d(TAG, "refresh: starting for ${glanceIds.size} widget instance(s)")
    if (glanceIds.isEmpty()) return

    val currency = context
        .getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)
        .getString("currency", "USD") ?: "USD"

    val widgetPrefs = context.getSharedPreferences("bajeti_widget_settings", Context.MODE_PRIVATE)
    val showBalance = widgetPrefs.getBoolean("show_balance", true)
    val showBreakdown = widgetPrefs.getBoolean("show_breakdown", true)
    val showSms = widgetPrefs.getBoolean("show_sms", true)
    val textSize = widgetPrefs.getString("text_size", "medium") ?: "medium"
    Log.d(TAG, "refresh: settings → balance=$showBalance breakdown=$showBreakdown sms=$showSms textSize=$textSize")

    val token: String? = try {
        when (val r = Clerk.auth.getToken()) {
            is ClerkResult.Success -> r.value
            else -> null
        }
    } catch (_: Exception) { null }
    Log.d(TAG, "refresh: token ${if (token != null) "✓ obtained" else "✗ not signed in"}")

    var balance = 0.0
    var income = 0.0
    var expenses = 0.0
    // "auth" | "network" | null — stored in ERROR_KEY; widget interprets it for display
    var apiErrorType: String? = null

    if (token != null) {
        try {
            val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val summary = ApiClient.summaryApi.getSummary("Bearer $token", month)
            balance = summary.currentMonth.balance
            income = summary.currentMonth.income
            expenses = summary.currentMonth.expenses
        } catch (e: Exception) {
            apiErrorType = "network"
        }
    } else {
        apiErrorType = "auth"
    }

    val smsPrefs = SmsPreferences(context.smsDataStore)
    val watchedSenders = smsPrefs.watchedSendersFlow.first().sorted()
    Log.d(TAG, "refresh: ${watchedSenders.size} watched sender(s): $watchedSenders")
    val repo = SmsRepository(context, smsPrefs, ApiClient.smsApi)

    val smsCounts = mutableMapOf<String, Int>()
    for (sender in watchedSenders) {
        val count = try { repo.loadNewMessages(sender).size } catch (_: Exception) { 0 }
        smsCounts[sender] = count
        Log.d(TAG, "refresh: '$sender' → $count new message(s)")
    }

    for (glanceId in glanceIds) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[BajetiGlanceWidget.BALANCE_KEY] = balance.toFloat()
                this[BajetiGlanceWidget.INCOME_KEY] = income.toFloat()
                this[BajetiGlanceWidget.EXPENSES_KEY] = expenses.toFloat()
                this[BajetiGlanceWidget.CURRENCY_KEY] = currency
                this[BajetiGlanceWidget.SMS_COUNTS_KEY] = Gson().toJson(smsCounts)
                this[BajetiGlanceWidget.IS_LOADING_KEY] = false
                this[BajetiGlanceWidget.LAST_UPDATED_KEY] = System.currentTimeMillis()
                this[BajetiGlanceWidget.SHOW_BALANCE_KEY] = showBalance
                this[BajetiGlanceWidget.SHOW_BREAKDOWN_KEY] = showBreakdown
                this[BajetiGlanceWidget.SHOW_SMS_KEY] = showSms
                this[BajetiGlanceWidget.TEXT_SIZE_KEY] = textSize
                if (apiErrorType != null) {
                    this[BajetiGlanceWidget.ERROR_KEY] = apiErrorType
                } else {
                    this.remove(BajetiGlanceWidget.ERROR_KEY)
                }
                this.remove(BajetiGlanceWidget.IMPORTING_SENDERS_KEY)
            }
        }
    }
    Log.d(TAG, "refresh: state pushed to ${glanceIds.size} instance(s), calling updateAll")
    BajetiGlanceWidget().updateAll(context)
}

// ── Workers ───────────────────────────────────────────────────────────────────

class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "WidgetRefreshWorker: started")
        refreshWidgetData(applicationContext)
        Log.d(TAG, "WidgetRefreshWorker: complete")
        return Result.success()
    }
}

class ImportSenderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val SENDER_KEY = "sender"
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(SENDER_KEY) ?: return Result.failure()
        val context = applicationContext
        Log.d(TAG, "ImportSenderWorker: importing '$sender'")

        val token: String = try {
            when (val r = Clerk.auth.getToken()) {
                is ClerkResult.Success -> r.value
                else -> null
            }
        } catch (_: Exception) { null } ?: run {
            Log.w(TAG, "ImportSenderWorker: no token, aborting")
            refreshWidgetData(context)
            return Result.failure()
        }

        val smsPrefs = SmsPreferences(context.smsDataStore)
        val repo = SmsRepository(context, smsPrefs, ApiClient.smsApi)
        repo.importSender(sender, token) { done, total ->
            Log.d(TAG, "ImportSenderWorker: '$sender' batch $done/$total")
        }

        Log.d(TAG, "ImportSenderWorker: done, refreshing")
        refreshWidgetData(context)
        return Result.success()
    }
}

class ImportAllWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val smsPrefs = SmsPreferences(context.smsDataStore)
        val watchedSenders = smsPrefs.watchedSendersFlow.first().sorted()
        Log.d(TAG, "ImportAllWorker: starting for ${watchedSenders.size} sender(s)")

        if (watchedSenders.isEmpty()) {
            Log.d(TAG, "ImportAllWorker: no senders, nothing to do")
            refreshWidgetData(context)
            return Result.success()
        }

        val token: String = try {
            when (val r = Clerk.auth.getToken()) {
                is ClerkResult.Success -> r.value
                else -> null
            }
        } catch (_: Exception) { null } ?: run {
            Log.w(TAG, "ImportAllWorker: no token, aborting")
            refreshWidgetData(context)
            return Result.failure()
        }

        val repo = SmsRepository(context, smsPrefs, ApiClient.smsApi)
        watchedSenders.forEachIndexed { idx, sender ->
            Log.d(TAG, "ImportAllWorker: importing '$sender' (${idx + 1}/${watchedSenders.size})")
            try {
                repo.importSender(sender, token) { done, total ->
                    Log.d(TAG, "ImportAllWorker: '$sender' batch $done/$total")
                }
            } catch (e: Exception) {
                Log.w(TAG, "ImportAllWorker: '$sender' failed → ${e.message}")
            }
        }

        Log.d(TAG, "ImportAllWorker: all done, refreshing")
        refreshWidgetData(context)
        return Result.success()
    }
}
