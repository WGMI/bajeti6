package com.example.bajeti.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.BajetiTheme

class WidgetConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val isConfigFlow = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (isConfigFlow) setResult(RESULT_CANCELED)

        val widgetPrefs = getSharedPreferences("bajeti_widget_settings", MODE_PRIVATE)

        setContent {
            BajetiTheme {
                var showBalance by remember {
                    mutableStateOf(widgetPrefs.getBoolean("show_balance", true))
                }
                var showBreakdown by remember {
                    mutableStateOf(widgetPrefs.getBoolean("show_breakdown", true))
                }
                var showSms by remember {
                    mutableStateOf(widgetPrefs.getBoolean("show_sms", true))
                }
                var textSize by remember {
                    mutableStateOf(widgetPrefs.getString("text_size", "medium") ?: "medium")
                }

                ConfigScreen(
                    showBalance = showBalance,
                    showBreakdown = showBreakdown,
                    showSms = showSms,
                    textSize = textSize,
                    onShowBalanceChange = {
                        showBalance = it
                        if (!it) showBreakdown = false
                    },
                    onShowBreakdownChange = { showBreakdown = it },
                    onShowSmsChange = { showSms = it },
                    onTextSizeChange = { textSize = it },
                    onSave = {
                        widgetPrefs.edit()
                            .putBoolean("show_balance", showBalance)
                            .putBoolean("show_breakdown", showBreakdown)
                            .putBoolean("show_sms", showSms)
                            .putString("text_size", textSize)
                            .apply()

                        WorkManager.getInstance(this@WidgetConfigActivity)
                            .enqueue(OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())

                        if (isConfigFlow) {
                            val result = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId,
                            )
                            setResult(RESULT_OK, result)
                        }
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    showBalance: Boolean,
    showBreakdown: Boolean,
    showSms: Boolean,
    textSize: String,
    onShowBalanceChange: (Boolean) -> Unit,
    onShowBreakdownChange: (Boolean) -> Unit,
    onShowSmsChange: (Boolean) -> Unit,
    onTextSizeChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TealPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose what to show on the widget",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = TextSecondary,
            )

            HorizontalDivider(color = DividerColor)

            SettingRow(
                title = "Current Balance",
                subtitle = "Monthly net balance amount",
                checked = showBalance,
                onCheckedChange = onShowBalanceChange,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = DividerColor)
            SettingRow(
                title = "Income & Expenses",
                subtitle = "Breakdown chips below the balance",
                checked = showBreakdown,
                onCheckedChange = onShowBreakdownChange,
                enabled = showBalance,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = DividerColor)
            SettingRow(
                title = "SMS Import Controls",
                subtitle = "New message counts and import buttons",
                checked = showSms,
                onCheckedChange = onShowSmsChange,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = DividerColor)
            TextSizePicker(
                selected = textSize,
                onSelect = onTextSizeChange,
            )
            HorizontalDivider(color = DividerColor)

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TextSizePicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Text Size", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("Adjust widget text and button size", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
        ) {
            listOf("small", "medium", "large").forEachIndexed { idx, size ->
                if (idx > 0) {
                    Box(Modifier.width(1.dp).height(34.dp).background(DividerColor))
                }
                val isSelected = selected == size
                Box(
                    modifier = Modifier
                        .clickable { onSelect(size) }
                        .background(if (isSelected) TealPrimary else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        size.replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else TextSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TealPrimary,
            ),
        )
    }
}
