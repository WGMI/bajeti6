package com.example.bajeti.ui.screens

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bajeti.data.DeviceSmsMessage
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.data.SmsImportResult
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.ExpenseRed
import com.example.bajeti.ui.theme.IncomeGreen
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TealSurface
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions

private fun formatMessageTime(timestampMs: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val now = Calendar.getInstance()
    val sameDay = msgCal.get(Calendar.DATE) == now.get(Calendar.DATE) &&
            msgCal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            msgCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    return if (sameDay) {
        SimpleDateFormat("h:mm a", Locale.US).format(msgCal.time)
    } else {
        SimpleDateFormat("MMM d • h:mm a", Locale.US).format(msgCal.time)
    }
}

@Composable
fun SenderDetailScreen(
    sender: String,
    onBack: () -> Unit,
    viewModel: SenderDetailViewModel = viewModel(
        factory = SenderDetailViewModelFactory(
            application = LocalContext.current.applicationContext as Application,
            sender = sender,
        )
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        SystemDatePickerDialog(
            initialMillis = uiState.startDateMillis.takeIf { it > -1L },
            onConfirm = { millis ->
                viewModel.setStartDate(millis)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // Auto-dismiss after a successful save (duplicate shows its own dialog instead)
    LaunchedEffect(uiState.previewState) {
        if (uiState.previewState is PreviewState.Saved) {
            viewModel.dismissPreview()
        }
    }

    when (val state = uiState.previewState) {
        is PreviewState.Ignored -> IgnoredDialog(
            reason = state.reason,
            onDismiss = viewModel::dismissPreview,
        )
        is PreviewState.Ready -> PreviewEditorDialog(
            state = state,
            categories = uiState.categories,
            onAmountChange = viewModel::updateAmount,
            onDateChange = viewModel::updateDate,
            onNotesChange = viewModel::updateNotes,
            onCategoryChange = viewModel::updateCategory,
            onSave = viewModel::saveTransaction,
            onDismiss = viewModel::dismissPreview,
        )
        is PreviewState.Duplicate -> DuplicateDialog(
            message = state.message,
            onDismiss = viewModel::dismissPreview,
        )
        is PreviewState.Error -> ErrorDialog(
            message = state.message,
            onDismiss = viewModel::dismissPreview,
        )
        else -> Unit
    }

    uiState.importResult?.let { result ->
        ImportSummaryDialog(
            sender = uiState.sender,
            result = result,
            onDismiss = viewModel::dismissImportResult,
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(sender, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (!uiState.isLoading) {
                    val count = uiState.messages.size
                    Text(
                        if (count > 0) "$count new message${if (count != 1) "s" else ""}" else "All caught up",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
            if (uiState.isImporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(uiState.importProgress ?: "Importing…", fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                TextButton(
                    onClick = viewModel::importAll,
                    enabled = !uiState.isLoading,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Import All", color = TealPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        HorizontalDivider(color = DividerColor)

        // Start date filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .clickableNoRipple { showDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (uiState.startDateMillis > -1L)
                    "From: ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Calendar.getInstance().apply { timeInMillis = uiState.startDateMillis }.time)}"
                else "All time · tap to set start date",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (uiState.startDateMillis > -1L) {
                IconButton(onClick = viewModel::clearStartDate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear start date", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(color = DividerColor)

        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TealPrimary)
            }

            uiState.messages.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("You're all caught up", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("No new messages for this sender", fontSize = 13.sp, color = TextSecondary)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        isLoadingPreview = uiState.previewState is PreviewState.Loading &&
                                (uiState.previewState as PreviewState.Loading).messageId == message.id,
                        onPreview = { viewModel.previewMessage(message) },
                        onIgnore = { viewModel.ignoreMessage(message) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: DeviceSmsMessage,
    isLoadingPreview: Boolean,
    onPreview: () -> Unit,
    onIgnore: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                formatMessageTime(message.timestampMillis),
                fontSize = 11.sp,
                color = TextTertiary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message.body,
                fontSize = 13.sp,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (isLoadingPreview) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Previewing…", fontSize = 13.sp, color = TextSecondary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Preview", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onIgnore,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Ignore", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IgnoredDialog(reason: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Can't parse", fontWeight = FontWeight.SemiBold) },
        text = { Text(reason, fontSize = 14.sp, color = TextPrimary) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = TealPrimary) }
        },
        containerColor = SurfaceWhite,
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error", fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 14.sp, color = TextPrimary) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = TealPrimary) }
        },
        containerColor = SurfaceWhite,
    )
}

@Composable
private fun DuplicateDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Already saved", fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 14.sp, color = TextPrimary) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = TealPrimary) }
        },
        containerColor = SurfaceWhite,
    )
}

@Composable
private fun PreviewEditorDialog(
    state: PreviewState.Ready,
    categories: List<SmsCategory>,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val typeColor = when (state.preview.type) {
        "income" -> IncomeGreen
        "expense" -> ExpenseRed
        else -> TextSecondary
    }
    val filteredCategories = categories.filter { it.type == state.preview.type }
    val selectedCategoryName = filteredCategories
        .firstOrNull { it.id == state.editedCategoryId }?.name ?: ""

    val isSaving = state.isSaving
    val canSave = !isSaving &&
            state.editedAmount.toDoubleOrNull()?.let { it > 0 } == true &&
            state.editedDate.isNotBlank() &&
            state.editedCategoryId != null

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Review Transaction", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (state.preview.type == "income") IncomeGreen.copy(alpha = 0.12f)
                                else ExpenseRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            state.preview.type.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = typeColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.editedAmount,
                    onValueChange = onAmountChange,
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(),
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.editedDate,
                    onValueChange = onDateChange,
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(),
                )

                Spacer(Modifier.height(10.dp))

                CategoryDropdown(
                    selectedName = selectedCategoryName,
                    categories = filteredCategories,
                    required = state.needsCategory && state.editedCategoryId == null,
                    enabled = !isSaving,
                    onSelect = onCategoryChange,
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.editedNotes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    enabled = !isSaving,
                    maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(),
                )

                if (state.needsCategory && state.editedCategoryId == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Category is required", fontSize = 11.sp, color = ExpenseRed)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = SurfaceWhite,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save Transaction", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel", fontSize = 14.sp, color = if (isSaving) TextSecondary else TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun CategoryDropdown(
    selectedName: String,
    categories: List<SmsCategory>,
    required: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text("Category") },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (enabled) TealPrimary else TextSecondary)
            },
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = TealPrimary,
                unfocusedIndicatorColor = if (required) ExpenseRed else DividerColor,
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                disabledContainerColor = SurfaceWhite,
                disabledIndicatorColor = if (required) ExpenseRed else DividerColor,
                disabledTextColor = TextPrimary,
                disabledLabelColor = TextSecondary,
                disabledTrailingIconColor = TextSecondary,
                cursorColor = TealPrimary,
            ),
        )
        // Invisible overlay to capture clicks (OutlinedTextField with readOnly still intercepts taps)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (enabled) Modifier.background(androidx.compose.ui.graphics.Color.Transparent) else Modifier)
                .let { if (enabled) it.then(Modifier.clickableNoRipple { expanded = true }) else it }
        )
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name, fontSize = 14.sp) },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = TealPrimary,
    unfocusedIndicatorColor = DividerColor,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite,
    cursorColor = TealPrimary,
    disabledContainerColor = SurfaceWhite,
    disabledIndicatorColor = DividerColor,
)

