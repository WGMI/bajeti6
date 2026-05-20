package com.example.bajeti.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Theme colours ─────────────────────────────────────────────────────────────
private val C_Teal = Color(0xFF1A3C40)
private val C_Surface = Color(0xFFFFFFFF)
private val C_TextPri = Color(0xFF1A1A2E)
private val C_TextSec = Color(0xFF6B7280)
private val C_Green = Color(0xFF22C55E)
private val C_Red = Color(0xFFEF4444)
private val C_GreenBg = Color(0xFFDCFCE7)
private val C_RedBg = Color(0xFFFEE2E2)
private val C_Divider = Color(0xFFE5E7EB)
private val C_White = Color(0xFFFFFFFF)

// ColorProvider(Color) is @RestrictTo(LIBRARY_GROUP) — a lint-level restriction, not a
// compiler restriction. Suppress it here so callers stay clean.
@Suppress("RestrictedApi")
private fun cp(color: Color) = ColorProvider(color)

// ── Widget ────────────────────────────────────────────────────────────────────

class BajetiGlanceWidget : GlanceAppWidget() {

    companion object {
        // Data keys
        val BALANCE_KEY = floatPreferencesKey("w_balance")
        val INCOME_KEY = floatPreferencesKey("w_income")
        val EXPENSES_KEY = floatPreferencesKey("w_expenses")
        val CURRENCY_KEY = stringPreferencesKey("w_currency")
        val SMS_COUNTS_KEY = stringPreferencesKey("w_sms_counts")
        val IS_LOADING_KEY = booleanPreferencesKey("w_is_loading")
        val LAST_UPDATED_KEY = longPreferencesKey("w_last_updated")
        val ERROR_KEY = stringPreferencesKey("w_error")
        val IMPORTING_SENDERS_KEY = stringPreferencesKey("w_importing_senders")
        // Settings keys — written by WidgetRefreshWorker from bajeti_widget_settings SharedPreferences
        val SHOW_BALANCE_KEY = booleanPreferencesKey("w_show_balance")
        val SHOW_BREAKDOWN_KEY = booleanPreferencesKey("w_show_breakdown")
        val SHOW_SMS_KEY = booleanPreferencesKey("w_show_sms")
        val TEXT_SIZE_KEY = stringPreferencesKey("w_text_size")

        // Responsive size breakpoints
        val SIZE_COMPACT = DpSize(250.dp, 100.dp)  // header + balance only
        val SIZE_FULL    = DpSize(250.dp, 200.dp)  // header + balance + SMS
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isLoading = prefs[IS_LOADING_KEY] ?: true
            val error = prefs[ERROR_KEY]
            val balance = prefs[BALANCE_KEY]?.toDouble() ?: 0.0
            val income = prefs[INCOME_KEY]?.toDouble() ?: 0.0
            val expenses = prefs[EXPENSES_KEY]?.toDouble() ?: 0.0
            val currency = prefs[CURRENCY_KEY] ?: "USD"
            val smsCounts = parseSmsCountsJson(prefs[SMS_COUNTS_KEY])
            val importingSenders = parseStringListJson(prefs[IMPORTING_SENDERS_KEY])
            val showBalance = prefs[SHOW_BALANCE_KEY] ?: true
            val showBreakdown = prefs[SHOW_BREAKDOWN_KEY] ?: true
            val showSms = prefs[SHOW_SMS_KEY] ?: true
            val textSize = prefs[TEXT_SIZE_KEY] ?: "medium"

            WidgetRoot(
                isLoading = isLoading,
                error = error,
                balance = balance,
                income = income,
                expenses = expenses,
                currency = currency,
                smsCounts = smsCounts,
                importingSenders = importingSenders,
                showBalance = showBalance,
                showBreakdown = showBreakdown,
                showSms = showSms,
                textSize = textSize,
            )
        }
    }
}

// ── Receiver ──────────────────────────────────────────────────────────────────

class BajetiWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = BajetiGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        schedulePeriodicRefresh(context)
    }

    private fun schedulePeriodicRefresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "bajeti_widget_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

private fun parseSmsCountsJson(json: String?): Map<String, Int> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        Gson().fromJson(json, type) ?: emptyMap()
    } catch (_: Exception) { emptyMap() }
}

private fun parseStringListJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun fmtCurrency(amount: Double, code: String): String = try {
    val fmt = NumberFormat.getCurrencyInstance()
    fmt.currency = Currency.getInstance(code)
    fmt.format(amount)
} catch (_: Exception) { NumberFormat.getCurrencyInstance(Locale.US).format(amount) }

// ── Text size scaling ─────────────────────────────────────────────────────────

private data class WTextSizes(
    val balance: Int,
    val label: Int,
    val chip: Int,
    val sender: Int,
    val status: Int,
    val header: Int,
)

private fun wTextSizes(size: String) = when (size) {
    "small" -> WTextSizes(18, 10, 10, 11, 10, 14)
    "large" -> WTextSizes(22, 12, 12, 13, 12, 16)
    else    -> WTextSizes(20, 11, 11, 12, 11, 15)
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
private fun WidgetRoot(
    isLoading: Boolean,
    error: String?,
    balance: Double,
    income: Double,
    expenses: Double,
    currency: String,
    smsCounts: Map<String, Int>,
    importingSenders: List<String>,
    showBalance: Boolean,
    showBreakdown: Boolean,
    showSms: Boolean,
    textSize: String,
) {
    val ts = wTextSizes(textSize)
    val isCompact = LocalSize.current.height < 140.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cp(C_Surface))
            .cornerRadius(16.dp),
    ) {
        WidgetHeader(ts)
        when {
            isLoading -> LoadingContent()
            else -> {
                if (showBalance) {
                    BalanceSection(balance, income, expenses, currency, error, showBreakdown, ts)
                }
                if (showSms && !isCompact) {
                    SmsSection(smsCounts, importingSenders, ts)
                }
                if (!showBalance && (!showSms || isCompact)) {
                    EmptySettingsHint()
                }
            }
        }
    }
}

