package com.splitpay.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.ExpenseActivityResponse
import com.splitpay.data.network.ExpenseResponse
import com.splitpay.viewmodel.ExpenseDetailViewModel

private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val Secondary        = Color(0xFF1B6D24)
private val Tertiary         = Color(0xFF84000C)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)

private val categoryEmoji = mapOf(
    "food" to "🍕", "transport" to "🚗", "accommodation" to "🏠",
    "entertainment" to "🎮", "shopping" to "🛒", "health" to "💊",
    "utilities" to "💡", "settlement" to "💸", "other" to "📦"
)

@Composable
fun ExpenseDetailScreen(
    groupId: String,
    expenseId: String,
    onNavigateBack: () -> Unit,
    viewModel: ExpenseDetailViewModel = viewModel()
) {
    LaunchedEffect(expenseId) { viewModel.load(groupId, expenseId) }

    val expense    by viewModel.expense.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
    val error      by viewModel.error.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = Tertiary)
            }
        } else if (expense != null) {
            val e = expense!!
            val userId = viewModel.currentUserId
            val myParticipant = e.participants.find { it.userId == userId }
            val myShare = myParticipant?.let { p ->
                if (e.paidBy == userId) e.amount - p.share else -p.share
            } ?: 0.0

            LazyColumn(
                contentPadding = PaddingValues(
                    top = 150.dp, bottom = 32.dp,
                    start = 20.dp, end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero card
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(categoryEmoji[e.category?.lowercase() ?: "other"] ?: "📦", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(e.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text("$${String.format("%.2f", e.amount)}", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip((e.category ?: "other").replaceFirstChar { c -> c.uppercase() })
                                Chip((e.splitMode ?: "equally").replaceFirstChar { c -> c.uppercase() })
                                Chip(e.createdAt.take(10))
                            }
                        }
                    }
                }

                // My share banner
                if (myShare != 0.0) {
                    item {
                        val (bannerColor, bannerText) = if (myShare > 0)
                            Secondary to "You get back $${String.format("%.2f", myShare)}"
                        else
                            Tertiary to "You owe $${String.format("%.2f", -myShare)}"
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bannerColor.copy(alpha = 0.1f))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Your share", fontSize = 14.sp, color = bannerColor, fontWeight = FontWeight.Medium)
                            Text(bannerText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = bannerColor)
                        }
                    }
                }

                // Paid by
                item {
                    SectionLabel("PAID BY")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLowest)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                e.paidByName.firstOrNull()?.toString() ?: "?",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                        Column {
                            Text(e.paidByName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                            Text("Paid the full amount", fontSize = 12.sp, color = OnSurfaceVariant)
                        }
                    }
                }

                // Participants breakdown
                item { SectionLabel("SPLIT BREAKDOWN") }
                items(e.participants) { participant ->
                    val isMe = participant.userId == userId
                    val pct  = if (e.amount > 0) (participant.share / e.amount * 100).toInt() else 0
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isMe) Primary.copy(alpha = 0.06f) else SurfaceLowest)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(participant.name.firstOrNull()?.toString() ?: "?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isMe) "${participant.name} (You)" else participant.name,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface
                            )
                            // Share bar
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).padding(top = 4.dp),
                                color = if (isMe) Primary else Primary.copy(alpha = 0.4f),
                                trackColor = SurfaceLow
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${String.format("%.2f", participant.share)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isMe) Primary else OnSurface)
                            Text("$pct%", fontSize = 11.sp, color = OnSurfaceVariant)
                        }
                    }
                }

                // Activity timeline
                if (activities.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel("ACTIVITY")
                    }
                    items(activities) { activity ->
                        ActivityRow(activity)
                    }
                }
            }
        }

        // Header
        Box(
            modifier = Modifier.fillMaxWidth().background(SurfaceLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(70.dp).align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Text("Expense Detail", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                Spacer(Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: ExpenseActivityResponse) {
    val (icon, color) = when (activity.action) {
        "created" -> Icons.Default.Create to Secondary
        "updated" -> Icons.Default.Create to Primary
        else      -> Icons.Default.Person to OnSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timeline dot
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(activity.userName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(activity.action.replaceFirstChar { it.uppercase() }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            if (activity.details != null) {
                Text(activity.details, fontSize = 12.sp, color = OnSurfaceVariant, lineHeight = 16.sp)
            }
            Text(activity.createdAt.take(16).replace("T", " "), fontSize = 10.sp, color = OnSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium) }
}