@Composable
private fun SystemDatePickerDialog(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val today = Calendar.getInstance()
    val init = initialMillis?.let { Calendar.getInstance().apply { timeInMillis = it } } ?: today
    val dialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onConfirm(cal.timeInMillis)
            },
            init.get(Calendar.YEAR),
            init.get(Calendar.MONTH),
            init.get(Calendar.DAY_OF_MONTH),
        )
    }
    DisposableEffect(dialog) {
        dialog.setOnCancelListener { onDismiss() }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            dialog.setOnDismissListener(null)
            if (dialog.isShowing) dialog.dismiss()
        }
    }
}

@Composable
private fun ImportSummaryDialog(
    sender: String,
    result: SmsImportResult,
    onDismiss: () -> Unit,
) {
    val isError = result.error != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isError) "Import Error" else "Import Complete",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(sender, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                if (isError) {
                    Text(result.error ?: "Unknown error", fontSize = 13.sp, color = TextPrimary)
                } else {
                    SummaryRow("Messages found", result.found)
                    SummaryRow("Created", result.created)
                    SummaryRow("Duplicates", result.duplicates)
                    SummaryRow("Ignored", result.ignored)
                    if (result.failed > 0) SummaryRow("Failed", result.failed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = TealPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = SurfaceWhite,
    )
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value.toString(), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
