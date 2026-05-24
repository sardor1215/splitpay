package com.splitpay.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.SpaceResponse
import com.splitpay.viewmodel.SpaceUiState
import com.splitpay.viewmodel.SpaceViewModel

private val Primary               = Color(0xFF2B348D)
private val PrimaryContainer      = Color(0xFF444DA6)
private val Surface               = Color(0xFFF9F9FC)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val OnSurface             = Color(0xFF1A1C1E)
private val OnSurfaceVariant      = Color(0xFF3F4949)
private val OutlineVariant        = Color(0xFFBEC8C9)

@Composable
fun SpaceListScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onSpaceClick: (spaceId: String) -> Unit,
    onCreateSpace: () -> Unit,
    vm: SpaceViewModel = viewModel()
) {
    val spaces  by vm.spaces.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) { vm.loadSpaces(groupId) }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        when {
            uiState is SpaceUiState.Loading && spaces.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Primary
                )
            }
            spaces.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🗂️", fontSize = 48.sp)
                    Text(
                        "No spaces yet",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = Primary
                    )
                    Text(
                        "Create a shared payment space for this group",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                            .clickable { onCreateSpace() }
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Create a space",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 140.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "${spaces.size} SPACE${if (spaces.size > 1) "S" else ""}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    items(spaces) { space ->
                        SpaceCard(space = space, onClick = { onSpaceClick(space.id) })
                    }
                }
            }
        }

        // ── Top Bar ───────────────────────────────────────────────────────────
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Primary)
                }
                Text("Expenses", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        if (spaces.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 32.dp)
                    .size(60.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Primary.copy(0.3f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                    .clickable { onCreateSpace() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create space",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SpaceCard(space: SpaceResponse, onClick: () -> Unit) {
    val statusColor = when (space.status) {
        "active"    -> Color(0xFF1B6D24)
        "settling"  -> Primary
        "settled"   -> Color(0xFF1B6D24)
        "cancelled" -> Color(0xFFBA1A1A)
        "suspended" -> Color(0xFFE65100)
        else        -> OnSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(space.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OnSurface)
                Text(
                    SpaceViewModel.categoryLabel(space.category),
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            SpaceViewModel.statusLabel(space.status),
                            fontSize = 11.sp,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (space.myAcceptanceStatus == "pending" && space.status == "pending_acceptance") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFE65100).copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Response needed",
                                fontSize = 11.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (space.myQuorumStatus == "pending") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Primary.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Confirm quorum",
                                fontSize = 11.sp,
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                space.dueDate?.let {
                    Text(
                        "Due ${it.take(10)}",
                        fontSize = 11.sp,
                        color = OutlineVariant,
                        fontWeight = FontWeight.Medium
                    )
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
                    Text(
                        "YOUR SHARE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = OutlineVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        "€%.2f".format(it),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }
        }
    }
}
