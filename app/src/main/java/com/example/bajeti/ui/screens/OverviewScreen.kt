package com.example.bajeti.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bajeti.data.CategoryExpense
import com.example.bajeti.data.MonthStats
import com.example.bajeti.data.Transaction
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.ExpenseRed
import com.example.bajeti.ui.theme.IncomeGreen
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

internal fun formatFullDate(dateStr: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
    SimpleDateFormat("MMMM d, yyyy", Locale.US).format(parsed)
} catch (_: Exception) { dateStr }

internal fun formatDate(dateStr: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
    val todayCal = Calendar.getInstance()
    val txCal = Calendar.getInstance().also { it.time = parsed }
    val diffDays = ((todayCal.timeInMillis - txCal.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()
    when (diffDays) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(parsed)
    }
} catch (_: Exception) { dateStr }

internal fun formatCurrency(amount: Double, currencyCode: String = "USD"): String = try {
    val format = NumberFormat.getCurrencyInstance()
    format.currency = Currency.getInstance(currencyCode)
    format.format(amount)
} catch (_: Exception) {
    NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

internal fun categoryEmoji(category: String?): String = when {
    category == null -> "💳"
    category.contains("food", ignoreCase = true) || category.contains("restaurant", ignoreCase = true) -> "🍔"
    category.contains("shop", ignoreCase = true) -> "🛍️"
    category.contains("transport", ignoreCase = true) || category.contains("travel", ignoreCase = true) -> "🚗"
    category.contains("health", ignoreCase = true) || category.contains("medical", ignoreCase = true) -> "💊"
    category.contains("entertain", ignoreCase = true) -> "🎬"
    category.contains("utility", ignoreCase = true) || category.contains("bill", ignoreCase = true) -> "💡"
    else -> "💳"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(onSeeAll: () -> Unit = {}, viewModel: OverviewViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var isSavingTx by remember { mutableStateOf(false) }
    var txSaveError by remember { mutableStateOf<String?>(null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
        ) {
            stickyHeader { OverviewTopBar() }

            if (uiState.isLoading) {
                item { LoadingState() }
            } else if (uiState.error != null) {
                item { ErrorState(message = uiState.error!!, onRetry = viewModel::refresh) }
            } else {
                val summary = uiState.summary
                if (summary != null) {
                    item { BalanceCard(summary.currentMonth, uiState.currency) }
                    item { Spacer(Modifier.height(12.dp)) }
                    item { TopCategoriesCard(summary.expenseByCategory, uiState.currency) }
                    if (uiState.transactions.isNotEmpty()) {
                        item { Spacer(Modifier.height(12.dp)) }
                        item {
                            RecentTransactionsList(
                                transactions = uiState.transactions,
                                currency = uiState.currency,
                                onSeeAll = onSeeAll,
                                onDeleteTx = { id, onDone -> viewModel.deleteTransaction(id, onDone) },
                                onEditTx = { tx -> editingTx = tx },
                            )
                        }
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
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TealPrimary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun OverviewTopBar() {
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

@Composable
private fun BalanceCard(stats: MonthStats, currency: String) {
    val total = stats.income + stats.expenses
    val incomeSweep = if (total > 0) (360f * (stats.income / total)).toFloat() else 0f
    val expenseSweep = if (total > 0) 360f - incomeSweep else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(TealPrimary, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("This Month", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatCurrency(stats.balance, currency),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                AmountChip("+${formatCurrency(stats.income, currency)}", positive = true)
                Spacer(Modifier.height(6.dp))
                AmountChip("-${formatCurrency(stats.expenses, currency)}", positive = false)
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    val strokeWidth = 18.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    if (incomeSweep > 0f) {
                        drawArc(
                            color = IncomeGreen,
                            startAngle = -90f,
                            sweepAngle = incomeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        )
                    }
                    if (expenseSweep > 0f) {
                        drawArc(
                            color = ExpenseRed,
                            startAngle = -90f + incomeSweep,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PieLegendDot(IncomeGreen, "Income")
                    PieLegendDot(ExpenseRed, "Expense")
                }
            }
        }
    }
}

@Composable
private fun PieLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
    }
}

@Composable
private fun AmountChip(text: String, positive: Boolean) {
    val bg = if (positive) IncomeGreen.copy(alpha = 0.20f) else ExpenseRed.copy(alpha = 0.20f)
    val color = if (positive) IncomeGreen else ExpenseRed
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopCategoriesCard(categories: List<CategoryExpense>, currency: String) {
    val top5 = categories.sortedByDescending { it.amount }.take(5)
    if (top5.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Top Categories", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                top5.forEach { cat -> CategorySquareCard(cat, currency) }
            }
        }
    }
}

@Composable
private fun CategorySquareCard(cat: CategoryExpense, currency: String) {
    Card(
        modifier = Modifier.size(96.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(categoryEmoji(cat.category), fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                cat.category ?: "Other",
                fontSize = 10.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatCurrency(cat.amount, currency),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentTransactionsList(
    transactions: List<Transaction>,
    currency: String,
    onSeeAll: () -> Unit,
    onDeleteTx: (id: String, onDone: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onEditTx: (Transaction) -> Unit = {},
) {
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    selected?.let { tx ->
        TransactionDetailDialog(
            tx = tx,
            currency = currency,
            onDismiss = { selected = null; isDeleting = false; deleteError = null },
            onDeleteClick = {
                isDeleting = true
                deleteError = null
                onDeleteTx(tx.id) { success ->
                    isDeleting = false
                    if (success) selected = null else deleteError = "Failed to delete"
                }
            },
            onUpdateClick = {
                selected = null
                onEditTx(tx)
            },
            isDeleting = isDeleting,
            deleteError = deleteError,
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent Transactions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "See all",
                    fontSize = 13.sp,
                    color = TealPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onSeeAll),
                )
            }
            Spacer(Modifier.height(12.dp))
            transactions.forEachIndexed { index, tx ->
                if (index > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                TransactionRow(tx, currency, onClick = { selected = tx })
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, currency: String, onClick: () -> Unit) {
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
            .padding(vertical = 2.dp),
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
            Text(formatDate(tx.date), fontSize = 12.sp, color = TextSecondary)
        }
        Text(amountText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = amountColor)
    }
}

@Composable
internal fun TransactionDetailDialog(
    tx: Transaction,
    currency: String,
    onDismiss: () -> Unit,
    onDeleteClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    isDeleting: Boolean = false,
    deleteError: String? = null,
) {
    val displayName = tx.notes?.takeIf { it.isNotBlank() }
        ?: tx.smsCounterparty
        ?: tx.type.replaceFirstChar { it.uppercase() }
    val typeColor = when (tx.type) {
        "income" -> IncomeGreen
        "expense" -> ExpenseRed
        else -> TextSecondary
    }
    val amountText = when (tx.type) {
        "income" -> "+ ${formatCurrency(tx.amount, currency)}"
        "expense" -> "– ${formatCurrency(tx.amount, currency)}"
        else -> formatCurrency(tx.amount, currency)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Left type border
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(typeColor),
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(AppBackground, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Transaction details",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Amount
                    DialogDetailRow("Amount") {
                        Text(amountText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = typeColor)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Category
                    if (!tx.category.isNullOrBlank()) {
                        DialogDetailRow("Category") {
                            Text(tx.category, fontSize = 14.sp, color = TextPrimary)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Type chip
                    DialogDetailRow("Type") {
                        Box(
                            modifier = Modifier
                                .background(AppBackground, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(tx.type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Date
                    DialogDetailRow("Date") {
                        Text(formatFullDate(tx.date), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    if (!tx.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        DialogDetailRow("Notes") {
                            Text(tx.notes, fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.End)
                        }
                    }
                    if (!tx.smsCounterparty.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        DialogDetailRow("Counterparty") {
                            Text(tx.smsCounterparty, fontSize = 14.sp, color = TextPrimary)
                        }
                    }

                    if (deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError, fontSize = 12.sp, color = ExpenseRed)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, DividerColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        ) {
                            Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create SMS rule", fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = onDeleteClick,
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = onUpdateClick,
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Update", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DialogDetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        value()
    }
}
