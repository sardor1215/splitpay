package com.splitpay.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
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
import com.splitpay.viewmodel.SettlementPayment
import com.splitpay.viewmodel.SettlementViewModel

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val PrimaryFixed     = Color(0xFFE0E0FF)
private val Tertiary         = Color(0xFF84000C)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

@Composable
fun SettlementScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettlementViewModel = viewModel()
) {
    val groupName by viewModel.groupName.collectAsStateWithLifecycle()
    val payments  by viewModel.payments.collectAsStateWithLifecycle()

    // Recompute on each recomposition
    val totalAmount    = payments.sumOf { it.amount }
    val remainingAmount = payments.filter { !it.isConfirmed }.sumOf { it.amount }
    val pendingCount   = payments.count { !it.isConfirmed }

    LaunchedEffect(groupId) { viewModel.loadSettlement(groupId) }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Scrollable content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 200.dp)
        ) {

            // ── Summary ───────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = groupName.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$${String.format("%.2f", totalAmount)}",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-2).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total amount to balance the group accounts.",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Suggested Payments ────────────────────────────────────────
            Text(
                text = "SUGGESTED PAYMENTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                payments.forEach { payment ->
                    SettlementCard(
                        payment = payment,
                        onConfirm = { viewModel.confirmPayment(payment.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Info note ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLow)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Trustworthy Transactions",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Confirming a payment notifies the recipient. Ensure you have transferred the funds via your preferred banking method first.",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Saved Methods ─────────────────────────────────────────────
            Text(
                text = "SAVED METHODS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bank
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceLowest)
                        .clickable { }
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Direct Bank",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                }
                // PayPal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceLowest)
                        .clickable { }
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "P",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Primary
                        )
                        Text(
                            text = "PayPal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                }
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
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Primary
                    )
                }
                Text(
                    text = "Settlement",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                // Avatar
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryFixed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Y",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }
        }

        // ── Bottom floating panel ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Primary.copy(alpha = 0.1f))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(SurfaceLowest)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "REMAINING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$${String.format("%.2f", remainingAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = OnSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(OutlineVariant.copy(alpha = 0.3f))
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TO SETTLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$pendingCount Payment${if (pendingCount != 1) "s" else ""}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = OnSurface
                        )
                    }
                }

                // Confirm All button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50), spotColor = Primary.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                        )
                        .clickable { viewModel.confirmAll() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Confirm All Payments",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Handle indicator
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(OutlineVariant.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ── Settlement Card ────────────────────────────────────────────────────────────
@Composable
fun SettlementCard(
    payment: SettlementPayment,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
            .padding(16.dp)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (payment.isConfirmed) Color(0xFF1B6D24) else Tertiary)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = payment.memberName.first().toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
                Column {
                    Text(
                        text = "PAY ${payment.memberName.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "$${String.format("%.2f", payment.amount)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (payment.isConfirmed) Color(0xFF1B6D24).copy(alpha = 0.1f)
                        else Primary.copy(alpha = 0.08f)
                    )
                    .clickable(enabled = !payment.isConfirmed) { onConfirm() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (payment.isConfirmed) "Done ✓" else "Confirm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (payment.isConfirmed) Color(0xFF1B6D24) else Primary
                )
            }
        }
    }
}
