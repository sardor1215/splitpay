package com.splitpay.ui.group

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.SpaceResponse
import com.splitpay.viewmodel.AddableContact
import com.splitpay.viewmodel.GroupDetailViewModel
import com.splitpay.viewmodel.GroupMember
import com.splitpay.viewmodel.InvitableContact
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.SpaceViewModel
import kotlinx.coroutines.launch

private val TertiaryFixedDim = Color(0xFFFFB4AC)

private const val INVITE_MESSAGE = "Hey! Join me on SplitPay to split expenses easily 🎉\nDownload: https://splitpay.app/download"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExpense: (spaceId: String) -> Unit = {},
    onNavigateToCreateExpense: () -> Unit = {},
    viewModel: GroupDetailViewModel = viewModel(),
    spaceVm: SpaceViewModel = viewModel()
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val Surface          = c.surface
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant
    val SurfaceContainerLowest = c.surfaceLowest
    val SurfaceContainerLow    = c.surfaceLow
    val SurfaceContainerHigh   = c.surfaceHigh
    val ErrorColor             = Color(0xFFBA1A1A)

    val context       = LocalContext.current
    val group         by viewModel.group.collectAsStateWithLifecycle()
    val members       by viewModel.members.collectAsStateWithLifecycle()
    val isAdmin       by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val groupDeleted  by viewModel.groupDeleted.collectAsStateWithLifecycle()
    val addableContacts   by viewModel.addableContacts.collectAsStateWithLifecycle()
    val invitableContacts by viewModel.invitableContacts.collectAsStateWithLifecycle()
    val isLoadingContacts by viewModel.isLoadingContacts.collectAsStateWithLifecycle()
    val spaces        by spaceVm.spaces.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var showSheet           by remember { mutableStateOf(false) }
    var showEditDialog      by remember { mutableStateOf(false) }
    var showDeleteDialog    by remember { mutableStateOf(false) }
    var showLeaveDialog     by remember { mutableStateOf(false) }
    var showLeaveBlocked    by remember { mutableStateOf(false) }
    var showMembersSheet    by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var editedName       by remember { mutableStateOf("") }
    var editedEmoji      by remember { mutableStateOf("") }
    var memberToRemove   by remember { mutableStateOf<GroupMember?>(null) }
    var searchQuery      by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadContactsForAdding(context.contentResolver)
    }

    LaunchedEffect(groupDeleted) { if (groupDeleted) onNavigateBack() }
    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
        spaceVm.loadSpaces(groupId)
    }


    // ── Add Member full-screen dialog ─────────────────────────────────────────
    if (showAddMemberSheet) {
        val filteredAddable = remember(searchQuery, addableContacts) {
            if (searchQuery.isBlank()) addableContacts
            else addableContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true)
            }
        }
        val filteredInvitable = remember(searchQuery, invitableContacts) {
            if (searchQuery.isBlank()) invitableContacts
            else invitableContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true)
            }
        }

        Dialog(
            onDismissRequest = { showAddMemberSheet = false; searchQuery = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Surface)) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Header
                    Box(
                        modifier = Modifier.fillMaxWidth().background(SurfaceContainerLowest)
                            .windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .clickable { showAddMemberSheet = false; searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, null, tint = Primary, modifier = Modifier.size(22.dp))
                            }
                            Text("Add Member", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Primary)
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                    }

                    // Search bar
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLowest)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Search, null, tint = OutlineVariant, modifier = Modifier.size(20.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = OnSurface),
                                cursorBrush = SolidColor(Primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) Text("Search by name or phone…", fontSize = 15.sp, color = OutlineVariant)
                                    inner()
                                }
                            )
                        }
                    }

                    if (isLoadingContacts) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // On SplitPay section
                            if (filteredAddable.isNotEmpty()) {
                                item {
                                    Text(
                                        "ON SPLITPAY", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp
                                    )
                                }
                                items(filteredAddable) { contact ->
                                    AddableContactRow(
                                        contact = contact,
                                        onAdd = {
                                            viewModel.addMemberById(groupId, contact.userId, contact.name,
                                                onSuccess = {},
                                                onError = {}
                                            )
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }

                            // Invite section
                            if (filteredInvitable.isNotEmpty()) {
                                item {
                                    Text(
                                        "INVITE TO SPLITPAY", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp
                                    )
                                }
                                items(filteredInvitable) { contact ->
                                    InvitableContactRow(
                                        contact = contact,
                                        onInvite = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, INVITE_MESSAGE)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Invite ${contact.name} via…"))
                                        }
                                    )
                                }
                            }

                            if (filteredAddable.isEmpty() && filteredInvitable.isEmpty() && searchQuery.isNotBlank()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No results for \"$searchQuery\"", color = OnSurfaceVariant, fontSize = 14.sp)
                                    }
                                }
                            }

                            if (filteredAddable.isEmpty() && filteredInvitable.isEmpty() && searchQuery.isBlank()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("All your contacts are already in this group!", color = OnSurfaceVariant, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Edit Group", fontWeight = FontWeight.Bold, color = Primary, fontSize = 18.sp) },
            text = {
                Column {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            Text(text = editedEmoji, fontSize = 36.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CHOOSE ICON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items(viewModel.emojis) { emoji ->
                            val isSel = emoji == editedEmoji
                            Box(
                                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Primary.copy(alpha = 0.12f) else SurfaceContainerLow)
                                    .border(if (isSel) 2.dp else 0.dp, if (isSel) Primary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { editedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 18.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("GROUP NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainerLow).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        BasicTextField(
                            value = editedName, onValueChange = { editedName = it }, singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (editedName.isEmpty()) Text("Enter group name", fontSize = 16.sp, color = OutlineVariant)
                                inner()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                        .clickable { viewModel.updateGroup(groupId, editedName, editedEmoji) { showEditDialog = false } }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceContainerLowest, shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Group", fontWeight = FontWeight.Bold, color = ErrorColor, fontSize = 18.sp) },
            text = { Text("Are you sure you want to delete \"${group?.name}\"? This action cannot be undone.", fontSize = 14.sp, color = OnSurfaceVariant, lineHeight = 20.sp) },
            confirmButton = {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(ErrorColor)
                    .clickable { showDeleteDialog = false; viewModel.deleteGroup(groupId) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    // ── Leave dialog ──────────────────────────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = SurfaceContainerLowest, shape = RoundedCornerShape(24.dp),
            title = { Text("Leave Group", fontWeight = FontWeight.Bold, color = ErrorColor, fontSize = 18.sp) },
            text = { Text("Are you sure you want to leave \"${group?.name}\"?", fontSize = 14.sp, color = OnSurfaceVariant, lineHeight = 20.sp) },
            confirmButton = {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(ErrorColor)
                    .clickable { showLeaveDialog = false; viewModel.leaveGroup(groupId) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Leave", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    // ── Leave blocked dialog ──────────────────────────────────────────────────
    if (showLeaveBlocked) {
        val balance = group?.balance ?: 0.0
        val message = if (balance < 0)
            "You owe €${"%.2f".format(-balance)} in this group. Settle your debts before leaving."
        else
            "You are owed €${"%.2f".format(balance)} in this group. Collect what you're owed before leaving."
        AlertDialog(
            onDismissRequest = { showLeaveBlocked = false },
            containerColor = SurfaceContainerLowest, shape = RoundedCornerShape(24.dp),
            title = { Text("Cannot Leave Group", fontWeight = FontWeight.Bold, color = ErrorColor, fontSize = 18.sp) },
            text = { Text(message, fontSize = 14.sp, color = OnSurfaceVariant, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { showLeaveBlocked = false }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = Primary)
                }
            },
            dismissButton = null
        )
    }

    // ── Remove member dialog ──────────────────────────────────────────────────
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            containerColor = SurfaceContainerLowest, shape = RoundedCornerShape(24.dp),
            title = { Text("Remove Member", fontWeight = FontWeight.Bold, color = ErrorColor, fontSize = 18.sp) },
            text = { Text("Remove ${member.name} from this group?", fontSize = 14.sp, color = OnSurfaceVariant) },
            confirmButton = {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(ErrorColor)
                    .clickable { viewModel.removeMember(groupId, member.userId) {}; memberToRemove = null }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Remove", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    // ── Members bottom sheet ──────────────────────────────────────────────────
    if (showMembersSheet) {
        val membersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMembersSheet = false },
            sheetState = membersSheetState,
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(OutlineVariant.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Members", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isAdmin) {
                            Box(
                                modifier = Modifier.clip(CircleShape).background(Primary.copy(alpha = 0.08f))
                                    .clickable {
                                        // Load contacts then open add member sheet
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        showAddMemberSheet = true
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("${members.size} total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                members.forEach { member ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text(member.name.firstOrNull()?.toString() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.role.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    color = if (member.role == "admin") Primary else OnSurfaceVariant,
                                    fontWeight = if (member.role == "admin") FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        if (isAdmin && member.role != "admin") {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(ErrorColor.copy(alpha = 0.1f))
                                    .clickable { memberToRemove = member }.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorColor) }
                        }
                    }
                    if (members.last() != member) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }

    // ── Settings bottom sheet ─────────────────────────────────────────────────
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(OutlineVariant.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(20.dp))
                Text("Group Settings", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Primary)
                Text(group?.name ?: "", fontSize = 13.sp, color = OnSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                SettingsOption(icon = { Icon(Icons.Default.Edit, null, tint = Primary, modifier = Modifier.size(20.dp)) }, title = "Edit Group", subtitle = "Change name and icon", iconBg = Primary.copy(alpha = 0.1f),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false; editedName = group?.name ?: ""; editedEmoji = group?.emoji ?: "💰"; showEditDialog = true } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsOption(icon = { Icon(Icons.Default.Group, null, tint = Primary, modifier = Modifier.size(20.dp)) }, title = "Manage Members", subtitle = "${members.size} members in this group", iconBg = Primary.copy(alpha = 0.1f),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false; showMembersSheet = true } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsOption(
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ErrorColor, modifier = Modifier.size(20.dp)) },
                    title = "Leave Group",
                    subtitle = "Remove yourself from this group",
                    iconBg = ErrorColor.copy(alpha = 0.1f),
                    titleColor = ErrorColor,
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showSheet = false
                            if ((group?.balance ?: 0.0) != 0.0) showLeaveBlocked = true
                            else showLeaveDialog = true
                        }
                    }
                )
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsOption(icon = { Icon(Icons.Default.Delete, null, tint = ErrorColor, modifier = Modifier.size(20.dp)) }, title = "Delete Group", subtitle = "Permanently remove this group", iconBg = ErrorColor.copy(alpha = 0.1f), titleColor = ErrorColor,
                        onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false; showDeleteDialog = true } }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh(groupId) },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 140.dp, bottom = 160.dp)) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("ACTIVE TRIP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(group?.name ?: "", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Primary, letterSpacing = (-1.5).sp, lineHeight = 42.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        group?.members?.take(4)?.forEachIndexed { index, member ->
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(when (index % 3) { 0 -> Primary.copy(0.15f); 1 -> Secondary.copy(0.15f); else -> Tertiary.copy(0.15f) }), contentAlignment = Alignment.Center) {
                                Text(member.firstOrNull()?.toString() ?: "?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = when (index % 3) { 0 -> Primary; 1 -> Secondary; else -> Tertiary })
                            }
                        }
                        if ((group?.members?.size ?: 0) > 4) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(SurfaceContainerHigh), contentAlignment = Alignment.Center) {
                                Text("+${(group?.members?.size ?: 0) - 4}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            // ── Expenses header ───────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "EXPENSES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp
                    )
                    if (spaces.isNotEmpty()) {
                        Text(
                            "${spaces.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Expense cards ─────────────────────────────────────────────────────
            if (spaces.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainerLowest)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🗂️", fontSize = 36.sp)
                            Text("No expenses yet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Primary)
                            Text("Tap + to create a shared expense", fontSize = 13.sp, color = OnSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                items(spaces) { space ->
                    ExpenseCard(space = space, onClick = { onNavigateToExpense(space.id) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

        }
        } // end PullToRefreshBox

        Box(modifier = Modifier.fillMaxWidth().background(SurfaceContainerLowest).windowInsetsPadding(WindowInsets.statusBars).height(70.dp).align(Alignment.TopCenter)) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary) }
                Text(group?.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                IconButton(onClick = { showSheet = true }) { Icon(Icons.Default.Settings, null, tint = Primary) }
            }
        }

        // FAB — Create new Expense
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .size(60.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Primary.copy(0.3f))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                .clickable { onNavigateToCreateExpense() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

// ── Expense Card ──────────────────────────────────────────────────────────────
@Composable
private fun ExpenseCard(space: SpaceResponse, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant
    val ErrorColor       = Color(0xFFBA1A1A)
    val SurfaceContainerLowest = c.surfaceLowest

    val statusColor = when (space.status) {
        "active"    -> Secondary
        "settling"  -> Primary
        "settled"   -> Secondary
        "cancelled" -> ErrorColor
        "suspended" -> Color(0xFFE65100)
        else        -> OnSurfaceVariant
    }
    val needsResponse = space.myAcceptanceStatus == "pending" && space.status == "pending_acceptance"
    val needsQuorum   = space.myQuorumStatus == "pending"
    val actionColor   = if (needsQuorum) Primary else Color(0xFFE65100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLowest)
            .then(
                if (needsResponse || needsQuorum)
                    Modifier.border(1.5.dp, actionColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Accent bar gauche ─────────────────────────────────────────
            if (needsResponse || needsQuorum) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(actionColor, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Corps principal ───────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (needsResponse || needsQuorum) 16.dp else 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(space.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OnSurface)
                        Text(SpaceViewModel.categoryLabel(space.category), fontSize = 12.sp, color = OnSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusColor.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(SpaceViewModel.statusLabel(space.status), fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }
                        space.dueDate?.let {
                            Text("Due ${it.take(10)}", fontSize = 11.sp, color = OutlineVariant, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "€%.2f".format(space.totalAmount),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text("${space.participants.size} members", fontSize = 11.sp, color = OnSurfaceVariant)
                        space.myShare?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("YOUR SHARE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OutlineVariant, letterSpacing = 0.8.sp)
                            Text("€%.2f".format(it), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                        }
                    }
                }

                // ── Bandeau d'action ──────────────────────────────────────
                if (needsResponse || needsQuorum) {
                    HorizontalDivider(color = actionColor.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(actionColor.copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (needsQuorum) "⚠️" else "🔔", fontSize = 14.sp)
                            Text(
                                if (needsQuorum) "Confirm quorum required" else "Your response is needed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = actionColor
                            )
                        }
                        Text("Tap to respond →", fontSize = 11.sp, color = actionColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Addable Contact Row ───────────────────────────────────────────────────────
@Composable
private fun AddableContactRow(contact: AddableContact, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val SurfaceContainerLowest = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    var added by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainerLowest).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(contact.name.firstOrNull()?.toString() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
            Column {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(contact.phone, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (added) Secondary.copy(alpha = 0.1f) else Primary.copy(alpha = 0.1f))
                .clickable(enabled = !added) { added = true; onAdd() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(if (added) "Added ✓" else "Add", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (added) Secondary else Primary)
        }
    }
}

// ── Invitable Contact Row ─────────────────────────────────────────────────────
@Composable
private fun InvitableContactRow(contact: InvitableContact, onInvite: () -> Unit) {
    val c = LocalAppColors.current
    val Secondary        = c.secondary
    val SurfaceContainerLowest = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainerLowest).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(OutlineVariant.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(contact.name.firstOrNull()?.toString() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
            }
            Column {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(contact.phone, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Secondary.copy(alpha = 0.1f)).clickable { onInvite() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Invite", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Secondary)
        }
    }
}

// ── Settings Option ───────────────────────────────────────────────────────────
@Composable
private fun SettingsOption(icon: @Composable () -> Unit, title: String, subtitle: String, iconBg: Color, titleColor: Color = Color(0xFF1A1C1E), onClick: () -> Unit) {
    val c = LocalAppColors.current
    val SurfaceContainerLow = c.surfaceLow
    val OnSurfaceVariant    = c.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLow).clickable { onClick() }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) { icon() }
            Column { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = titleColor); Text(subtitle, fontSize = 12.sp, color = OnSurfaceVariant) }
        }
    }
}



