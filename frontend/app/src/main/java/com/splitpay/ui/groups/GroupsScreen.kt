package com.splitpay.ui.groups

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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
private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val Secondary        = Color(0xFF1B6D24)
private val Tertiary         = Color(0xFF84000C)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

@Composable
fun GroupsScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToGroup: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToArchived: () -> Unit = {},
    archivedOnly: Boolean = false,
    viewModel: HomeViewModel = viewModel()
) {
    val groups         by viewModel.groups.collectAsStateWithLifecycle()
    val archivedGroups by viewModel.archivedGroups.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadGroups()
            viewModel.startPolling()
        }
    }
    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.stopPolling() }
    }

    val sourceList = if (archivedOnly) archivedGroups else groups
    val filteredGroups = remember(sourceList, searchQuery) {
        if (searchQuery.isBlank()) sourceList
        else sourceList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val settledCount = filteredGroups.count { it.balance == 0.0 }
    val activeCount  = filteredGroups.count { it.balance != 0.0 }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Content ───────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 156.dp, bottom = if (archivedOnly) 32.dp else 120.dp)
        ) {

            // ── Stats row ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(
                        label = "${filteredGroups.size} ${if (archivedOnly) "Archived" else "Groups"}",
                        color = if (archivedOnly) OnSurfaceVariant else Primary
                    )
                    if (!archivedOnly) {
                        StatPill(label = "$activeCount Active", color = Secondary)
                        StatPill(label = "$settledCount Settled", color = OutlineVariant)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Archived card (only on normal groups screen) ──────────────
            if (!archivedOnly && archivedGroups.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLowest)
                            .clickable { onNavigateToArchived() }
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
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(OnSurfaceVariant.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Archived",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${archivedGroups.size} group${if (archivedGroups.size > 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = archivedGroups.take(3).joinToString("  ") { it.emoji },
                                fontSize = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ── Group list ────────────────────────────────────────────────
            if (filteredGroups.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (archivedOnly) "📦" else "🔍",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (archivedOnly) "No archived groups" else "No groups found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (archivedOnly) "Archive a group from its settings"
                                   else "Try a different search term",
                            fontSize = 14.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredGroups) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onNavigateToGroup(group.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // ── Header ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .align(Alignment.TopCenter)
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = if (archivedOnly) 8.dp else 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (archivedOnly) {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Primary)
                    }
                }
                Text(
                    text = if (archivedOnly) "Archived Groups" else "My Groups",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp,
                    modifier = if (archivedOnly) Modifier.weight(1f).padding(start = 4.dp) else Modifier
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { searchActive = !searchActive },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Search bar (visible when active)
            if (searchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLow)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurface
                        ),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search groups...",
                                    fontSize = 15.sp,
                                    color = OutlineVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────
        if (!archivedOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 110.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                    )
                    .clickable { onNavigateToCreateGroup() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New group",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // ── Bottom Navigation ─────────────────────────────────────────
            GroupsBottomNav(
                onTabSelected = { index ->
                    when (index) {
                        0 -> onNavigateToHome()
                        3 -> onNavigateToProfile()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ── Stat Pill ─────────────────────────────────────────────────────────────────
@Composable
private fun StatPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ── Group Card ────────────────────────────────────────────────────────────────
@Composable
private fun GroupCard(group: Group, onClick: () -> Unit) {
    val balanceColor = when {
        group.balance > 0 -> Secondary
        group.balance < 0 -> Tertiary
        else -> OutlineVariant
    }
    val balanceLabel = when {
        group.balance > 0 -> "OWED"
        group.balance < 0 -> "YOU OWE"
        else -> "SETTLED"
    }
    val balanceAmount = if (group.balance != 0.0)
        "$${String.format("%.2f", Math.abs(group.balance))}"
    else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
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
                        .background(SurfaceLow),
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
                    text = group.lastActivity.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OutlineVariant,
                    letterSpacing = 0.5.sp
                )
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

// ── Bottom Navigation ─────────────────────────────────────────────────────────
@Composable
private fun GroupsBottomNav(
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
            .background(SurfaceLowest)
            .padding(bottom = 12.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = index == 1 // Groups is always active here
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
