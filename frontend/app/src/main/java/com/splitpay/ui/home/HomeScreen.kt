package com.splitpay.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.model.Group
import com.splitpay.viewmodel.HomeViewModel

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val Secondary = Color(0xFF1B6D24)
private val Tertiary = Color(0xFF84000C)
private val Surface = Color(0xFFF9F9FC)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val SurfaceContainerLow = Color(0xFFF3F3F6)
private val SurfaceContainerHigh = Color(0xFFE8E8EA)
private val OnSurface = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant = Color(0xFFBEC8C9)

@Composable
fun HomeScreen(
    onNavigateToGroup: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToArchivedGroups: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToSettlement: (String) -> Unit,
    homeViewModel: HomeViewModel = viewModel()
) {
    val groups by homeViewModel.groups.collectAsStateWithLifecycle()
    val archivedGroups by homeViewModel.archivedGroups.collectAsStateWithLifecycle()
    val totalOwed by homeViewModel.totalOwed.collectAsStateWithLifecycle()
    val totalOwe by homeViewModel.totalOwe.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            homeViewModel.loadGroups()
            homeViewModel.startPolling()
        }
    }
    DisposableEffect(lifecycleOwner) {
        onDispose { homeViewModel.stopPolling() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Main content ──────────────────────────────────────────────────
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
                    text = "TOTAL BALANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.2f", totalOwed - totalOwe)}",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-2).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Owe / Owed cards ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BalanceCard(
                        modifier = Modifier.weight(1f),
                        label = "YOU OWE",
                        amount = totalOwe,
                        accentColor = Tertiary
                    )
                    BalanceCard(
                        modifier = Modifier.weight(1f),
                        label = "YOU ARE OWED",
                        amount = totalOwed,
                        accentColor = Secondary
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Groups header ─────────────────────────────────────────────
            item {
                // Archived card first (if any)
                if (archivedGroups.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceContainerLowest)
                            .clickable { onNavigateToArchivedGroups() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(OnSurfaceVariant.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text("Archived", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                                    Text(
                                        text = "${archivedGroups.size} group${if (archivedGroups.size > 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = archivedGroups.take(3).joinToString("  ") { it.emoji },
                                fontSize = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

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

            // ── Group items (max 5) ───────────────────────────────────────
            items(groups.take(5)) { group ->
                GroupItem(
                    group = group,
                    onClick = { onNavigateToGroup(group.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Quick Settle section ──────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QuickSettleCard(onReviewSettle = { onNavigateToSettlement("2") })
            }
        }

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
                        text = "A",
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
            onTabSelected = { index ->
                selectedTab = index
                when (index) {
                    1 -> onNavigateToGroups()
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
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
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

// ── Group Item ────────────────────────────────────────────────────────────────
@Composable
fun GroupItem(
    group: Group,
    onClick: () -> Unit
) {
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
                        text = "${group.memberCount} member${if (group.memberCount != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
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

// ── Quick Settle Card ─────────────────────────────────────────────────────────
@Composable
fun QuickSettleCard(onReviewSettle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
            .padding(20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚡", fontSize = 18.sp)
                Text(
                    text = "Quick Settle",
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You have pending debts from Apartment Expenses. Settle now to keep your accounts balanced.",
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary, PrimaryContainer)
                        )
                    )
                    .clickable { onReviewSettle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Review & Settle",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ── Bottom Navigation ─────────────────────────────────────────────────────────
@Composable
fun BottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Pair("Home", Icons.Default.Home),
        Pair("Groups", Icons.Default.Group),
        Pair("Activity", Icons.Default.Notifications),
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
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Primary else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Primary else OnSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    }
}