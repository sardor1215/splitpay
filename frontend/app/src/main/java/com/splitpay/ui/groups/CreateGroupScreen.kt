package com.splitpay.ui.groups

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.viewmodel.Contact
import com.splitpay.viewmodel.CreateGroupViewModel

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val PrimaryFixed     = Color(0xFFE0E0FF)
private val Secondary        = Color(0xFF1B6D24)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val groupName     by viewModel.groupName.collectAsStateWithLifecycle()
    val selectedEmoji by viewModel.selectedEmoji.collectAsStateWithLifecycle()
    val contacts      by viewModel.contacts.collectAsStateWithLifecycle()
    val isSyncing     by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val createError   by viewModel.createError.collectAsStateWithLifecycle()
    val addedCount = contacts.count { it.isAdded }

    val previewContacts = contacts.take(3)

    var showAllSheet by remember { mutableStateOf(false) }
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current

    val syncPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.syncContacts() }

    // Auto-sync on first open
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.syncContacts()
        } else {
            syncPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // ── All contacts sheet ────────────────────────────────────────────────
    if (showAllSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Add Members",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${contacts.size} contacts on SplitPay",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                var sheetSearch by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLow)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = sheetSearch,
                        onValueChange = { sheetSearch = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (sheetSearch.isEmpty()) Text("Search contacts…", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                    if (sheetSearch.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { sheetSearch = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val filtered = remember(contacts, sheetSearch) {
                    if (sheetSearch.isBlank()) contacts
                    else contacts.filter { it.name.contains(sheetSearch, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No contacts found", fontSize = 14.sp, color = OutlineVariant)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filtered.forEach { contact ->
                            ContactRow(
                                contact = contact,
                                onToggle = { viewModel.onToggleContact(contact.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Scrollable content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 120.dp)
        ) {

            // ── Group icon + name ─────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selected emoji preview
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(colors = listOf(Primary.copy(alpha = 0.08f), PrimaryFixed.copy(alpha = 0.4f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedEmoji, fontSize = 44.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Group name field
                Text(
                    text = "GROUP NAME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = groupName,
                    onValueChange = { viewModel.onGroupNameChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (groupName.isEmpty()) {
                            Text(
                                text = "e.g. Trip to Paris",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = OutlineVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Emoji picker ──────────────────────────────────────────────
            Text(
                text = "CHOOSE AN ICON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Fixed height grid — 3 rows of 6
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(viewModel.emojis) { emoji ->
                    val isSelected = emoji == selectedEmoji
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Primary.copy(alpha = 0.12f)
                                else SurfaceLowest
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Primary else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Members header ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ADD MEMBERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                if (addedCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$addedCount added",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ── Sync button ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Primary.copy(alpha = 0.06f))
                    .clickable { if (!isSyncing) syncPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isSyncing) "Syncing…" else "Sync contacts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                    Text(
                        text = "Find your contacts who use SplitPay",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (contacts.isEmpty()) {
                // ── Empty state ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceLowest)
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap \"Sync contacts\" to find\nyour friends on SplitPay",
                        fontSize = 13.sp,
                        color = OutlineVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // ── Preview (3 premiers) ──────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewContacts.forEach { contact ->
                        ContactRow(
                            contact = contact,
                            onToggle = { viewModel.onToggleContact(contact.id) }
                        )
                    }
                }

                // ── See all button → ouvre sheet ──────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLowest)
                        .clickable { showAllSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "See all (${contacts.size} contacts)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = OutlineVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            } // end if (contacts.isEmpty()) else

            // ── Error dialog ──────────────────────────────────────────────
            if (createError != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Error", fontWeight = FontWeight.Bold) },
                    text  = { Text(createError!!) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "New Group",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                TextButton(
                    onClick = { viewModel.createGroup { groupId -> onGroupCreated(groupId) } },
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Create",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLoading) Primary.copy(alpha = 0.4f) else Primary
                    )
                }
            }
        }

        // ── Bottom CTA ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(50), spotColor = Primary.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(
                            colors = if (isLoading)
                                listOf(Primary.copy(alpha = 0.6f), PrimaryContainer.copy(alpha = 0.6f))
                            else
                                listOf(Primary, PrimaryContainer)
                        )
                    )
                    .then(if (!isLoading) Modifier.clickable { viewModel.createGroup { groupId -> onGroupCreated(groupId) } } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (addedCount > 0) "Create Group · $addedCount member${if (addedCount > 1) "s" else ""}"
                               else "Create Group",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// ── Contact Row ───────────────────────────────────────────────────────────────
@Composable
private fun ContactRow(contact: Contact, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLowest)
            .then(if (contact.isOnApp) Modifier.clickable { onToggle() } else Modifier)
            .padding(16.dp),
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
                        if (contact.isOnApp) Primary.copy(alpha = 0.1f)
                        else OutlineVariant.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.first().toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (contact.isOnApp) Primary else OnSurfaceVariant
                )
            }
            Column {
                Text(
                    text = contact.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (contact.isOnApp) OnSurface else OnSurfaceVariant.copy(alpha = 0.5f)
                )
                if (!contact.isOnApp) {
                    Text(
                        text = "Not on SplitPay",
                        fontSize = 11.sp,
                        color = OutlineVariant
                    )
                }
            }
        }

        if (contact.isOnApp) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (contact.isAdded) Primary else Color.Transparent)
                    .border(2.dp, if (contact.isAdded) Primary else OutlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (contact.isAdded) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Primary.copy(alpha = 0.08f))
                    .clickable { /* TODO: envoyer invitation */ }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
        }
    }
}