// ── Header — title on the left, ⚙ and ↺ on the right via Box overlay ──────────

@Composable
private fun WidgetHeader(ts: WTextSizes) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cp(C_Teal))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "Bajeti",
            style = TextStyle(
                color = cp(C_White),
                fontSize = ts.header.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Box(
            modifier = GlanceModifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .clickable(actionStartActivity(Intent(context, WidgetConfigActivity::class.java)))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", style = TextStyle(color = cp(C_White), fontSize = ts.header.sp))
                }
                Spacer(GlanceModifier.width(4.dp))
                Box(
                    modifier = GlanceModifier
                        .clickable(actionRunCallback<RefreshCallback>())
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↺", style = TextStyle(color = cp(C_White), fontSize = ts.header.sp))
                }
            }
        }
    }
}

// ── Loading / empty-settings hint ────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Loading…", style = TextStyle(color = cp(C_TextSec), fontSize = 13.sp))
    }
}

@Composable
private fun EmptySettingsHint() {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "All sections hidden — tap ⚙ to configure",
            style = TextStyle(color = cp(C_TextSec), fontSize = 12.sp),
        )
    }
}

// ── Balance section ───────────────────────────────────────────────────────────

@Composable
private fun BalanceSection(
    balance: Double,
    income: Double,
    expenses: Double,
    currency: String,
    error: String?,
    showBreakdown: Boolean,
    ts: WTextSizes,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("This Month", style = TextStyle(color = cp(C_TextSec), fontSize = ts.label.sp))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            if (error != null) "—" else fmtCurrency(balance, currency),
            style = TextStyle(
                color = cp(C_TextPri),
                fontSize = ts.balance.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (error != null) {
            Spacer(GlanceModifier.height(2.dp))
            Text(error, style = TextStyle(color = cp(C_TextSec), fontSize = ts.label.sp))
        } else if (showBreakdown) {
            Spacer(GlanceModifier.height(6.dp))
            Row {
                AmountChip("+${fmtCurrency(income, currency)}", C_Green, C_GreenBg, ts)
                Spacer(GlanceModifier.width(6.dp))
                AmountChip("-${fmtCurrency(expenses, currency)}", C_Red, C_RedBg, ts)
            }
        }
    }
}

@Composable
private fun AmountChip(text: String, textColor: Color, bgColor: Color, ts: WTextSizes) {
    Box(
        modifier = GlanceModifier
            .background(cp(bgColor))
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = TextStyle(
                color = cp(textColor),
                fontSize = ts.chip.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ── SMS section ───────────────────────────────────────────────────────────────

@Composable
private fun SmsSection(smsCounts: Map<String, Int>, importingSenders: List<String>, ts: WTextSizes) {
    Spacer(
        GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(cp(C_Divider)),
    )
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Section header: "SMS Import" label left, "Import All" button right.
        val isAnyImporting = importingSenders.isNotEmpty()
        val hasNewMessages = smsCounts.values.any { it > 0 }

        Box(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "SMS Import",
                style = TextStyle(
                    color = cp(C_TextSec),
                    fontSize = ts.label.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (smsCounts.isNotEmpty() && !isAnyImporting && hasNewMessages) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<ImportAllCallback>())
                            .background(cp(C_Teal))
                            .cornerRadius(6.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Import All",
                            style = TextStyle(
                                color = cp(C_White),
                                fontSize = ts.status.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        if (smsCounts.isEmpty()) {
            Text(
                "No senders configured — open app to add",
                style = TextStyle(color = cp(C_TextSec), fontSize = ts.label.sp),
            )
        } else {
            val visible = smsCounts.entries.toList().take(4)
            val overflow = smsCounts.size - visible.size
            visible.forEachIndexed { idx, (sender, count) ->
                if (idx > 0) Spacer(GlanceModifier.height(5.dp))
                SmsRow(sender, count, sender in importingSenders, ts)
            }
            if (overflow > 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "+$overflow more — open app",
                    style = TextStyle(color = cp(C_TextSec), fontSize = ts.status.sp),
                )
            }
        }
    }
}

// Box overlay: sender name left (padded to avoid overlap), action pinned right.
@Composable
private fun SmsRow(sender: String, count: Int, isImporting: Boolean, ts: WTextSizes) {
    Box(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            sender,
            modifier = GlanceModifier.fillMaxWidth().padding(end = 96.dp),
            style = TextStyle(color = cp(C_TextPri), fontSize = ts.sender.sp),
            maxLines = 1,
        )
        Box(
            modifier = GlanceModifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            when {
                isImporting -> Text(
                    "Importing…",
                    style = TextStyle(color = cp(C_TextSec), fontSize = ts.status.sp),
                )
                count > 0 -> Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        "$count new",
                        style = TextStyle(color = cp(C_TextSec), fontSize = ts.status.sp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .clickable(
                                actionRunCallback<ImportSenderCallback>(
                                    actionParametersOf(ImportSenderCallback.SENDER_KEY to sender),
                                ),
                            )
                            .background(cp(C_Teal))
                            .cornerRadius(6.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "Import",
                            style = TextStyle(
                                color = cp(C_White),
                                fontSize = ts.status.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                else -> Text(
                    "✓ Up to date",
                    style = TextStyle(color = cp(C_Green), fontSize = ts.status.sp),
                )
            }
        }
    }
}
