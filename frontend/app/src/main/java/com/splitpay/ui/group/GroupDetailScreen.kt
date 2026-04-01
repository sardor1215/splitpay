package com.splitpay.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.model.Expense
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
    val group        by viewModel.group.collectAsStateWithLifecycle()
    val expenses     by viewModel.expenses.collectAsStateWithLifecycle()
    val settlements  by viewModel.settlements.collectAsStateWithLifecycle()
    val yourBalance  by viewModel.yourBalance.collectAsStateWithLifecycle()
    val totalSpending by viewModel.totalSpending.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()

    var showMembersSheet by remember { mutableStateOf(false) }
    val membersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

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
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (member.isOnApp) Primary.copy(alpha = 0.1f)
                                            else OutlineVariant.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.first().toString(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (member.isOnApp) Primary else OnSurfaceVariant
                                    )
                                }
                                Column {
                                    Text(
                                        text = member.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (member.isOnApp) OnSurface else OnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    if (!member.isOnApp) {
                                        Text("Not on SplitPay", fontSize = 11.sp, color = OutlineVariant)
                                    }
                                }
                            }
                            if (!member.isOnApp) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Primary.copy(alpha = 0.08f))
                                        .clickable { }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-8).dp),
                        modifier = Modifier.clickable { showMembersSheet = true }
                    ) {
                        group?.members?.take(4)?.forEachIndexed { index, member ->
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
                                    text = member.first().toString(),
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
                        if ((group?.members?.size ?: 0) > 4) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${(group?.members?.size ?: 0) - 4}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceVariant
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
                                text = group?.members?.firstOrNull() ?: "",
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
                IconButton(onClick = { }) {
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