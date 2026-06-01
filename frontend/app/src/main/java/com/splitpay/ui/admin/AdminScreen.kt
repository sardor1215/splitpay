package com.splitpay.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.network.AdminGroupResponse
import com.splitpay.data.network.AdminKycDocResponse
import com.splitpay.data.network.AdminUserResponse
import com.splitpay.data.network.AmlAlertResponse
import com.splitpay.data.network.GdprConfigUpdateRequest
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.AdminViewModel

private val Warning = Color(0xFFB45309)

@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = viewModel()
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

    val stats        by viewModel.stats.collectAsStateWithLifecycle()
    val users        by viewModel.users.collectAsStateWithLifecycle()
    val groups       by viewModel.groups.collectAsStateWithLifecycle()
    val amlAlerts    by viewModel.amlAlerts.collectAsStateWithLifecycle()
    val gdprConfig   by viewModel.gdprConfig.collectAsStateWithLifecycle()
    val kycPending   by viewModel.kycPending.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val error        by viewModel.error.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Users", "Balances", "Groups", "KYC", "Alerts", "Config")
    val pendingCount    = amlAlerts.count { it.status == "pending" }
    val kycPendingCount = kycPending.size

    // Show snackbar for action results
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionResult) {
        if (actionResult != null) {
            snackbarHostState.showSnackbar(actionResult!!)
            viewModel.clearActionResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Surface
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {

            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 70.dp)) {

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = Tertiary)
                    }
                } else {
                    // Stats cards
                    stats?.let { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(Modifier.weight(1f), "Users",    "${s.totalUsers}",    Primary)
                            StatCard(Modifier.weight(1f), "Groups",   "${s.totalGroups}",   Secondary)
                            StatCard(Modifier.weight(1f), "Expenses", "${s.totalExpenses}", PrimaryContainer)
                            StatCard(Modifier.weight(1f), "Total",    "$${String.format("%.0f", s.totalAmount)}", Tertiary)
                        }
                    }

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor   = SurfaceLowest,
                        contentColor     = Primary
                    ) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick  = { selectedTab = i },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        val badgeCount = when (title) {
                                            "Alerts" -> pendingCount
                                            "KYC"    -> kycPendingCount
                                            else     -> 0
                                        }
                                        if (badgeCount > 0) {
                                            Box(
                                                modifier = Modifier.size(16.dp).clip(CircleShape).background(Tertiary),
                                                contentAlignment = Alignment.Center
                                            ) { Text("$badgeCount", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { items(users) { UserRow(it, viewModel) } }

                        1 -> BalancesTab(users, viewModel)

                        2 -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { items(groups) { GroupRow(it) } }

                        3 -> KycReviewTab(kycPending, viewModel)

                        4 -> AmlAlertsTab(amlAlerts, viewModel)

                        5 -> GdprConfigTab(gdprConfig, viewModel)
                    }
                }
            }

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(70.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Text("Admin Dashboard", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { viewModel.load() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ── Balances Tab ───────────────────────────────────────────────────────────

@Composable
private fun BalancesTab(users: List<AdminUserResponse>, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    // Dialog state
    var target    by remember { mutableStateOf<AdminUserResponse?>(null) }
    var mode      by remember { mutableStateOf("add") }   // "set" | "add" | "subtract"
    var amountStr by remember { mutableStateOf("") }
    var note      by remember { mutableStateOf("") }

    if (target != null) {
        AlertDialog(
            onDismissRequest = { target = null; amountStr = ""; note = "" },
            containerColor   = SurfaceLow,
            title = {
                Column {
                    Text("Manage Balance", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = 17.sp)
                    Text(target!!.name, fontSize = 13.sp, color = OnSurfaceVariant)
                    Text("Current: $${String.format("%.2f", target!!.accountBalance)}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (target!!.accountBalance >= 0) Primary else Tertiary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Mode selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("add" to "Add", "subtract" to "Subtract", "set" to "Set to").forEach { (key, label) ->
                            val selected = mode == key
                            val color = when (key) {
                                "add"      -> Secondary
                                "subtract" -> Tertiary
                                else       -> Primary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) color else color.copy(alpha = 0.08f))
                                    .clickable { mode = key }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else color
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value         = amountStr,
                        onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label         = { Text("Amount (USD)") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier      = Modifier.fillMaxWidth(),
                        leadingIcon   = { Text("$", fontWeight = FontWeight.Bold, color = OnSurfaceVariant) }
                    )
                    OutlinedTextField(
                        value         = note,
                        onValueChange = { note = it },
                        label         = { Text("Note (optional)") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    // Preview of new balance
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    val preview = when (mode) {
                        "set"      -> amt
                        "add"      -> target!!.accountBalance + amt
                        "subtract" -> maxOf(0.0, target!!.accountBalance - amt)
                        else       -> target!!.accountBalance
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary.copy(alpha = 0.06f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("New balance", fontSize = 12.sp, color = OnSurfaceVariant)
                        Text(
                            "$${String.format("%.2f", preview)}",
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = Primary
                        )
                    }
                }
            },
            confirmButton = {
                val amt = amountStr.toDoubleOrNull()
                TextButton(
                    onClick = {
                        if (amt != null && amt > 0) {
                            viewModel.updateUserBalance(target!!.id, target!!.name, mode, amt, note.ifBlank { null })
                            target = null; amountStr = ""; note = ""
                        }
                    },
                    enabled = amountStr.toDoubleOrNull()?.let { it > 0 } == true
                ) { Text("Confirm", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { target = null; amountStr = ""; note = "" }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No users", color = OnSurfaceVariant)
        }
        return
    }

    // Search / filter
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) users
                   else users.filter { it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value         = query,
            onValueChange = { query = it },
            placeholder   = { Text("Search users…", fontSize = 13.sp) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape         = RoundedCornerShape(14.dp)
        )

        // Total balance stat
        val totalBalance = users.sumOf { it.accountBalance }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Primary.copy(alpha = 0.07f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total balance across all users", fontSize = 12.sp, color = OnSurfaceVariant)
            Text("$${String.format("%.2f", totalBalance)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { user ->
                val balanceColor = if (user.accountBalance > 0) Secondary else OnSurfaceVariant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLowest)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary
                        )
                    }
                    // Name + email
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(user.email, fontSize = 11.sp, color = OnSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    // Balance
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$${String.format("%.2f", user.accountBalance)}",
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = balanceColor
                        )
                        // Recharge button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Primary.copy(alpha = 0.1f))
                                .clickable {
                                    target = user
                                    mode = "add"
                                    amountStr = ""
                                    note = ""
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                }
            }
        }
    }
}

// ── KYC Review Tab ─────────────────────────────────────────────────────────

@Composable
private fun KycReviewTab(docs: List<AdminKycDocResponse>, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    var rejectTarget by remember { mutableStateOf<AdminKycDocResponse?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    if (rejectTarget != null) {
        AlertDialog(
            onDismissRequest = { rejectTarget = null; rejectReason = "" },
            title = { Text("Reject Document") },
            text = {
                Column {
                    Text("Reason for rejection:", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("e.g. Document unclear, expired ID…") },
                        singleLine = false, maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reviewKycDocument(rejectTarget!!.id, "rejected", rejectReason.ifBlank { "Document rejected" })
                    rejectTarget = null; rejectReason = ""
                }) { Text("Reject", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null; rejectReason = "" }) { Text("Cancel") }
            }
        )
    }

    if (docs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending KYC documents", color = OnSurfaceVariant)
        }
        return
    }

    val context = LocalContext.current
    val imageLoader = remember {
        coil.ImageLoader.Builder(context).okHttpClient(RetrofitClient.httpClient).build()
    }

    // Group documents by user
    val byUser = docs.groupBy { it.userId }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        byUser.forEach { (userId, userDocs) ->
            val user = userDocs.first()
            item(key = userId) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLowest)
                        .padding(14.dp)
                ) {
                    // User header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                            Text(user.userEmail, fontSize = 11.sp, color = OnSurfaceVariant)
                            Text("${userDocs.size} document(s) pending", fontSize = 11.sp, color = Warning)
                        }
                        // Approve All button
                        Button(
                            onClick = { viewModel.approveAllKycForUser(userId, user.userName) },
                            colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Approve All", fontSize = 12.sp) }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceLow, thickness = 1.dp)
                    Spacer(Modifier.height(10.dp))

                    // One row per document
                    userDocs.forEach { doc ->
                        var expanded by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(Warning.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            doc.docType.replace("_", " ").uppercase(),
                                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Warning
                                        )
                                    }
                                    Text(doc.createdAt.take(10), fontSize = 10.sp, color = OnSurfaceVariant.copy(alpha = 0.6f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // View/hide image
                                    Box(
                                        modifier = Modifier.size(34.dp).clip(CircleShape)
                                            .background(if (expanded) Primary.copy(alpha = 0.1f) else Color.Transparent)
                                            .clickable { expanded = !expanded },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Visibility, null,
                                            tint = if (expanded) Primary else OnSurfaceVariant,
                                            modifier = Modifier.size(18.dp))
                                    }
                                    // Reject single doc
                                    Box(
                                        modifier = Modifier.size(34.dp).clip(CircleShape)
                                            .background(Tertiary.copy(alpha = 0.06f))
                                            .clickable { rejectTarget = doc },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", fontSize = 13.sp, color = Tertiary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                val fileUrl = "${RetrofitClient.baseUrl}admin/kyc/documents/${doc.id}/file"
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(260.dp)
                                        .clip(RoundedCornerShape(10.dp)).background(SurfaceLow),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(fileUrl).crossfade(true).build(),
                                        imageLoader = imageLoader,
                                        contentDescription = doc.docType,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── AML Alerts Tab ─────────────────────────────────────────────────────────

@Composable
private fun AmlAlertsTab(alerts: List<AmlAlertResponse>, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val OnSurfaceVariant = c.onSurfaceVariant

    var filter by remember { mutableStateOf("pending") }
    val filtered = if (filter == "all") alerts else alerts.filter { it.status == filter }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("pending", "cleared", "suspended", "all").forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick  = { filter = f; viewModel.loadAmlAlerts(if (f == "all") null else f) },
                    label    = { Text(f.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                )
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No $filter alerts", color = OnSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { alert ->
                    AlertRow(alert, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AmlAlertResponse, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    val typeColor = when (alert.alertType) {
        "large_transaction" -> Primary
        "high_frequency"    -> Warning
        "new_account"       -> PrimaryContainer
        else                -> OnSurfaceVariant
    }
    val statusColor = when (alert.status) {
        "pending"   -> Warning
        "cleared"   -> Secondary
        "suspended" -> Tertiary
        else        -> OnSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(alert.alertType.replace("_", " ").uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = typeColor)
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(alert.status.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(alert.userName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(alert.description, fontSize = 11.sp, color = OnSurfaceVariant)
                if (alert.amount != null) {
                    Text("Amount: ${"%.2f".format(alert.amount)}", fontSize = 11.sp, color = typeColor, fontWeight = FontWeight.Medium)
                }
                Text(alert.createdAt.take(10), fontSize = 10.sp, color = OnSurfaceVariant.copy(alpha = 0.6f))
            }
        }

        if (alert.status == "pending") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.reviewAlert(alert.id, "cleared") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
                ) { Text("Clear", fontSize = 12.sp) }
                Button(
                    onClick = { viewModel.reviewAlert(alert.id, "suspended") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Tertiary)
                ) { Text("Suspend User", fontSize = 12.sp) }
            }
        }
    }
}

// ── GDPR Config Tab ────────────────────────────────────────────────────────

@Composable
private fun GdprConfigTab(config: Map<String, String>, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val Primary  = c.primary
    val Tertiary = c.tertiary

    var archiveMonths    by remember(config) { mutableStateOf(config["archive_after_months"] ?: "12") }
    var deleteMonths     by remember(config) { mutableStateOf(config["delete_after_months"] ?: "24") }
    var largeThreshold   by remember(config) { mutableStateOf(config["large_transaction_threshold"] ?: "1000") }
    var freqCount        by remember(config) { mutableStateOf(config["high_frequency_count"] ?: "10") }
    var freqWindow       by remember(config) { mutableStateOf(config["high_frequency_window_hours"] ?: "24") }
    var newAccDays       by remember(config) { mutableStateOf(config["new_account_days"] ?: "30") }
    var autoSuspend      by remember(config) { mutableStateOf(config["auto_suspend_after_alerts"] ?: "3") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("GDPR Retention", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
        }
        item {
            ConfigField("Archive inactive accounts after (months)", archiveMonths) { archiveMonths = it }
        }
        item {
            ConfigField("Delete archived accounts after (months)", deleteMonths) { deleteMonths = it }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("AML Thresholds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Warning)
        }
        item {
            ConfigField("Large transaction threshold (USD)", largeThreshold) { largeThreshold = it }
        }
        item {
            ConfigField("High frequency — expense count", freqCount) { freqCount = it }
        }
        item {
            ConfigField("High frequency — window (hours)", freqWindow) { freqWindow = it }
        }
        item {
            ConfigField("New account age threshold (days)", newAccDays) { newAccDays = it }
        }
        item {
            ConfigField("Auto-suspend after N unreviewed alerts", autoSuspend) { autoSuspend = it }
        }
        item {
            Button(
                onClick = {
                    viewModel.updateGdprConfig(GdprConfigUpdateRequest(
                        archiveAfterMonths          = archiveMonths.toIntOrNull(),
                        deleteAfterMonths           = deleteMonths.toIntOrNull(),
                        largeTransactionThreshold   = largeThreshold.toDoubleOrNull(),
                        highFrequencyCount          = freqCount.toIntOrNull(),
                        highFrequencyWindowHours    = freqWindow.toIntOrNull(),
                        newAccountDays              = newAccDays.toIntOrNull(),
                        autoSuspendAfterAlerts      = autoSuspend.toIntOrNull()
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text("Save Configuration") }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.runGdprCleanup() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
            ) { Text("Run GDPR Cleanup Now") }
        }
    }
}

@Composable
private fun ConfigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

// ── Shared row composables ─────────────────────────────────────────────────

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun UserRow(user: AdminUserResponse, viewModel: AdminViewModel) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    val amlColor = when (user.amlStatus ?: "clear") {
        "flagged"   -> Warning
        "suspended" -> Tertiary
        else        -> Secondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(if (user.isAdmin) Primary else Primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null,
                    tint = if (user.isAdmin) Color.White else Primary,
                    modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(user.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (user.isAdmin) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    if (!user.amlStatus.isNullOrEmpty() && user.amlStatus != "clear") {
                        Box(Modifier.clip(RoundedCornerShape(4.dp))
                            .background(amlColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text(user.amlStatus.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = amlColor)
                        }
                    }
                }
                Text(user.email, fontSize = 11.sp, color = OnSurfaceVariant)
            }
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (user.isVerified) Secondary.copy(alpha = 0.1f) else Tertiary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (user.isVerified) "✓" else "✗",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (user.isVerified) Secondary else Tertiary
                )
            }
        }
        // Balance row
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Balance: $${String.format("%.2f", user.accountBalance)}",
                fontSize = 12.sp,
                color = if (user.accountBalance > 0) Secondary else OnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Lift suspension button — only shown for auto-suspended users
        if (user.amlStatus == "suspended") {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { viewModel.liftSuspension(user.id, user.name) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
            ) { Text("Lift Suspension", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun GroupRow(group: AdminGroupResponse) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceLowest).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(Primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) { Text(group.emoji, fontSize = 20.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${group.memberCount} members · ${group.expenseCount} expenses", fontSize = 11.sp, color = OnSurfaceVariant)
        }
        Text("$${String.format("%.2f", group.totalAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
    }
}
