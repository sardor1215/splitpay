package com.splitpay.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdmin: () -> Unit = {},
    onNavigateToKyc: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
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

    val userName           by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail          by viewModel.userEmail.collectAsStateWithLifecycle()
    val userPhone          by viewModel.userPhone.collectAsStateWithLifecycle()
    val totalBalance       by viewModel.totalBalance.collectAsStateWithLifecycle()
    val groupCount         by viewModel.groupCount.collectAsStateWithLifecycle()
    val darkMode           by viewModel.darkMode.collectAsStateWithLifecycle()
    val isAdmin            by viewModel.isAdmin.collectAsStateWithLifecycle()
    val kycStatus          by viewModel.kycStatus.collectAsStateWithLifecycle()
    val requireConsent     by viewModel.requireConsent.collectAsStateWithLifecycle()
    val pendingInvitations by viewModel.pendingInvitations.collectAsStateWithLifecycle()
    val payments           by viewModel.payments.collectAsStateWithLifecycle()
    val accountBalance     by viewModel.accountBalance.collectAsStateWithLifecycle()
    val isLoading          by viewModel.isLoading.collectAsStateWithLifecycle()

    val initials = userName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")

    var showEditDialog   by remember { mutableStateOf(false) }
    var editName         by remember { mutableStateOf("") }
    var editPhone        by remember { mutableStateOf("") }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var deleteErrorMsg    by remember { mutableStateOf<String?>(null) }
    var showHistorySheet  by remember { mutableStateOf(false) }

    // ── Edit Profile Dialog ────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor   = SurfaceLow,
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = OnSurface) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = editName,
                        onValueChange = { editName = it },
                        label         = { Text("Full name") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value         = editPhone,
                        onValueChange = { editPhone = it },
                        label         = { Text("Phone number") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProfile(editName, editPhone)
                    showEditDialog = false
                }) { Text("Save", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = SurfaceLow,
            title = {
                Text("Delete Account", fontWeight = FontWeight.Bold, color = Tertiary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This action is permanent and cannot be undone.",
                        fontWeight = FontWeight.SemiBold,
                        color      = OnSurface,
                        fontSize   = 14.sp
                    )
                    Text(
                        "In accordance with GDPR, your personal information will be anonymized. Your transaction history and group contributions will be retained in anonymized form.",
                        color    = OnSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Text(
                        "You cannot delete your account if you have outstanding debts.",
                        color    = Tertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount(
                        onSuccess = { viewModel.logout(onLogout) },
                        onError   = { msg -> deleteErrorMsg = msg }
                    )
                }) { Text("Delete Forever", color = Tertiary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

    // ── Delete Error Dialog ────────────────────────────────────────────────
    deleteErrorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteErrorMsg = null },
            containerColor   = SurfaceLow,
            title = { Text("Cannot Delete Account", fontWeight = FontWeight.Bold, color = OnSurface) },
            text  = { Text(msg, color = OnSurfaceVariant, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { deleteErrorMsg = null }) {
                    Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Scrollable content ────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 40.dp)
        ) {

            // ── Profile Hero ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with edit button
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .clickable {
                                editName  = userName
                                editPhone = userPhone
                                showEditDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = userName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnSurface,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userEmail,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stat chips
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip(
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = Secondary,
                        label = "$${String.format("%.2f", totalBalance)}"
                    )
                    StatChip(
                        icon = Icons.Default.Group,
                        iconColor = Primary,
                        label = "$groupCount Groups"
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Account Preferences ───────────────────────────────────────
            SectionHeader(title = "ACCOUNT PREFERENCES")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsItem(
                    icon = Icons.Default.AttachMoney,
                    title = "Currency",
                    subtitle = "Default currency for new expenses",
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "USD ($)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = OutlineVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Brightness4,
                    title = "Dark Mode",
                    subtitle = "Switch between light and dark themes",
                    trailingContent = {
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { viewModel.toggleDarkMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryContainer,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = OutlineVariant
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Communication ─────────────────────────────────────────────
            SectionHeader(title = "COMMUNICATION")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Manage push and email alerts",
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = OutlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Security & Privacy ────────────────────────────────────────
            SectionHeader(title = "SECURITY & PRIVACY")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Privacy Settings",
                subtitle = "Control who can see your activity",
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = OutlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsItem(
                icon = Icons.Default.Group,
                title = "Require consent to join",
                subtitle = if (requireConsent) "You must approve before being added to a group"
                           else "Anyone can add you to groups directly",
                onClick = { viewModel.setRequireConsent(!requireConsent) },
                trailingContent = {
                    Switch(
                        checked = requireConsent,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                    )
                }
            )

            // ── Pending invitations ────────────────────────────────────────
            if (pendingInvitations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeader(title = "PENDING INVITATIONS")
                Spacer(modifier = Modifier.height(12.dp))
                pendingInvitations.forEach { inv ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(SurfaceLowest)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(inv.emoji, fontSize = 28.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(inv.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(inv.subtitle, fontSize = 12.sp, color = OnSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                        .background(Secondary.copy(alpha = 0.1f))
                                        .clickable { viewModel.acceptInvitation(inv) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Secondary) }
                                Box(
                                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                        .background(Tertiary.copy(alpha = 0.1f))
                                        .clickable { viewModel.declineInvitation(inv) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Decline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tertiary) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── Identity Verification (KYC) ──────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))
            SectionHeader(title = "IDENTITY VERIFICATION")
            Spacer(modifier = Modifier.height(12.dp))
            val (kycIcon, kycSubtitle, kycColor) = when (kycStatus) {
                "approved" -> Triple(Icons.Default.Lock, "Verified — all features unlocked", Secondary)
                "pending"  -> Triple(Icons.Default.Lock, "Under review", Color(0xFFB45309))
                "rejected" -> Triple(Icons.Default.Lock, "Action required — re-upload documents", Tertiary)
                else       -> Triple(Icons.Default.Lock, "Not verified — tap to upload documents", Primary)
            }
            SettingsItem(
                icon = kycIcon,
                title = "KYC Verification",
                subtitle = kycSubtitle,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (kycStatus != "approved") {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(kycColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = kycStatus.replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = kycColor
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OutlineVariant, modifier = Modifier.size(20.dp))
                    }
                },
                onClick = onNavigateToKyc
            )

            // ── Admin ─────────────────────────────────────────────────────
            if (isAdmin) {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeader(title = "ADMINISTRATION")
                Spacer(modifier = Modifier.height(12.dp))
                SettingsItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "Admin Dashboard",
                    subtitle = "Users, groups and app statistics",
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null,
                            tint = OutlineVariant, modifier = Modifier.size(20.dp))
                    },
                    onClick = onNavigateToAdmin
                )
            }

            // ── Account Balance ───────────────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))
            SectionHeader(title = "ACCOUNT BALANCE")
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceLowest)
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Available balance", fontSize = 13.sp, color = OnSurfaceVariant)
                            Text(
                                "€${String.format("%.2f", accountBalance)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = if (accountBalance >= 0) Primary else Tertiary,
                                letterSpacing = (-1).sp
                            )
                        }
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (accountBalance >= 0) Primary.copy(0.1f) else Tertiary.copy(0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (accountBalance >= 0) Primary else Tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // ── Transaction History button ────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))
            SectionHeader(title = "TRANSACTIONS")
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.History,
                title = "Transaction History",
                subtitle = if (payments.isEmpty()) "No transactions yet"
                           else "${payments.size} transaction${if (payments.size > 1) "s" else ""}",
                onClick = { if (payments.isNotEmpty()) showHistorySheet = true },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = OutlineVariant, modifier = Modifier.size(20.dp))
                }
            )

            // ── Transaction History sheet ──────────────────────────────────
            if (showHistorySheet) {
                ModalBottomSheet(
                    onDismissRequest = { showHistorySheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SurfaceLowest,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(OutlineVariant.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transaction History", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(Primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("${payments.size} total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(payments) { payment ->
                                val isSent      = payment.direction == "sent"
                                val accentColor = if (isSent) Tertiary else Secondary
                                val sign        = if (isSent) "-" else "+"
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SurfaceLow)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                                    .background(accentColor.copy(0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (isSent) "↑" else "↓", fontSize = 18.sp, color = accentColor, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text(
                                                    if (isSent) "To ${payment.toName ?: "?"}" else "From ${payment.fromName ?: "?"}",
                                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OnSurface
                                                )
                                                payment.spaceName?.let {
                                                    Text(it, fontSize = 11.sp, color = OnSurfaceVariant)
                                                }
                                                Text(payment.paidAt.take(10), fontSize = 11.sp, color = OutlineVariant)
                                            }
                                        }
                                        Text(
                                            "$sign€${String.format("%.2f", payment.amount)}",
                                            fontWeight = FontWeight.Black, fontSize = 16.sp, color = accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Logout ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tertiary.copy(alpha = 0.05f))
                    .clickable { viewModel.logout(onLogout) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = Tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Logout from SplitPay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Delete Account ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tertiary.copy(alpha = 0.08f))
                    .clickable { showDeleteDialog = true }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Delete Account",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SplitPay v1.0.0 • Build 1",
                fontSize = 11.sp,
                color = OnSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        } // end PullToRefreshBox

        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(70.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "SplitPay",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                // Spacer to balance the row
                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ── Stat Chip ─────────────────────────────────────────────────────────────────
@Composable
private fun StatChip(icon: ImageVector, iconColor: Color, label: String) {
    val c = LocalAppColors.current
    val SurfaceLow = c.surfaceLow
    val OnSurface  = c.onSurface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
    }
}

// ── Section Header ────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    val Primary = c.primary

    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Primary.copy(alpha = 0.8f),
        letterSpacing = 1.5.sp
    )
}

// ── Settings Item ─────────────────────────────────────────────────────────────
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLowest)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }
        trailingContent()
    }
}
