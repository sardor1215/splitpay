package com.splitpay.ui.groups

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.viewmodel.AppContact
import com.splitpay.viewmodel.CreateGroupViewModel
import com.splitpay.viewmodel.DeviceContact

private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val PrimaryFixed     = Color(0xFFE0E0FF)
private val Secondary        = Color(0xFF1B6D24)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

private const val INVITE_MESSAGE = "Hey! Join me on SplitPay to split expenses easily 🎉\n" +
        "Download the app: https://splitpay.app/download"

@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit = {},
    viewModel: CreateGroupViewModel = viewModel()
) {
    val context           = LocalContext.current
    val groupName         by viewModel.groupName.collectAsStateWithLifecycle()
    val selectedEmoji     by viewModel.selectedEmoji.collectAsStateWithLifecycle()
    val appContacts       by viewModel.appContacts.collectAsStateWithLifecycle()
    val nonAppContacts    by viewModel.nonAppContacts.collectAsStateWithLifecycle()
    val isLoadingContacts by viewModel.isLoadingContacts.collectAsStateWithLifecycle()
    val selectedCount     = appContacts.count { it.isSelected }
    var showAllContacts   by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.loadContacts(context.contentResolver) }

    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }

    if (showAllContacts) {
        AllContactsDialog(
            appContacts    = appContacts,
            nonAppContacts = nonAppContacts,
            onToggle       = { viewModel.onToggleContact(it) },
            onInvite       = { phone ->
                val msg = "$INVITE_MESSAGE"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }
                context.startActivity(Intent.createChooser(intent, "Invite via…"))
            },
            onDismiss = { showAllContacts = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 120.dp)
        ) {

            // ── Group icon + name ─────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Primary.copy(alpha = 0.08f), PrimaryFixed.copy(alpha = 0.4f)))),
                    contentAlignment = Alignment.Center
                ) { Text(text = selectedEmoji, fontSize = 44.sp) }

                Spacer(modifier = Modifier.height(24.dp))
                Text("GROUP NAME", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = groupName,
                    onValueChange = { viewModel.onGroupNameChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = OnSurface, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (groupName.isEmpty()) Text("e.g. Trip to Paris", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            color = OutlineVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        inner()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Emoji picker ──────────────────────────────────────────────
            Text("CHOOSE AN ICON", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().height(176.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(viewModel.emojis) { emoji ->
                    val isSel = emoji == selectedEmoji
                    Box(
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) Primary.copy(alpha = 0.12f) else SurfaceLowest)
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) Primary else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { viewModel.onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = emoji, fontSize = 22.sp) }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── On SplitPay ───────────────────────────────────────────────
            if (isLoadingContacts) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                }
            } else {
                // Section: On SplitPay
                if (appContacts.isNotEmpty()) {
                    SectionHeader(title = "ON SPLITPAY", badge = if (selectedCount > 0) "$selectedCount selected" else null)
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        appContacts.take(5).forEach { contact ->
                            AppContactRow(contact = contact, onToggle = { viewModel.onToggleContact(contact.userId) })
                        }
                    }
                    if (appContacts.size > 5 || nonAppContacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SeeAllButton(
                            label = "See all ${appContacts.size + nonAppContacts.size} contacts",
                            onClick = { showAllContacts = true }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Section: Not on app
                if (nonAppContacts.isNotEmpty()) {
                    SectionHeader(title = "INVITE TO SPLITPAY")
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nonAppContacts.take(3).forEach { contact ->
                            InviteContactRow(contact = contact, onInvite = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, INVITE_MESSAGE)
                                }
                                context.startActivity(Intent.createChooser(intent, "Invite ${contact.name} via…"))
                            })
                        }
                    }
                    if (appContacts.isEmpty() && nonAppContacts.size > 3) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SeeAllButton(label = "See all ${nonAppContacts.size} contacts", onClick = { showAllContacts = true })
                    }
                }

                // Empty state
                if (appContacts.isEmpty() && nonAppContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Primary.copy(alpha = 0.06f)).padding(16.dp)) {
                        Text("No contacts found. Make sure you've granted contact permission.",
                            fontSize = 14.sp, color = OnSurfaceVariant, lineHeight = 20.sp)
                    }
                }
            }
        }

        // ── Header ────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().background(SurfaceLowest)
            .windowInsetsPadding(WindowInsets.statusBars).height(64.dp).align(Alignment.TopCenter)) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Primary, modifier = Modifier.size(22.dp))
                }
                Text("New Group", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Primary, letterSpacing = (-0.5).sp)
                TextButton(onClick = { viewModel.createGroup { groupId -> onGroupCreated(groupId) } }) {
                    Text("Create", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                }
            }
        }

        // ── Bottom CTA ────────────────────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .shadow(12.dp, RoundedCornerShape(50), spotColor = Primary.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                    .clickable { viewModel.createGroup { groupId -> onGroupCreated(groupId) } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedCount > 0) "Create Group · $selectedCount member${if (selectedCount > 1) "s" else ""}"
                           else "Create Group",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
        }
    }
}

