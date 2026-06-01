package com.splitpay.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.model.Group
import com.splitpay.data.network.GroupDebtorResponse
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.AddableContact
import com.splitpay.viewmodel.HomeViewModel
import com.splitpay.viewmodel.PayState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGroup: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeViewModel.fetchGroups()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-refresh every 60 seconds — cancelled automatically when screen leaves composition
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            homeViewModel.fetchGroups()
        }
    }

    val groups         by homeViewModel.groups.collectAsStateWithLifecycle()
    val accountBalance by homeViewModel.accountBalance.collectAsStateWithLifecycle()
    val pendingCount   by homeViewModel.pendingCount.collectAsStateWithLifecycle()
    val totalOwed  = groups.filter { it.balance > 0 }.sumOf { it.balance }
    val totalOwe   = groups.filter { it.balance < 0 }.sumOf { -it.balance }
    var selectedTab by remember { mutableIntStateOf(0) }

    val isLoading           by homeViewModel.isLoading.collectAsStateWithLifecycle()
    val context             = LocalContext.current
    val contacts            by homeViewModel.splitPayContacts.collectAsStateWithLifecycle()
    val isLoadingContacts   by homeViewModel.isLoadingContacts.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) homeViewModel.loadSplitPayContacts(context.contentResolver) }

    var showOweSheet      by remember { mutableStateOf(false) }
    var showOwedSheet     by remember { mutableStateOf(false) }
    var showSendSheet     by remember { mutableStateOf(false) }
    val sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var payDialogGroup by remember { mutableStateOf<Group?>(null) }
    val debtors        by homeViewModel.debtors.collectAsStateWithLifecycle()
    val payState       by homeViewModel.payState.collectAsStateWithLifecycle()

    // Refresh groups + close dialog after successful payment
    LaunchedEffect(payState) {
        if (payState is PayState.Success) {
            homeViewModel.fetchGroups()
            payDialogGroup = null
            homeViewModel.resetPayState()
        }
    }

    if (payDialogGroup != null) {
        PayDialog(
            group          = payDialogGroup!!,
            debtors        = debtors,
            payState       = payState,
            accountBalance = accountBalance,
            onPay          = { toUserId, amount -> homeViewModel.directPay(toUserId, amount) },
            onDismiss      = { payDialogGroup = null; homeViewModel.resetPayState() }
        )
    }

    // ── Bottom sheets ──────────────────────────────────────────────────────
    if (showOweSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOweSheet = false },
            sheetState       = sheetState,
            containerColor   = SurfaceContainerLowest,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            BalanceSheet(
                title         = "You owe",
                groups        = groups.filter { it.balance < 0 },
                amountSign    = -1.0,
                showPayButton = true,
                onPayClick    = { group ->
                    showOweSheet = false
                    payDialogGroup = group
                    homeViewModel.loadDebtors(group.id)
                },
                onDismiss     = { showOweSheet = false }
            )
        }
    }

    if (showOwedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOwedSheet = false },
            sheetState       = sheetState,
            containerColor   = SurfaceContainerLowest,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            BalanceSheet(
                title         = "Owed to you",
                groups        = groups.filter { it.balance > 0 },
                amountSign    = 1.0,
                showPayButton = false,
                onPayClick    = null,
                onDismiss     = { showOwedSheet = false }
            )
        }
    }

    if (showSendSheet) {
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
        ModalBottomSheet(
            onDismissRequest = { showSendSheet = false; homeViewModel.resetPayState() },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = SurfaceContainerLowest,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            SendMoneySheet(
                availableBalance  = accountBalance,
                contacts          = contacts,
                isLoadingContacts = isLoadingContacts,
                payState          = payState,
                onSendTo          = { toUserId, amount, note ->
                    homeViewModel.directPay(toUserId, amount, note)
                },
                onDismiss = { showSendSheet = false; homeViewModel.resetPayState() }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Main content ──────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { homeViewModel.fetchGroups(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 140.dp, bottom = 140.dp)
        ) {

            // ── Balance section ───────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ACCOUNT BALANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.2f", accountBalance)}",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-2).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 3 balance cards ───────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BalanceCard(
                        modifier    = Modifier.weight(1f),
                        label       = "YOU OWE",
                        amount      = totalOwe,
                        accentColor = OnSurface,
                        onClick     = { if (totalOwe > 0) showOweSheet = true }
                    )
                    BalanceCard(
                        modifier    = Modifier.weight(1f),
                        label       = "OWED TO YOU",
                        amount      = totalOwed,
                        accentColor = OnSurface,
                        onClick     = { if (totalOwed > 0) showOwedSheet = true }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Quick actions ─────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                        )
                        .clickable { showSendSheet = true }
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Send Money",
                                fontSize   = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White
                            )
                            Text(
                                "Transfer directly to anyone",
                                fontSize = 12.sp,
                                color    = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.North,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Groups header ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Groups",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    TextButton(onClick = { onNavigateToGroups() }) {
                        Text(
                            text = "View all",
                            fontSize = 13.sp,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Group items ───────────────────────────────────────────────
            items(groups.take(5)) { group ->
                GroupItem(
                    group = group,
                    onClick = { onNavigateToGroup(group.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

        }
        } // end PullToRefreshBox

        // ── Glass Top App Bar ─────────────────────────────────────────────
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
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SplitPay",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer)
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = homeViewModel.userInitial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 110.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Primary, PrimaryContainer)
                    )
                )
                .clickable { onNavigateToCreateGroup() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add expense",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // ── Bottom Navigation ─────────────────────────────────────────────
        BottomNav(
            selectedTab = selectedTab,
            notificationCount = pendingCount,
            onTabSelected = { index ->
                selectedTab = index
                when (index) {
                    1 -> onNavigateToGroups()
                    2 -> onNavigateToNotifications()
                    3 -> onNavigateToProfile()
                    else -> { }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Balance Card ──────────────────────────────────────────────────────────────
@Composable
fun BalanceCard(
    modifier: Modifier = Modifier,
    label: String,
    amount: Double,
    accentColor: Color,
    onClick: () -> Unit = {}
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Column {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$${String.format("%.2f", amount)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        }
    }
}

private fun formatActivity(iso: String): String {
    if (iso.isBlank()) return "RECENTLY"
    return try {
        val zdt = java.time.OffsetDateTime.parse(iso)
        val now = java.time.OffsetDateTime.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(zdt.toLocalDate(), now.toLocalDate())
        when {
            days == 0L  -> "TODAY"
            days == 1L  -> "YESTERDAY"
            days < 7L   -> "$days DAYS AGO"
            days < 30L  -> "${days / 7}W AGO"
            days < 365L -> "${days / 30}MO AGO"
            else        -> "${days / 365}Y AGO"
        }
    } catch (_: Exception) { "RECENTLY" }
}

// ── Group Item ────────────────────────────────────────────────────────────────
@Composable
fun GroupItem(
    group: Group,
    onClick: () -> Unit
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

    val balanceColor = when {
        group.balance > 0 -> Secondary
        group.balance < 0 -> Tertiary
        else -> OnSurfaceVariant
    }
    val balanceLabel = when {
        group.balance > 0 -> "OWED"
        group.balance < 0 -> "YOU OWE"
        else -> "SETTLED"
    }
    val balanceAmount = when {
        group.balance != 0.0 -> "$${String.format("%.2f", Math.abs(group.balance))}"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Emoji avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = group.emoji, fontSize = 24.sp)
                }
                Column {
                    Text(
                        text = group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${group.members.size} members",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatActivity(group.lastActivity),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OutlineVariant,
                    letterSpacing = 0.5.sp
                )
                if (group.balance != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = balanceLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = balanceAmount,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = balanceColor
                    )
                }
            }
        }
    }
}

// ── Bottom Navigation ─────────────────────────────────────────────────────────
@Composable
fun BottomNav(
    selectedTab: Int,
    notificationCount: Int = 0,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
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

    val tabs = listOf(
        Pair("Home", Icons.Default.Home),
        Pair("Groups", Icons.Default.Group),
        Pair("Notifications", Icons.Default.Notifications),
        Pair("Profile", Icons.Default.Person)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SurfaceContainerLowest)
            .padding(bottom = 12.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Primary.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Primary else OnSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            if (index == 2 && notificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Balance Sheet ─────────────────────────────────────────────────────────────
@Composable
private fun BalanceSheet(
    title: String,
    groups: List<Group>,
    amountSign: Double,
    showPayButton: Boolean,
    onPayClick: ((Group) -> Unit)?,
    onDismiss: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(OutlineVariant.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary)
        Spacer(Modifier.height(4.dp))
        Text(
            "${groups.size} group${if (groups.size > 1) "s" else ""}",
            fontSize = 13.sp,
            color = OnSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("All settled up! 🎉", fontSize = 15.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { group ->
                    val amount = group.balance * amountSign
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceContainerLow)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainerLowest),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(group.emoji, fontSize = 22.sp)
                            }
                            Column {
                                Text(
                                    group.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = OnSurface
                                )
                                Text(
                                    "$${String.format("%.2f", amount)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OnSurface
                                )
                            }
                        }
                        if (showPayButton && onPayClick != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                                    .clickable { onPayClick(group) }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text("Pay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Send Money Sheet ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendMoneySheet(
    availableBalance: Double,
    contacts: List<AddableContact>,
    isLoadingContacts: Boolean,
    payState: PayState,
    onSendTo: (toUserId: String, amount: Double, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant
    val Tertiary         = c.tertiary

    var selected       by remember { mutableStateOf<AddableContact?>(null) }
    var searchQuery    by remember { mutableStateOf("") }
    var amountText     by remember { mutableStateOf("") }
    var note           by remember { mutableStateOf("") }
    var isSending      by remember { mutableStateOf(false) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }
    var successBalance by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(payState) {
        when (payState) {
            is PayState.Success -> { isSending = false; successBalance = payState.newBalance }
            is PayState.Error   -> { isSending = false; errorMsg = payState.message }
            else -> {}
        }
    }

    val filtered = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Box(
            modifier = Modifier.width(40.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(OutlineVariant.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))

        when {
            // ── Success ───────────────────────────────────────────────────
            successBalance != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(60.dp))
                    Text("Transfer sent!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text("New balance: €${String.format("%.2f", successBalance)}", fontSize = 14.sp, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                            .clickable { onDismiss() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
            }

            // ── Step 2: Amount + Note ─────────────────────────────────────
            selected != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(Primary.copy(alpha = 0.08f))
                            .clickable { selected = null; amountText = ""; note = ""; errorMsg = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Send to ${selected!!.name}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Primary)
                        Text("Available: €${String.format("%.2f", availableBalance)}", fontSize = 13.sp, color = OnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))

                Text("AMOUNT (€)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OutlineVariant, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLow).padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontSize = 22.sp, color = OnSurface, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (amountText.isEmpty()) Text("0.00", fontSize = 22.sp, color = OutlineVariant, fontWeight = FontWeight.Bold)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text("NOTE (optional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OutlineVariant, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLow).padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (note.isEmpty()) Text("Dinner, rent…", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                }

                errorMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Tertiary.copy(alpha = 0.1f)).padding(14.dp)
                    ) { Text(msg, color = Tertiary, fontSize = 13.sp) }
                }

                Spacer(Modifier.height(16.dp))
                val amount = amountText.toDoubleOrNull() ?: 0.0
                val canSend = amount > 0 && !isSending
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canSend) Brush.linearGradient(listOf(Primary, PrimaryContainer))
                            else Brush.linearGradient(listOf(OutlineVariant.copy(0.3f), OutlineVariant.copy(0.3f)))
                        )
                        .clickable(enabled = canSend) {
                            isSending = true; errorMsg = null
                            onSendTo(selected!!.userId, amount, note.takeIf { it.isNotBlank() })
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending)
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.North, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Send €${String.format("%.2f", amount)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                }
            }

            // ── Step 1: Contact list ──────────────────────────────────────
            else -> {
                Text("Send Money", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary)
                Text("Available: €${String.format("%.2f", availableBalance)}", fontSize = 13.sp, color = OnSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLow).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Search, null, tint = OutlineVariant, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = OnSurface),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text("Search name or phone…", fontSize = 15.sp, color = OutlineVariant)
                                inner()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                when {
                    isLoadingContacts -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                    contacts.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("No SplitPay contacts found.\nMake sure your contacts have the app.", fontSize = 14.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                            Text("No results for \"$searchQuery\"", fontSize = 14.sp, color = OnSurfaceVariant)
                        }
                    }
                    else -> {
                        Text("ON SPLITPAY", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered) { contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(SurfaceLow).clickable { selected = contact }.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(contact.name.firstOrNull()?.toString() ?: "?",
                                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Primary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                                        Text(contact.phone, fontSize = 12.sp, color = OnSurfaceVariant)
                                    }
                                    Icon(Icons.Default.North, null, tint = Primary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Pay Dialog ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayDialog(
    group: Group,
    debtors: List<GroupDebtorResponse>,
    payState: PayState,
    accountBalance: Double,
    onPay: (toUserId: String, amount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant
    val ErrorColor       = Color(0xFFBA1A1A)
    val WarnColor        = Color(0xFFE65100)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDebtor by remember { mutableStateOf<GroupDebtorResponse?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SurfaceLowest,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Box(
                modifier = Modifier.width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OutlineVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))
            Text("Pay — ${group.name}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary)
            Spacer(Modifier.height(2.dp))
            // Always show the available balance
            Text(
                "Your balance: €${String.format("%.2f", accountBalance)}",
                fontSize = 13.sp,
                color = if (accountBalance > 0) OnSurfaceVariant else ErrorColor,
                fontWeight = if (accountBalance <= 0) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(20.dp))

            when {
                payState is PayState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                payState is PayState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(ErrorColor.copy(alpha = 0.1f)).padding(16.dp)
                    ) { Text((payState as PayState.Error).message, color = ErrorColor, fontSize = 14.sp) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
                debtors.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No pending debts in this group.", fontSize = 14.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        debtors.forEach { debtor ->
                            val isConfirming  = confirmDebtor?.userId == debtor.userId
                            val canPayFull    = accountBalance >= debtor.amount
                            val canPayPartial = !canPayFull && accountBalance > 0
                            val payAmount     = if (canPayFull) debtor.amount else accountBalance

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceLow)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Debtor info row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(debtor.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OnSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("€${String.format("%.2f", debtor.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = OnSurface)
                                    }
                                    if (!isConfirming) {
                                        // Initial Pay button — always shown, state revealed on confirm
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(
                                                    if (accountBalance > 0)
                                                        Brush.linearGradient(listOf(Primary, PrimaryContainer))
                                                    else
                                                        Brush.linearGradient(listOf(OutlineVariant.copy(0.4f), OutlineVariant.copy(0.4f)))
                                                )
                                                .clickable(enabled = accountBalance > 0) { confirmDebtor = debtor }
                                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                        ) {
                                            Text("Pay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // Confirm state — show balance scenario
                                if (isConfirming) {
                                    when {
                                        // ── Scenario 1: full balance ──────────────────────
                                        canPayFull -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { confirmDebtor = null },
                                                    modifier = Modifier.weight(1f).height(40.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) { Text("Cancel", fontSize = 13.sp) }
                                                Box(
                                                    modifier = Modifier.weight(1f).height(40.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                                                        .clickable { onPay(debtor.userId, debtor.amount); confirmDebtor = null },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("Confirm €${String.format("%.2f", debtor.amount)}",
                                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                        // ── Scenario 2: partial balance ───────────────────
                                        canPayPartial -> {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                    .background(WarnColor.copy(alpha = 0.1f)).padding(10.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("Insufficient balance", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarnColor)
                                                    Text(
                                                        "You have €${String.format("%.2f", accountBalance)} but owe €${String.format("%.2f", debtor.amount)}. " +
                                                        "You're €${String.format("%.2f", debtor.amount - accountBalance)} short.",
                                                        fontSize = 12.sp, color = WarnColor.copy(alpha = 0.8f), lineHeight = 16.sp
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { confirmDebtor = null },
                                                    modifier = Modifier.weight(1f).height(40.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) { Text("Cancel", fontSize = 13.sp) }
                                                Box(
                                                    modifier = Modifier.weight(1f).height(40.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Brush.linearGradient(listOf(WarnColor, WarnColor))),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize()
                                                            .clickable { onPay(debtor.userId, payAmount); confirmDebtor = null },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("Pay €${String.format("%.2f", payAmount)} (partial)",
                                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                        // ── Scenario 3: no balance ────────────────────────
                                        else -> {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                    .background(ErrorColor.copy(alpha = 0.1f)).padding(10.dp)
                                            ) {
                                                Text(
                                                    "No balance available. Top up your account to pay this debt.",
                                                    fontSize = 13.sp, color = ErrorColor, lineHeight = 18.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { confirmDebtor = null },
                                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("Close", fontSize = 13.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}