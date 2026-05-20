package com.example.bajeti.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bajeti.data.Transaction
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.ExpenseRed
import com.example.bajeti.ui.theme.IncomeGreen
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "TransactionsScreen"

private fun formatDateLabel(dateStr: String): String = try {
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)!!
    )
} catch (_: Exception) { dateStr }

@Composable
fun TrendsScreen(viewModel: TransactionsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isSavingTx by remember { mutableStateOf(false) }
    var txSaveError by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var filtersExpanded by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        uiState.typeFilter != null,
        uiState.search.isNotBlank(),
        uiState.dateFrom != null,
        uiState.dateTo != null,
    ).count { it }

    selectedTx?.let { tx ->
        TransactionDetailDialog(
            tx = tx,
            currency = uiState.currency,
            onDismiss = { selectedTx = null; isDeleting = false; deleteError = null },
            onDeleteClick = {
                isDeleting = true
                deleteError = null
                viewModel.deleteTransaction(tx.id) { success ->
                    isDeleting = false
                    if (success) selectedTx = null else deleteError = "Failed to delete"
                }
            },
            onUpdateClick = {
                editingTx = tx
                selectedTx = null
            },
            isDeleting = isDeleting,
            deleteError = deleteError,
        )
    }

    editingTx?.let { tx ->
        TransactionFormDialog(
            existing = tx,
            onEdit = { req ->
                isSavingTx = true
                txSaveError = null
                viewModel.updateTransaction(tx.id, req) { success ->
                    isSavingTx = false
                    if (success) editingTx = null else txSaveError = "Failed to update transaction"
                }
            },
            onDismiss = { editingTx = null; txSaveError = null },
            isSaving = isSavingTx,
            saveError = txSaveError,
        )
    }

    if (showAddDialog) {
        TransactionFormDialog(
            onCreate = { req ->
                isSavingTx = true
                txSaveError = null
                viewModel.createTransaction(req) { success ->
                    isSavingTx = false
                    if (success) showAddDialog = false else txSaveError = "Failed to create transaction"
                }
            },
            onDismiss = { showAddDialog = false; txSaveError = null },
            isSaving = isSavingTx,
            saveError = txSaveError,
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = layoutInfo.totalItemsCount
            Log.d(TAG, "Scroll check — lastVisible=$lastVisible total=$total hasMore=${uiState.hasMore}")
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .collect { nearBottom ->
                Log.d(TAG, "snapshotFlow nearBottom=$nearBottom hasMore=${uiState.hasMore} isLoadingMore=${uiState.isLoadingMore}")
                if (nearBottom && uiState.hasMore && !uiState.isLoadingMore) {
                    Log.d(TAG, "Triggering loadMore()")
                    viewModel.loadMore()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bajeti", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(TealPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Screen title
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                Text("Transactions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        // Collapsible filter section
        item {
            FilterSection(
                uiState = uiState,
                searchText = searchText,
                filtersExpanded = filtersExpanded,
                activeFilterCount = activeFilterCount,
                onExpandToggle = { filtersExpanded = !filtersExpanded },
                onSearchChange = { v -> searchText = v; viewModel.setSearch(v) },
                onTypeFilter = viewModel::setTypeFilter,
                onDateFrom = viewModel::setDateFrom,
                onDateTo = viewModel::setDateTo,
                onClear = {
                    searchText = ""
                    viewModel.clearFilters()
                },
            )
        }

        // Full-screen loading
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            }
        } else if (uiState.error != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(uiState.error!!, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
                    Button(
                        onClick = viewModel::retry,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        } else if (uiState.transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No transactions found", fontSize = 14.sp, color = TextSecondary)
                }
            }
        } else {
            // Card top cap
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(SurfaceWhite, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                )
            }

            // Individual transaction rows
            itemsIndexed(uiState.transactions) { index, tx ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(SurfaceWhite),
                ) {
                    if (index > 0) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    TxRow(tx = tx, currency = uiState.currency, onClick = { selectedTx = tx })
                }
            }

            // Card bottom cap
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(SurfaceWhite, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .height(8.dp),
                )
            }
        }

        // Load-more spinner
        if (uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }

    FloatingActionButton(
        onClick = { showAddDialog = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 16.dp, end = 16.dp),
        containerColor = TealPrimary,
        contentColor = Color.White,
        shape = CircleShape,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
    }
    } // end Box
}

@Composable
private fun FilterSection(
    uiState: TransactionsUiState,
    searchText: String,
    filtersExpanded: Boolean,
    activeFilterCount: Int,
    onExpandToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onTypeFilter: (String?) -> Unit,
    onDateFrom: (String?) -> Unit,
    onDateTo: (String?) -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                Text(
                    "Filters",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (activeFilterCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(TealPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$activeFilterCount",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Clear", fontSize = 12.sp, color = ExpenseRed)
                    }
                }
                Icon(
                    if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Expandable body
            AnimatedVisibility(visible = filtersExpanded) {
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HorizontalDivider(color = DividerColor)

                    // Search
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Search", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppBackground, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchText.isEmpty()) {
                                        Text("Search transactions…", fontSize = 13.sp, color = TextSecondary)
                                    }
                                    BasicTextField(
                                        value = searchText,
                                        onValueChange = onSearchChange,
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                                        cursorBrush = SolidColor(TealPrimary),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (searchText.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onSearchChange("") },
                                    )
                                }
                            }
                        }
                    }

                    // Type
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Type", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(null to "All", "income" to "Income", "expense" to "Expense").forEach { (value, label) ->
                                val selected = uiState.typeFilter == value
                                FilterChip(
                                    selected = selected,
                                    onClick = { onTypeFilter(value) },
                                    label = { Text(label, fontSize = 12.sp) },
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
                    }

                    // Date range
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Date range", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DatePickerField(
                                label = "From",
                                value = uiState.dateFrom,
                                onDateSelected = onDateFrom,
                                modifier = Modifier.weight(1f),
                            )
                            DatePickerField(
                                label = "To",
                                value = uiState.dateTo,
                                onDateSelected = onDateTo,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    value: String?,
    onDateSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance().also { cal ->
            value?.let { runCatching { sdf.parse(it)?.let { d -> cal.time = d } } }
        }
        DisposableEffect(Unit) {
            val dialog = android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    onDateSelected("%04d-%02d-%02d".format(year, month + 1, day))
                    showPicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            )
            dialog.setOnDismissListener { showPicker = false }
            dialog.show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }

    Box(
        modifier = modifier
            .background(AppBackground, RoundedCornerShape(8.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (value != null) formatDateLabel(value) else label,
                fontSize = 13.sp,
                color = if (value != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear date",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = { onDateSelected(null) }),
                )
            }
        }
    }
}

@Composable
private fun TxRow(tx: Transaction, currency: String, onClick: () -> Unit) {
    val displayName = tx.notes?.takeIf { it.isNotBlank() }
        ?: tx.smsCounterparty
        ?: tx.type.replaceFirstChar { it.uppercase() }
    val amountColor = when (tx.type) {
        "income" -> IncomeGreen
        "expense" -> ExpenseRed
        else -> TextPrimary
    }
    val amountText = when (tx.type) {
        "income" -> "+${formatCurrency(tx.amount, currency)}"
        "expense" -> "-${formatCurrency(tx.amount, currency)}"
        else -> formatCurrency(tx.amount, currency)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(AppBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoryEmoji(tx.category ?: displayName), fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (!tx.category.isNullOrBlank()) {
                        append(tx.category)
                        append(" · ")
                    }
                    append(formatDate(tx.date))
                },
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(amountText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = amountColor)
    }
}
