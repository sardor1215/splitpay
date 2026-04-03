package com.splitpay.ui.group

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.model.Expense
import com.splitpay.viewmodel.ContactWithStatus
import com.splitpay.viewmodel.GroupDetailViewModel
import com.splitpay.viewmodel.GroupMember
import com.splitpay.viewmodel.Settlement

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val Secondary = Color(0xFF1B6D24)
private val Tertiary = Color(0xFF84000C)
private val TertiaryFixedDim = Color(0xFFFFB4AC)
private val Surface = Color(0xFFF9F9FC)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val SurfaceContainerLow = Color(0xFFF3F3F6)
private val SurfaceContainerHigh = Color(0xFFE8E8EA)
private val OnSurface = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant = Color(0xFFBEC8C9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: (String) -> Unit,
    onNavigateToSettlement: (String) -> Unit,
    viewModel: GroupDetailViewModel = viewModel()
) {
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val group        by viewModel.group.collectAsStateWithLifecycle()
    val expenses     by viewModel.expenses.collectAsStateWithLifecycle()
    val settlements  by viewModel.settlements.collectAsStateWithLifecycle()
    val yourBalance  by viewModel.yourBalance.collectAsStateWithLifecycle()
    val totalSpending by viewModel.totalSpending.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()

    val contacts         by viewModel.contacts.collectAsStateWithLifecycle()
    val isLoadingContacts by viewModel.isLoadingContacts.collectAsStateWithLifecycle()
    val contactsError    by viewModel.contactsError.collectAsStateWithLifecycle()
    val addMemberError   by viewModel.addMemberError.collectAsStateWithLifecycle()
    val groupError       by viewModel.groupError.collectAsStateWithLifecycle()

    var showMembersSheet   by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var showSettingsSheet  by remember { mutableStateOf(false) }
    var showEditDialog     by remember { mutableStateOf(false) }
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val membersSheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addMemberSheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showAddMemberSheet = true
            viewModel.loadContacts()
        }
    }

    // Auto-load contacts when sheet opens (if permission already granted)
    LaunchedEffect(showAddMemberSheet) {
        if (showAddMemberSheet &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.loadContacts()
        }
    }

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
        viewModel.startPolling(groupId)
    }
    DisposableEffect(groupId) {
        onDispose { viewModel.stopPolling() }
    }

    // ── Members bottom sheet ──────────────────────────────────────────────
    if (showMembersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMembersSheet = false },
            sheetState = membersSheetState,
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Members",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${groupMembers.size} people in this group",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Search bar
                var memberSearch by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = memberSearch,
                        onValueChange = { memberSearch = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (memberSearch.isEmpty()) Text("Search members…", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                    if (memberSearch.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { memberSearch = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val filtered = remember(groupMembers, memberSearch) {
                    if (memberSearch.isBlank()) groupMembers
                    else groupMembers.filter { it.name.contains(memberSearch, ignoreCase = true) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceContainerLow)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (member.role == "admin") Primary.copy(alpha = 0.15f)
                                            else Primary.copy(alpha = 0.07f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.first().toString(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = member.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurface
                                        )
                                        if (member.role == "admin") {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Primary.copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Admin",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (member.isOnApp) "On SplitPay" else "Not on SplitPay",
                                        fontSize = 11.sp,
                                        color = if (member.isOnApp) OnSurfaceVariant else OutlineVariant
                                    )
                                }
                            }
                            if (member.userId.isNotEmpty() && member.role != "admin") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Tertiary.copy(alpha = 0.07f))
                                        .clickable {
                                            viewModel.removeMember(groupId, member.userId) {}
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.PersonRemove, null, tint = Tertiary, modifier = Modifier.size(14.dp))
                                        Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tertiary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Add Member button ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Primary.copy(alpha = 0.06f))
                        .clickable {
                            showMembersSheet = false
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Add Member", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                        Text("Sync from your contacts", fontSize = 11.sp, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }

    // ── Add Member sheet ──────────────────────────────────────────────────
    if (showAddMemberSheet) {
        var addSearch   by remember { mutableStateOf("") }
        var selectedIds by remember { mutableStateOf(setOf<String>()) }

        val memberUserIds = remember(groupMembers) { groupMembers.map { it.userId }.toSet() }
        val filtered = remember(contacts, addSearch, memberUserIds) {
            contacts
                .filter { it.isOnApp && it.userId !in memberUserIds }
                .let { list ->
                    if (addSearch.isBlank()) list
                    else list.filter { it.name.contains(addSearch, ignoreCase = true) }
                }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddMemberSheet = false },
            sheetState = addMemberSheetState,
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // ── Header ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Members",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (selectedIds.isEmpty()) "${filtered.size} available"
                                   else "${selectedIds.size} selected",
                            fontSize = 13.sp,
                            color = if (selectedIds.isEmpty()) OnSurfaceVariant else Primary,
                            fontWeight = if (selectedIds.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.06f))
                            .clickable { if (!isLoadingContacts) viewModel.loadContacts() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingContacts) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Search bar ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = addSearch,
                        onValueChange = { addSearch = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (addSearch.isEmpty()) Text("Search contacts…", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                    if (addSearch.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { addSearch = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Error ─────────────────────────────────────────────────
                val errorMsg = contactsError ?: addMemberError
                if (errorMsg != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearErrors() },
                        title = { Text("Error", fontWeight = FontWeight.Bold) },
                        text  = { Text(errorMsg) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.clearErrors() }) {
                                Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = SurfaceContainerLowest,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                // ── Contact list with checkboxes ──────────────────────────
                if (isLoadingContacts) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Primary) }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("No contacts found", fontSize = 14.sp, color = OutlineVariant) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filtered.forEach { contact ->
                            val isSelected = contact.userId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) Primary.copy(alpha = 0.07f)
                                        else SurfaceContainerLow
                                    )
                                    .clickable {
                                        val uid = contact.userId ?: return@clickable
                                        selectedIds = if (isSelected) selectedIds - uid else selectedIds + uid
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Primary else Primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text(
                                            text = contact.name.first().toString(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Primary
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurface
                                    )
                                    Text(
                                        text = contact.phone,
                                        fontSize = 11.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        val uid = contact.userId ?: return@Checkbox
                                        selectedIds = if (checked) selectedIds + uid else selectedIds - uid
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Primary,
                                        uncheckedColor = OutlineVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // ── Add button ────────────────────────────────────────────
                if (selectedIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Primary, PrimaryContainer)
                                )
                            )
                            .clickable {
                                showAddMemberSheet = false
                                viewModel.addMembers(groupId, selectedIds.toList()) {}
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Add ${selectedIds.size} member${if (selectedIds.size > 1) "s" else ""}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // ── Group error dialog ────────────────────────────────────────────────
    if (groupError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrors() },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text  = { Text(groupError!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrors() }) {
                    Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Delete confirmation dialog ────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group", fontWeight = FontWeight.Bold, color = Tertiary) },
            text  = { Text("This will permanently delete the group and all its data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteGroup(groupId) { onNavigateBack() }
                }) {
                    Text("Delete", color = Tertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Primary)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Archive confirmation dialog ───────────────────────────────────────
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive Group", fontWeight = FontWeight.Bold) },
            text  = { Text("The group will be archived and hidden from your list. Members won't be able to add expenses.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    viewModel.archiveGroup(groupId) { onNavigateBack() }
                }) {
                    Text("Archive", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Edit group dialog ─────────────────────────────────────────────────
    if (showEditDialog) {
        var editName  by remember { mutableStateOf(group?.name ?: "") }
        var editEmoji by remember { mutableStateOf(group?.emoji ?: "💰") }
        val emojis = listOf("🏠","✈️","🍕","🏕️","🎉","🛒","🏋️","🎮","🎵","🚗","⚽","📚","💼","🌴","🍻","🎓","💊","🐾","💰")
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Group", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Emoji", fontSize = 13.sp, color = OnSurfaceVariant, fontWeight = FontWeight.Medium)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        gridItems(emojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (editEmoji == emoji) Primary.copy(alpha = 0.15f) else SurfaceContainerLow)
                                    .border(if (editEmoji == emoji) 2.dp else 0.dp, if (editEmoji == emoji) Primary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { editEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 18.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            showEditDialog = false
                            viewModel.updateGroup(groupId, editName, editEmoji) {}
                        }
                    }
                ) { Text("Save", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Settings sheet ────────────────────────────────────────────────────
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Group Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // Members
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .clickable { showSettingsSheet = false; showMembersSheet = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Group, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text("Members", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                        Text("${groupMembers.size} people in this group", fontSize = 12.sp, color = OnSurfaceVariant)
                    }
                }
                // Add Member
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .clickable {
                            showSettingsSheet = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                showAddMemberSheet = true
                                viewModel.loadContacts()
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.PersonAdd, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text("Add Member", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                        Text("Invite from your contacts", fontSize = 12.sp, color = OnSurfaceVariant)
                    }
                }
                // Edit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .clickable { showSettingsSheet = false; showEditDialog = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Edit, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text("Edit Group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                        Text("Change name and emoji", fontSize = 12.sp, color = OnSurfaceVariant)
                    }
                }
                // Archive / Unarchive
                val isArchived = group?.isArchived == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .clickable {
                            showSettingsSheet = false
                            if (isArchived) viewModel.unarchiveGroup(groupId) { onNavigateBack() }
                            else showArchiveConfirm = true
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(OnSurfaceVariant.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Archive, null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text(
                            text = if (isArchived) "Unarchive Group" else "Archive Group",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface
                        )
                        Text(
                            text = if (isArchived) "Move back to active groups" else "Hide without deleting",
                            fontSize = 12.sp, color = OnSurfaceVariant
                        )
                    }
                }
                // Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tertiary.copy(alpha = 0.05f))
                        .clickable { showSettingsSheet = false; showDeleteConfirm = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Tertiary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Tertiary, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text("Delete Group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tertiary)
                        Text("Permanently delete this group", fontSize = 12.sp, color = Tertiary.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Main scrollable content ───────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 140.dp, bottom = 160.dp)
        ) {

            // ── Hero section ──────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Active badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ACTIVE TRIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = group?.name ?: "",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = Primary,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 42.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Member avatars
                    if (groupMembers.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-8).dp),
                            modifier = Modifier.clickable { showMembersSheet = true }
                        ) {
                            groupMembers.take(4).forEachIndexed { index, member ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (index % 3) {
                                                0 -> Primary.copy(alpha = 0.15f)
                                                1 -> Secondary.copy(alpha = 0.15f)
                                                else -> Tertiary.copy(alpha = 0.15f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.first().toString(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (index % 3) {
                                            0 -> Primary
                                            1 -> Secondary
                                            else -> Tertiary
                                        }
                                    )
                                }
                            }
                            if (groupMembers.size > 4) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${groupMembers.size - 4}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (isLoading) {
                        // Placeholder while loading
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainerHigh)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "TOTAL SPENDING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "€${String.format("%.2f", totalSpending)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Primary,
                        letterSpacing = (-1).sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Balance cards ─────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceContainerLowest)
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = "YOUR STATUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariant.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (yourBalance >= 0) Secondary else Tertiary
                                        )
                                )
                                Column {
                                    Text(
                                        text = if (yourBalance >= 0)
                                            "You are owed €${String.format("%.2f", yourBalance)}"
                                        else
                                            "You owe €${String.format("%.2f", -yourBalance)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (yourBalance >= 0)
                                            "Settlement pending from ${settlements.size} members"
                                        else
                                            "Settle up to clear your balance",
                                        fontSize = 12.sp,
                                        color = if (yourBalance >= 0) Secondary else Tertiary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Insights card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Primary)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "💡", fontSize = 18.sp)
                                Text(
                                    text = "INSIGHTS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Top spender",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = groupMembers.firstOrNull()?.name ?: "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Expense timeline header ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expense Timeline",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    TextButton(onClick = { }) {
                        Text(
                            text = "Filter",
                            fontSize = 13.sp,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Expense items ─────────────────────────────────────────────
            items(expenses) { expense ->
                ExpenseItem(expense = expense)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Settlement breakdown ──────────────────────────────────────
            if (settlements.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Settlement Breakdown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceContainerLow)
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            settlements.forEach { settlement ->
                                SettlementRow(settlement = settlement)
                            }
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "ℹ️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Net balance includes all pending transfers",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Loading overlay ───────────────────────────────────────────────
        if (isLoading && group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        }

        // ── Top App Bar ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(70.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Primary
                    )
                }
                Text(
                    text = group?.name ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Primary
                    )
                }
            }
        }

        // ── FAB Add Expense ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 110.dp)
                .size(60.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), spotColor = Primary.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Primary, PrimaryContainer)
                    )
                )
                .clickable { onNavigateToAddExpense(groupId) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add expense",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // ── Floating Settle Up panel ──────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = Primary.copy(alpha = 0.08f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceContainerLowest)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BALANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (yourBalance >= 0)
                                "+€${String.format("%.2f", yourBalance)}"
                            else
                                "-€${String.format("%.2f", -yourBalance)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (yourBalance >= 0) Secondary else Tertiary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Primary, PrimaryContainer)
                                )
                            )
                            .clickable { onNavigateToSettlement(groupId) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "💳", fontSize = 16.sp)
                            Text(
                                text = "Settle Up",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Expense Item ──────────────────────────────────────────────────────────────
@Composable
fun ExpenseItem(expense: Expense) {
    val accentColor = when {
        expense.yourShare > 0 -> Secondary
        expense.yourShare < 0 -> TertiaryFixedDim
        else -> OutlineVariant
    }
    val shareText = when {
        expense.yourShare > 0 -> "You get back €${String.format("%.2f", expense.yourShare)}"
        expense.yourShare < 0 -> "You owe €${String.format("%.2f", -expense.yourShare)}"
        else -> "Settled"
    }
    val shareColor = when {
        expense.yourShare > 0 -> Secondary
        expense.yourShare < 0 -> Tertiary
        else -> OnSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Column {
                    Text(
                        text = expense.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${expense.date} • Paid by ${expense.paidBy}",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "€${String.format("%.2f", expense.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = shareText.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = shareColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ── Settlement Row ────────────────────────────────────────────────────────────
@Composable
fun SettlementRow(settlement: Settlement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = settlement.memberName.first().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Text(
                text = if (settlement.owesYou)
                    "${settlement.memberName} owes you"
                else
                    "You owe ${settlement.memberName}",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnSurface
            )
        }
        Text(
            text = "€${String.format("%.2f", settlement.amount)}",
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = if (settlement.owesYou) Secondary else Tertiary
        )
    }
}