// ── All Contacts Dialog ───────────────────────────────────────────────────────
@Composable
private fun AllContactsDialog(
    appContacts: List<AppContact>,
    nonAppContacts: List<DeviceContact>,
    onToggle: (String) -> Unit,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val filteredApp = remember(query, appContacts) {
        if (query.isBlank()) appContacts
        else appContacts.filter { c ->
            c.name.contains(query, ignoreCase = true) ||
            c.phone.contains(query, ignoreCase = true) ||
            c.email.contains(query, ignoreCase = true)
        }
    }
    val filteredNon = remember(query, nonAppContacts) {
        if (query.isBlank()) nonAppContacts
        else nonAppContacts.filter { c ->
            c.name.contains(query, ignoreCase = true) ||
            c.phone.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Surface)) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Box(modifier = Modifier.fillMaxWidth().background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.statusBars).height(64.dp)) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onDismiss() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Primary, modifier = Modifier.size(22.dp))
                        }
                        Text("All Contacts", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Primary, letterSpacing = (-0.5).sp)
                        val sel = appContacts.count { it.isSelected }
                        if (sel > 0) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("$sel selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        } else { Spacer(modifier = Modifier.width(80.dp)) }
                    }
                }

                // Search bar
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp)).background(SurfaceLowest)
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = OutlineVariant, modifier = Modifier.size(20.dp))
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = OnSurface),
                            cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Search by name, phone or email…", fontSize = 15.sp, color = OutlineVariant)
                                inner()
                            }
                        )
                    }
                }

                LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    if (filteredApp.isNotEmpty()) {
                        item { SectionHeader(title = "ON SPLITPAY") }
                        items(filteredApp) { contact ->
                            AppContactRow(contact = contact, onToggle = { onToggle(contact.userId) })
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (filteredNon.isNotEmpty()) {
                        item { SectionHeader(title = "INVITE TO SPLITPAY") }
                        items(filteredNon) { contact ->
                            InviteContactRow(contact = contact, onInvite = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, INVITE_MESSAGE)
                                }
                                context.startActivity(Intent.createChooser(intent, "Invite ${contact.name} via…"))
                            })
                        }
                    }

                    if (filteredApp.isEmpty() && filteredNon.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No results for \"$query\"", color = OnSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
        if (badge != null) {
            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
        }
    }
}

@Composable
private fun SeeAllButton(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Primary.copy(alpha = 0.07f)).clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
    }
}

@Composable
private fun AppContactRow(contact: AppContact, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceLowest)
        .clickable { onToggle() }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center) {
                Text(contact.name.firstOrNull()?.toString() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
            Column {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(contact.phone, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
        Box(modifier = Modifier.size(26.dp).clip(CircleShape)
            .background(if (contact.isSelected) Primary else Color.Transparent)
            .border(2.dp, if (contact.isSelected) Primary else OutlineVariant, CircleShape),
            contentAlignment = Alignment.Center) {
            if (contact.isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun InviteContactRow(contact: DeviceContact, onInvite: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceLowest)
        .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(OutlineVariant.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Text(contact.name.firstOrNull()?.toString() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
            }
            Column {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(contact.phone, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(50))
            .background(Secondary.copy(alpha = 0.1f)).clickable { onInvite() }
            .padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Invite", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Secondary)
        }
    }
}
