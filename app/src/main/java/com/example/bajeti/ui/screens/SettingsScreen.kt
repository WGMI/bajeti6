package com.example.bajeti.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bajeti.data.SmsCategory
import com.example.bajeti.widget.WidgetConfigActivity
import com.example.bajeti.ui.theme.AppBackground
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TealSurface
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.TextTertiary

private val LogoutRed = Color(0xFFEF4444)
private val LogoutRedLight = Color(0xFFFEF2F2)

private data class DeleteConflictState(
    val category: SmsCategory,
    val transactionCount: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── SMS permission ──────────────────────────────────────────────────────
    var hasSmsPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasSmsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }
    var showSmsRationale by remember { mutableStateOf(false) }

    // ── Category manager ────────────────────────────────────────────────────
    // null = closed, "income" or "expense" = open for that type
    var categoryManagerType by remember { mutableStateOf<String?>(null) }

    // ── SMS rationale dialog ────────────────────────────────────────────────
    if (showSmsRationale) {
        AlertDialog(
            onDismissRequest = { showSmsRationale = false },
            title = { Text("SMS Access", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Bajeti reads your inbox to automatically detect M-PESA and bank transactions. " +
                    "Messages are processed on-device and never shared.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSmsRationale = false
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }) { Text("Allow", color = TealPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showSmsRationale = false }) {
                    Text("Not now", color = TextSecondary)
                }
            },
        )
    }

    // ── Category manager dialog ─────────────────────────────────────────────
    categoryManagerType?.let { type ->
        CategoryManagerDialog(
            type = type,
            categories = uiState.categories.filter { it.type == type },
            isSaving = uiState.isSavingCategory,
            onCreate = { name, t, onDone -> viewModel.createCategory(name, t, onDone) },
            onUpdate = { id, name, t, onDone -> viewModel.updateCategory(id, name, t, onDone) },
            onDelete = { id, onConflict, onDone -> viewModel.deleteCategory(id, onConflict, onDone) },
            onDeleteWithReassign = { id, rid, onDone -> viewModel.deleteCategoryWithReassign(id, rid, onDone) },
            onDeleteAndTransactions = { id, onDone -> viewModel.deleteCategoryAndTransactions(id, onDone) },
            onDismiss = { categoryManagerType = null },
        )
    }

    // ── Main layout ─────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        // App bar
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Manage your preferences and secure your account.", fontSize = 13.sp, color = TextSecondary)
        }

        Spacer(Modifier.height(12.dp))

        // Profile card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(TealPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.userName.ifEmpty { "Loading…" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── APP PREFERENCES ─────────────────────────────────────────────────
        Text(
            "APP PREFERENCES",
            fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PreferenceDropdown(
                    label = "Currency",
                    selected = uiState.selectedCurrency,
                    options = uiState.options.currency,
                    onSelect = viewModel::selectCurrency,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── CATEGORIES ───────────────────────────────────────────────────────
        Text(
            "CATEGORIES",
            fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                CategoryTypeRow(
                    label = "Income",
                    count = uiState.categories.count { it.type == "income" },
                    dotColor = TealPrimary,
                    loading = uiState.categoriesLoading,
                    onClick = { categoryManagerType = "income" },
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                CategoryTypeRow(
                    label = "Expense",
                    count = uiState.categories.count { it.type == "expense" },
                    dotColor = LogoutRed,
                    loading = uiState.categoriesLoading,
                    onClick = { categoryManagerType = "expense" },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── WIDGET ──────────────────────────────────────────────────────────
        Text(
            "WIDGET",
            fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(context, WidgetConfigActivity::class.java)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(TealSurface, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Widget Settings", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("Configure what's shown on the home screen widget", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── PERMISSIONS ─────────────────────────────────────────────────────
        Text(
            "PERMISSIONS",
            fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (hasSmsPermission) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        } else showSmsRationale = true
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(38.dp).background(TealSurface, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMS Access", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        if (hasSmsPermission) "Granted" else "Not granted",
                        fontSize = 12.sp,
                        color = if (hasSmsPermission) TealPrimary else TextSecondary,
                    )
                }
                Switch(
                    checked = hasSmsPermission,
                    onCheckedChange = {
                        if (hasSmsPermission) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        } else showSmsRationale = true
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── ACCOUNT ─────────────────────────────────────────────────────────
        Text(
            "ACCOUNT",
            fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.signOut() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(38.dp).background(LogoutRedLight, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = LogoutRed, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Log Out", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LogoutRed)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category type entry row (Income / Expense)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryTypeRow(
    label: String,
    count: Int,
    dotColor: Color,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$label Categories",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TealPrimary, strokeWidth = 2.dp)
        } else {
            Text("$count", fontSize = 13.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category manager dialog  (search + CRUD for one type)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryManagerDialog(
    type: String,
    categories: List<SmsCategory>,          // pre-filtered to this type
    isSaving: Boolean,
    onCreate: (name: String, type: String, onDone: (SmsCategory?) -> Unit) -> Unit,
    onUpdate: (id: String, name: String, type: String, onDone: (Boolean) -> Unit) -> Unit,
    onDelete: (id: String, onConflict: (Int) -> Unit, onDone: (Boolean) -> Unit) -> Unit,
    onDeleteWithReassign: (id: String, reassignId: String, onDone: (Boolean) -> Unit) -> Unit,
    onDeleteAndTransactions: (id: String, onDone: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = "${type.replaceFirstChar { it.uppercase() }} Categories"

    var query by remember { mutableStateOf("") }

    // Create state
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // Edit state
    var editingCat by remember { mutableStateOf<SmsCategory?>(null) }
    var editName by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(type) }

    // Delete state
    var deletingCat by remember { mutableStateOf<SmsCategory?>(null) }
    var deleteConflict by remember { mutableStateOf<DeleteConflictState?>(null) }
    var showReassignPicker by remember { mutableStateOf(false) }

    val filtered = remember(query, categories) {
        if (query.isBlank()) categories
        else categories.filter { it.name.contains(query, ignoreCase = true) }
    }

    // ── Create dialog ───────────────────────────────────────────────────────
    if (showCreate) {
        CategoryEditDialog(
            title = "New ${type.replaceFirstChar { it.uppercase() }} Category",
            name = newName,
            type = type,               // locked to this manager's type
            showTypeSelector = false,  // no type change when creating from the type-specific manager
            isSaving = isSaving,
            onNameChange = { newName = it },
            onTypeChange = {},
            onConfirm = {
                onCreate(newName, type) { result ->
                    if (result != null) {
                        showCreate = false
                        newName = ""
                    }
                }
            },
            onDismiss = { showCreate = false; newName = "" },
        )
    }

    // ── Edit dialog ─────────────────────────────────────────────────────────
    if (editingCat != null) {
        CategoryEditDialog(
            title = "Edit Category",
            name = editName,
            type = editType,
            showTypeSelector = true,
            isSaving = isSaving,
            onNameChange = { editName = it },
            onTypeChange = { editType = it },
            onConfirm = {
                onUpdate(editingCat!!.id, editName, editType) { success ->
                    if (success) editingCat = null
                }
            },
            onDismiss = { editingCat = null },
        )
    }

    // ── Delete confirmation ─────────────────────────────────────────────────
    if (deletingCat != null) {
        val cat = deletingCat!!
        AlertDialog(
            onDismissRequest = { deletingCat = null },
            title = { Text("Delete category?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "\"${cat.name}\" will be permanently deleted. If it has transactions you'll be asked what to do with them.",
                    fontSize = 14.sp, color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = cat.id
                    deletingCat = null
                    onDelete(
                        id,
                        { count -> deleteConflict = DeleteConflictState(cat, count) },
                        {},
                    )
                }) { Text("Delete", color = LogoutRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCat = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceWhite,
        )
    }

    // ── Conflict resolution ─────────────────────────────────────────────────
    if (deleteConflict != null) {
        val (conflictCat, count) = deleteConflict!!
        // Other categories of the same type (for reassignment)
        val reassignOptions = categories.filter { it.id != conflictCat.id }

        if (showReassignPicker) {
            // Reassign target picker
            Dialog(onDismissRequest = { showReassignPicker = false }) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                    Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                        Text(
                            "Move transactions to…",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "All transactions in \"${conflictCat.name}\" will be moved.",
                            fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        if (reassignOptions.isEmpty()) {
                            Text(
                                "No other categories of this type to reassign to.",
                                fontSize = 13.sp, color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(reassignOptions) { cat ->
                                    HorizontalDivider(color = DividerColor)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showReassignPicker = false
                                                val id = conflictCat.id
                                                deleteConflict = null
                                                onDeleteWithReassign(id, cat.id) {}
                                            }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(cat.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = DividerColor)
                        TextButton(
                            onClick = { showReassignPicker = false },
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text("Cancel", color = TealPrimary) }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { deleteConflict = null },
                title = { Text("Category in use", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            "\"${conflictCat.name}\" has $count transaction${if (count == 1) "" else "s"}.",
                            fontSize = 14.sp, color = TextPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("What should happen to those transactions?", fontSize = 13.sp, color = TextSecondary)
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = { showReassignPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        ) { Text("Move to another category", fontSize = 13.sp) }
                        Button(
                            onClick = {
                                val id = conflictCat.id
                                deleteConflict = null
                                onDeleteAndTransactions(id) {}
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoutRed),
                        ) { Text("Delete all transactions", fontSize = 13.sp) }
                        TextButton(
                            onClick = { deleteConflict = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Cancel", color = TextSecondary) }
                    }
                },
                dismissButton = {},
                containerColor = SurfaceWhite,
            )
        }
    }

    // ── Main picker dialog ──────────────────────────────────────────────────
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { newName = ""; showCreate = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = TealPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

                when {
                    categories.isEmpty() -> {
                        Text(
                            "No ${type} categories yet. Tap New to add one.",
                            fontSize = 13.sp, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    filtered.isEmpty() -> {
                        Text(
                            "No matches for \"$query\"",
                            fontSize = 13.sp, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(filtered, key = { it.id }) { cat ->
                                HorizontalDivider(color = DividerColor)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        cat.name,
                                        fontSize = 14.sp, color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            editingCat = cat
                                            editName = cat.name
                                            editType = cat.type
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { deletingCat = cat },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = LogoutRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(top = 4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("Done", color = TealPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable category edit dialog  (create + edit)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryEditDialog(
    title: String,
    name: String,
    type: String,
    showTypeSelector: Boolean,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = TealPrimary,
                        unfocusedIndicatorColor = DividerColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        cursorColor = TealPrimary,
                    ),
                )
                if (showTypeSelector) {
                    Column {
                        Text("Type", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("expense", "income", "transfer").forEach { t ->
                                val selected = type == t
                                FilterChip(
                                    selected = selected,
                                    onClick = { if (!isSaving) onTypeChange(t) },
                                    label = { Text(t.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TealPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppBackground,
                                        labelColor = TextSecondary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = selected,
                                        borderColor = DividerColor, selectedBorderColor = TealPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving && name.isNotBlank()) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealPrimary, strokeWidth = 2.dp)
                else Text("Save", color = TealPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving) onDismiss() }) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceWhite,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared preference dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PreferenceDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    displayTransform: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(displayTransform(selected), fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayTransform(option), fontSize = 13.sp) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
