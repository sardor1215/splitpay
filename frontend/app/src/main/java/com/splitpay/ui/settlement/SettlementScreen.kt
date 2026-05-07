package com.splitpay.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun SettlementScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettlementViewModel = viewModel()
) {
    val groupName  by viewModel.groupName.collectAsStateWithLifecycle()
    val payments   by viewModel.payments.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
    val error      by viewModel.error.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    val totalAmount    = payments.sumOf { it.amount }
    val pendingCount   = payments.count { !it.isConfirmed }

    var selectedMethod by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) { viewModel.loadSettlement(groupId) }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Surface
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Scrollable content ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 96.dp, bottom = 200.dp)
            ) {

                // ── Summary ───────────────────────────────────────────────
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
                        text = if (payments.isEmpty() && !isLoading)
                            "All balances are settled! 🎉"
                        else
                            "Total amount to balance the group accounts.",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(36.dp))
                    }
                } else if (payments.isEmpty()) {
                    // All settled empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceLowest)
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "All settled up!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No outstanding balances in this group.",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // ── Suggested Payments ────────────────────────────────
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
                                currentUserId = currentUserId,
                                onConfirm = { viewModel.confirmPayment(payment.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Info note ─────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLow)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                "Trustworthy Transactions",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Confirming a payment records it and updates all balances instantly. Make sure funds have been transferred first.",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // ── Saved Methods ─────────────────────────────────────
                    Text(
                        "PAYMENT METHODS",
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
                        val bankSelected = selectedMethod == "bank"
                        Box(
                            modifier = Modifier
                                .weight(1f).height(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (bankSelected) Primary.copy(alpha = 0.08f) else SurfaceLowest)
                                .then(if (bankSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(20.dp)) else Modifier)
                                .clickable { selectedMethod = if (bankSelected) null else "bank" }
                                .padding(20.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(Icons.Default.AccountBalance, null, tint = Primary, modifier = Modifier.size(26.dp))
                                Text("Direct Bank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (bankSelected) Primary else OnSurface)
                            }
                        }
                        val paypalSelected = selectedMethod == "paypal"
                        Box(
                            modifier = Modifier
                                .weight(1f).height(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (paypalSelected) Primary.copy(alpha = 0.08f) else SurfaceLowest)
                                .then(if (paypalSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(20.dp)) else Modifier)
                                .clickable { selectedMethod = if (paypalSelected) null else "paypal" }
                                .padding(20.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("P", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Primary)
                                Text("PayPal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (paypalSelected) Primary else OnSurface)
                            }
                        }
                    }
                }
            }

            // ── Header ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Text("Settle Up", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Primary, letterSpacing = (-0.5).sp)
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // ── Bottom panel ──────────────────────────────────────────────
            if (payments.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Primary.copy(0.1f))
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(SurfaceLowest)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("REMAINING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, letterSpacing = 1.sp)
                                Text(
                                    "$${String.format("%.2f", payments.filter { !it.isConfirmed }.sumOf { it.amount })}",
                                    fontSize = 18.sp, fontWeight = FontWeight.Black, color = OnSurface
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(0.3f)))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TO SETTLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, letterSpacing = 1.sp)
                                Text(
                                    "$pendingCount Payment${if (pendingCount != 1) "s" else ""}",
                                    fontSize = 18.sp, fontWeight = FontWeight.Black, color = OnSurface
                                )
                            }
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth().height(56.dp)
                                    .shadow(8.dp, RoundedCornerShape(50), spotColor = Primary.copy(0.3f))
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                                    .clickable { viewModel.confirmAll() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Confirm All Payments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Icon(Icons.Default.DoneAll, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Settlement Card ────────────────────────────────────────────────────────────
@Composable
fun SettlementCard(
    payment: SettlementPayment,
    currentUserId: String,
    onConfirm: () -> Unit
) {
    val isYourDebt = payment.isYourDebt
    val owesYou    = payment.owesYou

    val label = when {
        isYourDebt -> "PAY ${payment.toName.uppercase()}"
        owesYou    -> "${payment.fromName.uppercase()} OWES YOU"
        else       -> "${payment.fromName.uppercase()} → ${payment.toName.uppercase()}"
    }
    val avatarName = when {
        isYourDebt -> payment.toName
        else       -> payment.fromName
    }
    val accentColor = when {
        payment.isConfirmed -> Color(0xFF1B6D24)
        isYourDebt          -> Tertiary
        owesYou             -> Color(0xFF1B6D24)
        else                -> OutlineVariant
    }
    val canConfirm = !payment.isConfirmed && (isYourDebt || owesYou)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp).height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarName.firstOrNull()?.toString() ?: "?",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary
                    )
                }
                Column {
                    Text(
                        text = label,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant, letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "$${String.format("%.2f", payment.amount)}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface
                    )
                }
            }

            if (canConfirm || payment.isConfirmed) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (payment.isConfirmed) Color(0xFF1B6D24).copy(alpha = 0.1f)
                            else if (isYourDebt) Tertiary.copy(alpha = 0.08f)
                            else Primary.copy(alpha = 0.08f)
                        )
                        .clickable(enabled = canConfirm) { onConfirm() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            payment.isConfirmed -> "Done ✓"
                            isYourDebt          -> "I Paid"
                            else                -> "Received"
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = when {
                            payment.isConfirmed -> Color(0xFF1B6D24)
                            isYourDebt          -> Tertiary
                            else                -> Primary
                        }
                    )
                }
            }
        }
    }
}
