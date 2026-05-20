package com.example.bajeti.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateTransactionRequest
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.data.Transaction
import com.example.bajeti.data.UpdateTransactionRequest
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.ExpenseRed
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Composable
internal fun TransactionFormDialog(
    existing: Transaction? = null,
    onCreate: ((CreateTransactionRequest) -> Unit)? = null,
    onEdit: ((UpdateTransactionRequest) -> Unit)? = null,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    saveError: String? = null,
) {
    val isEditMode = existing != null
    val context = LocalContext.current

    var amount by remember {
        mutableStateOf(
            existing?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var type by remember { mutableStateOf(existing?.type ?: "expense") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: todayString()) }
    var selectedCategoryId by remember { mutableStateOf(existing?.categoryId) }
    var selectedCategoryName by remember { mutableStateOf(existing?.category) }
    var categories by remember { mutableStateOf<List<SmsCategory>>(emptyList()) }
    var loadingCategories by remember { mutableStateOf(true) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val token = when (val r = Clerk.auth.getToken()) {
                is ClerkResult.Success -> r.value
                else -> null
            }
            if (token != null) {
                categories = ApiClient.summaryApi.getCategories("Bearer $token")
                if (existing?.categoryId == null && existing?.category != null) {
                    categories.find { it.name == existing.category }?.let {
                        selectedCategoryId = it.id
                        selectedCategoryName = it.name
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            loadingCategories = false
        }
    }

    LaunchedEffect(type) {
        val currentCatType = categories.find { it.id == selectedCategoryId }?.type
        if (selectedCategoryId != null && currentCatType != type) {
            selectedCategoryId = null
            selectedCategoryName = null
        }
    }

    if (showDatePicker) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance().also { cal ->
            runCatching { sdf.parse(date)?.let { d -> cal.time = d } }
        }
        DisposableEffect(Unit) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    date = "%04d-%02d-%02d".format(year, month + 1, day)
                    showDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            )
            dialog.setOnDismissListener { showDatePicker = false }
            dialog.show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }

    val filteredCategories = categories.filter { it.type == type }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isEditMode) "Edit Transaction" else "New Transaction",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                FormFieldLabel("Type")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("income", "expense", "transfer").forEach { t ->
                        val selected = type == t
                        FilterChip(
                            selected = selected,
                            onClick = { type = t },
                            label = { Text(t.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = AppBackground,
                                labelColor = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = DividerColor,
                                selectedBorderColor = TealPrimary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                FormFieldLabel("Amount")
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (amount.isEmpty()) {
                        Text("0.00", fontSize = 13.sp, color = TextSecondary)
                    }
                    BasicTextField(
                        value = amount,
                        onValueChange = { v -> if (v.matches(Regex("""^\d*\.?\d*$"""))) amount = v },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                        cursorBrush = SolidColor(TealPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                FormFieldLabel("Category")
                Spacer(Modifier.height(6.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppBackground, RoundedCornerShape(8.dp))
                            .clickable(enabled = !loadingCategories) { categoryDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                loadingCategories -> "Loading…"
                                selectedCategoryName != null -> selectedCategoryName!!
                                else -> "Select category"
                            },
                            fontSize = 13.sp,
                            color = if (selectedCategoryName != null) TextPrimary else TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (categoryDropdownExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceWhite),
                    ) {
                        if (filteredCategories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No categories", fontSize = 13.sp, color = TextSecondary) },
                                onClick = {},
                                enabled = false,
                            )
                        } else {
                            filteredCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name, fontSize = 13.sp, color = TextPrimary) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        selectedCategoryName = cat.name
                                        categoryDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                FormFieldLabel("Date")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground, RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(formatFullDate(date), fontSize = 13.sp, color = TextPrimary)
                }

                Spacer(Modifier.height(12.dp))

                FormFieldLabel("Notes")
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (notes.isEmpty()) {
                        Text("Optional notes…", fontSize = 13.sp, color = TextSecondary)
                    }
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                        cursorBrush = SolidColor(TealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    )
                }

                if (saveError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(saveError, fontSize = 12.sp, color = ExpenseRed)
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: return@Button
                            val catId = selectedCategoryId ?: return@Button
                            if (isEditMode) {
                                onEdit?.invoke(UpdateTransactionRequest(amount = amt, categoryId = catId, date = date, notes = notes, type = type))
                            } else {
                                onCreate?.invoke(CreateTransactionRequest(amount = amt, categoryId = catId, date = date, notes = notes, type = type, idempotencyKey = UUID.randomUUID().toString()))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        enabled = !isSaving && amount.isNotBlank() && selectedCategoryId != null,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (isEditMode) "Update" else "Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormFieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
}

private fun todayString(): String {
    val cal = Calendar.getInstance()
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}
