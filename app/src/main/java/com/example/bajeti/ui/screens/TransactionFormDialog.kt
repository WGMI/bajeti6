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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import com.example.bajeti.data.Account
import com.example.bajeti.data.ApiClient
import com.example.bajeti.data.CreateCategoryRequest
import com.example.bajeti.data.CreateTransactionRequest
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.data.SmsPreviewRequest
import com.example.bajeti.data.Transaction
import com.example.bajeti.data.UpdateTransactionRequest
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.ExpenseRed
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    // ── Form state ──────────────────────────────────────────────────────────
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
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loadingAccounts by remember { mutableStateOf(true) }
    // For income/expense — null means "Wallet (default)"
    var selectedAccountId by remember {
        mutableStateOf(if (existing?.type != "transfer") existing?.accountId else null)
    }
    var selectedAccountName by remember {
        mutableStateOf(if (existing?.type != "transfer") existing?.accountName else null)
    }
    // For transfer — derive from/to from transferLeg + counterAccount
    var selectedFromAccountId by remember {
        mutableStateOf(
            when {
                existing?.type == "transfer" && existing.transferLeg == "out" -> existing.accountId
                existing?.type == "transfer" && existing.transferLeg == "in" -> existing.counterAccountId
                else -> null
            }
        )
    }
    var selectedFromAccountName by remember {
        mutableStateOf(
            when {
                existing?.type == "transfer" && existing.transferLeg == "out" -> existing.accountName
                existing?.type == "transfer" && existing.transferLeg == "in" -> existing.counterAccountName
                else -> null
            }
        )
    }
    var selectedToAccountId by remember {
        mutableStateOf(
            when {
                existing?.type == "transfer" && existing.transferLeg == "out" -> existing.counterAccountId
                existing?.type == "transfer" && existing.transferLeg == "in" -> existing.accountId
                else -> null
            }
        )
    }
    var selectedToAccountName by remember {
        mutableStateOf(
            when {
                existing?.type == "transfer" && existing.transferLeg == "out" -> existing.counterAccountName
                existing?.type == "transfer" && existing.transferLeg == "in" -> existing.accountName
                else -> null
            }
        )
    }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showFromAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // ── Paste SMS state ─────────────────────────────────────────────────────
    var showPasteSms by remember { mutableStateOf(false) }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    // ── Category picker state ───────────────────────────────────────────────
    var showCategoryPicker by remember { mutableStateOf(false) }

    // ── Load categories + accounts ───────────────────────────────────────────
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
                accounts = ApiClient.summaryApi.getAccounts("Bearer $token")
            }
        } catch (_: Exception) {
        } finally {
            loadingCategories = false
            loadingAccounts = false
        }
    }

    // Clear category and account selections when type changes
    LaunchedEffect(type) {
        val currentCatType = categories.find { it.id == selectedCategoryId }?.type
        if (selectedCategoryId != null && currentCatType != type) {
            selectedCategoryId = null
            selectedCategoryName = null
        }
        if (type == "transfer") {
            selectedAccountId = null
            selectedAccountName = null
        } else {
            selectedFromAccountId = null
            selectedFromAccountName = null
            selectedToAccountId = null
            selectedToAccountName = null
        }
    }

    // ── Date picker ─────────────────────────────────────────────────────────
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

    // ── Paste SMS dialog ────────────────────────────────────────────────────
    if (showPasteSms) {
        PasteSmsDialog(
            isLoading = isPreviewing,
            error = previewError,
            onConfirm = { smsText ->
                isPreviewing = true
                previewError = null
                scope.launch {
                    try {
                        val token = when (val r = Clerk.auth.getToken()) {
                            is ClerkResult.Success -> r.value
                            else -> null
                        }
                        if (token == null) {
                            previewError = "Not signed in"
                            isPreviewing = false
                            return@launch
                        }
                        val response = ApiClient.smsApi.previewSms(
                            authorization = "Bearer $token",
                            request = SmsPreviewRequest(
                                message = smsText,
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                        when (response.status) {
                            "ignored" -> {
                                previewError = response.reason ?: "Could not parse this SMS as a transaction"
                            }
                            "ready", "needs_category" -> {
                                val preview = response.preview!!
                                // Populate form fields
                                amount = preview.amount.toBigDecimal().stripTrailingZeros().toPlainString()
                                type = preview.type
                                date = preview.date
                                notes = preview.notes
                                if (preview.categoryId != null) {
                                    val cat = categories.find { it.id == preview.categoryId }
                                    selectedCategoryId = preview.categoryId
                                    selectedCategoryName = cat?.name
                                } else {
                                    selectedCategoryId = null
                                    selectedCategoryName = null
                                }
                                showPasteSms = false
                                previewError = null
                            }
                            else -> {
                                previewError = "Unexpected response (${response.status})"
                            }
                        }
                    } catch (e: Exception) {
                        previewError = e.message ?: "Preview failed"
                    } finally {
                        isPreviewing = false
                    }
                }
            },
            onDismiss = {
                showPasteSms = false
                previewError = null
            },
        )
    }

    // ── Category picker dialog ──────────────────────────────────────────────
    val filteredCategories = categories.filter { it.type == type }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = filteredCategories,
            currentType = type,
            onSelect = { cat ->
                selectedCategoryId = cat.id
                selectedCategoryName = cat.name
                showCategoryPicker = false
            },
            onCreateNew = { name, onResult ->
                scope.launch {
                    try {
                        val token = when (val r = Clerk.auth.getToken()) {
                            is ClerkResult.Success -> r.value
                            else -> null
                        } ?: run { onResult(null); return@launch }
                        val newCat = ApiClient.summaryApi.createCategory(
                            "Bearer $token",
                            CreateCategoryRequest(name = name.trim(), type = type),
                        )
                        categories = (categories + newCat)
                            .sortedWith(compareBy({ it.type }, { it.name }))
                        onResult(newCat)
                    } catch (_: Exception) {
                        onResult(null)
                    }
                }
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // ── Account picker dialogs ──────────────────────────────────────────────
    if (showAccountPicker) {
        AccountPickerDialog(
            accounts = accounts,
            onSelect = { acc ->
                selectedAccountId = acc?.id
                selectedAccountName = acc?.name
                showAccountPicker = false
            },
            allowClear = selectedAccountId != null,
            onDismiss = { showAccountPicker = false },
        )
    }
    if (showFromAccountPicker) {
        AccountPickerDialog(
            accounts = accounts.filter { it.id != selectedToAccountId },
            onSelect = { acc ->
                selectedFromAccountId = acc?.id
                selectedFromAccountName = acc?.name
                showFromAccountPicker = false
            },
            allowClear = false,
            onDismiss = { showFromAccountPicker = false },
        )
    }
    if (showToAccountPicker) {
        AccountPickerDialog(
            accounts = accounts.filter { it.id != selectedFromAccountId },
            onSelect = { acc ->
                selectedToAccountId = acc?.id
                selectedToAccountName = acc?.name
                showToAccountPicker = false
            },
            allowClear = false,
            onDismiss = { showToAccountPicker = false },
        )
    }

    // ── Main dialog ─────────────────────────────────────────────────────────
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
                // Header row: title + paste SMS button + close
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
                    // Paste SMS icon button
                    IconButton(
                        onClick = {
                            previewError = null
                            showPasteSms = true
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Sms,
                            contentDescription = "Paste SMS",
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Type chips
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

                // Amount
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

                // Category — opens searchable dialog instead of dropdown
                FormFieldLabel("Category")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground, RoundedCornerShape(8.dp))
                        .clickable(enabled = !loadingCategories) { showCategoryPicker = true }
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
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                }

                Spacer(Modifier.height(12.dp))

                // Account picker(s) — single for income/expense, from/to for transfer
                if (type == "transfer") {
                    FormFieldLabel("From Account")
                    Spacer(Modifier.height(6.dp))
                    AccountPickerRow(
                        label = selectedFromAccountName ?: "Select account",
                        hasValue = selectedFromAccountName != null,
                        isLoading = loadingAccounts,
                        onClick = { showFromAccountPicker = true },
                    )
                    Spacer(Modifier.height(12.dp))
                    FormFieldLabel("To Account")
                    Spacer(Modifier.height(6.dp))
                    AccountPickerRow(
                        label = selectedToAccountName ?: "Select account",
                        hasValue = selectedToAccountName != null,
                        isLoading = loadingAccounts,
                        onClick = { showToAccountPicker = true },
                    )
                } else {
                    FormFieldLabel("Account")
                    Spacer(Modifier.height(6.dp))
                    AccountPickerRow(
                        label = selectedAccountName ?: "Wallet (default)",
                        hasValue = selectedAccountName != null,
                        isLoading = loadingAccounts,
                        onClick = { if (accounts.isNotEmpty()) showAccountPicker = true },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Date
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

                // Notes
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
                                onEdit?.invoke(
                                    UpdateTransactionRequest(
                                        amount = amt,
                                        categoryId = catId,
                                        date = date,
                                        notes = notes,
                                        type = type,
                                        accountId = if (type != "transfer") selectedAccountId else null,
                                        fromAccountId = if (type == "transfer") selectedFromAccountId else null,
                                        toAccountId = if (type == "transfer") selectedToAccountId else null,
                                    )
                                )
                            } else {
                                onCreate?.invoke(
                                    CreateTransactionRequest(
                                        amount = amt,
                                        categoryId = catId,
                                        date = date,
                                        notes = notes,
                                        type = type,
                                        idempotencyKey = UUID.randomUUID().toString(),
                                        accountId = if (type != "transfer") selectedAccountId else null,
                                        fromAccountId = if (type == "transfer") selectedFromAccountId else null,
                                        toAccountId = if (type == "transfer") selectedToAccountId else null,
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        enabled = !isSaving && amount.isNotBlank() && selectedCategoryId != null &&
                            (type != "transfer" || (selectedFromAccountId != null && selectedToAccountId != null)),
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

// ─────────────────────────────────────────────────────────────────────────────
// Paste SMS dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasteSmsDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Sms,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Paste SMS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (!isLoading) onDismiss() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste your M-PESA or bank SMS to auto-fill the form.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))

                // SMS text area
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (!isLoading) text = it },
                    placeholder = { Text("Paste SMS here…", fontSize = 13.sp, color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    enabled = !isLoading,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = TealPrimary,
                        unfocusedIndicatorColor = DividerColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        cursorColor = TealPrimary,
                        disabledContainerColor = SurfaceWhite,
                        disabledIndicatorColor = DividerColor,
                    ),
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, fontSize = 12.sp, color = ExpenseRed)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (!isLoading) onDismiss() },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { if (text.isNotBlank()) onConfirm(text) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        enabled = !isLoading && text.isNotBlank(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Preview")
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category picker dialog (searchable + inline create)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryPickerDialog(
    categories: List<SmsCategory>,
    currentType: String,
    onSelect: (SmsCategory) -> Unit,
    onCreateNew: (name: String, onResult: (SmsCategory?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // Create sub-dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    val filtered = remember(query, categories) {
        if (query.isBlank()) categories
        else categories.filter { it.name.contains(query, ignoreCase = true) }
    }

    // New category AlertDialog — appears on top of the picker
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) { showCreateDialog = false; createError = null } },
            title = {
                Column {
                    Text("New Category", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Type: ${currentType.replaceFirstChar { it.uppercase() }}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { if (!isCreating) newCatName = it },
                        label = { Text("Category name") },
                        singleLine = true,
                        enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = TealPrimary,
                            unfocusedIndicatorColor = DividerColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            cursorColor = TealPrimary,
                        ),
                    )
                    if (createError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(createError!!, fontSize = 12.sp, color = ExpenseRed)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCatName.isBlank() || isCreating) return@TextButton
                        isCreating = true
                        createError = null
                        onCreateNew(newCatName) { newCat ->
                            isCreating = false
                            if (newCat != null) {
                                showCreateDialog = false
                                newCatName = ""
                                createError = null
                                onSelect(newCat) // select immediately and close picker
                            } else {
                                createError = "Failed to create category. Try again."
                            }
                        }
                    },
                    enabled = !isCreating && newCatName.isNotBlank(),
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Create", color = TealPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!isCreating) {
                        showCreateDialog = false
                        createError = null
                    }
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceWhite,
        )
    }

    // Main picker dialog
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Choose Category",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = { Text("Search…", fontSize = 13.sp, color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = TealPrimary,
                        unfocusedIndicatorColor = DividerColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        cursorColor = TealPrimary,
                    ),
                )

                Spacer(Modifier.height(8.dp))

                // Category list
                when {
                    categories.isEmpty() -> {
                        Text(
                            "No categories yet",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    filtered.isEmpty() -> {
                        Text(
                            "No matches for \"$query\"",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(filtered, key = { it.id }) { cat ->
                                HorizontalDivider(color = DividerColor)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(cat) }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        cat.name,
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                // New category button
                HorizontalDivider(color = DividerColor)
                TextButton(
                    onClick = {
                        newCatName = ""
                        createError = null
                        showCreateDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Category", color = TealPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Account picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountPickerRow(
    label: String,
    hasValue: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground, RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isLoading) "Loading…" else label,
            fontSize = 13.sp,
            color = if (hasValue) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun AccountPickerDialog(
    accounts: List<Account>,
    allowClear: Boolean,
    onSelect: (Account?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Choose Account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (accounts.isEmpty()) {
                    Text(
                        "No accounts found",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        if (allowClear) {
                            item {
                                HorizontalDivider(color = DividerColor)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(null) }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Wallet (default)", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        items(accounts, key = { it.id }) { acc ->
                            HorizontalDivider(color = DividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(acc) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(acc.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                if (acc.isDefault) {
                                    Text("default", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = DividerColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormFieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
}

private fun todayString(): String {
    val cal = Calendar.getInstance()
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}
