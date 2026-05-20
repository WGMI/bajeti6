package com.example.bajeti.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bajeti.data.SmsImportResult
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TealSurface
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.TextTertiary

@Composable
fun SmsImportScreen(
    onSenderClick: (String) -> Unit = {},
    viewModel: SmsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // Check permission immediately on first composition
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    // Re-check permission on every resume (handles return from system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionResult(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.showSenderPicker) {
        SenderPickerDialog(
            available = uiState.allSenders.filter { it !in uiState.watchedSenders },
            onSelect = viewModel::addSender,
            onDismiss = viewModel::dismissSenderPicker,
        )
    }

    uiState.lastResult?.let { result ->
        ImportResultDialog(
            sender = uiState.lastResultSender ?: "",
            result = result,
            onDismiss = viewModel::dismissResult,
        )
    }

    if (uiState.isCheckingPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = TealPrimary,
                strokeWidth = 3.dp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SMS Import", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (uiState.hasPermission) {
                    TextButton(
                        onClick = viewModel::showSenderPicker,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", color = TealPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        when {
            !uiState.hasPermission -> item {
                PermissionCard(
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            uiState.watchedSenders.isEmpty() -> item {
                EmptyState(
                    onAdd = viewModel::showSenderPicker,
                    modifier = Modifier.padding(24.dp),
                )
            }

            else -> {
                item {
                    Text(
                        "WATCHED SENDERS",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(uiState.watchedSenders.toList(), key = { it }) { sender ->
                    SenderCard(
                        sender = sender,
                        isSynced = (uiState.syncCursors[sender] ?: -1L) > -1L,
                        isImporting = sender in uiState.importingFor,
                        progressText = uiState.importProgress[sender],
                        onClick = { onSenderClick(sender) },
                        onImport = { viewModel.importAll(sender) },
                        onRemove = { viewModel.removeSender(sender) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(TealSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("SMS Permission Required", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow Bajeti to read SMS so it can detect transactions in your inbox.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            ) {
                Text("Grant Access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(TealSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Sms, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No senders added", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add an SMS sender to start importing transactions automatically.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Sender", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SenderCard(
    sender: String,
    isSynced: Boolean,
    isImporting: Boolean,
    progressText: String?,
    onClick: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(TealSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sender, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        if (isSynced) "Previously synced" else "Never synced",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove sender", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            if (isImporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = TealPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        progressText ?: "Importing…",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
            } else {
                Button(
                    onClick = onImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Import All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SenderPickerDialog(
    available: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, available) {
        if (query.isBlank()) available else available.filter { it.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Text(
                    "Add Sender",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Select an SMS sender to watch",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))

                if (available.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        placeholder = { Text("Search senders…", fontSize = 13.sp, color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
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
                }

                when {
                    available.isEmpty() -> Text(
                        "All available senders are already being watched.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    filtered.isEmpty() -> Text(
                        "No senders match \"$query\"",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(filtered) { sender ->
                            HorizontalDivider(color = DividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(sender) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(TealSurface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(sender, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerColor)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Cancel", color = TealPrimary)
                }
            }
        }
    }
}

@Composable
private fun ImportResultDialog(
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
                    if (result.created > 0 || result.duplicates > 0 || result.ignored > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Partial results before error:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        ResultRow("Created", result.created)
                        ResultRow("Duplicates", result.duplicates)
                        ResultRow("Ignored", result.ignored)
                    }
                } else {
                    ResultRow("Messages found", result.found)
                    ResultRow("Batches", result.batchCount)
                    Spacer(Modifier.height(6.dp))
                    ResultRow("Created", result.created)
                    ResultRow("Duplicates", result.duplicates)
                    ResultRow("Ignored", result.ignored)
                    if (result.failed > 0) ResultRow("Failed", result.failed)
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
private fun ResultRow(label: String, value: Int) {
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

