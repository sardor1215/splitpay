package com.splitpay.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.ui.theme.InterFontFamily
import com.splitpay.viewmodel.EditExpenseViewModel
import com.splitpay.viewmodel.SplitMode

private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val Secondary        = Color(0xFF1B6D24)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

@Composable
fun EditExpenseScreen(
    groupId: String,
    expenseId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditExpenseViewModel = viewModel()
) {
    LaunchedEffect(expenseId) { viewModel.load(groupId, expenseId) }

    val amount       by viewModel.amount.collectAsStateWithLifecycle()
    val description  by viewModel.description.collectAsStateWithLifecycle()
    val paidBy       by viewModel.paidBy.collectAsStateWithLifecycle()
    val paidByUserId by viewModel.paidByUserId.collectAsStateWithLifecycle()
    val splitMode    by viewModel.splitMode.collectAsStateWithLifecycle()
    val category     by viewModel.category.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val error        by viewModel.error.collectAsStateWithLifecycle()

    var showPaidByDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) { if (error != null) snackbarHostState.showSnackbar(error!!) }

    if (showPaidByDialog) {
        AlertDialog(
            onDismissRequest = { showPaidByDialog = false },
            title = { Text("Who paid?", fontWeight = FontWeight.Bold, color = OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    participants.forEach { p ->
                        val isSelected = p.id == paidByUserId
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Primary.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { viewModel.onPaidByChange(p.id, p.name); showPaidByDialog = false }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Primary else OnSurface)
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = Surface) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().background(Surface).padding(scaffoldPadding)) {

            if (isLoading && participants.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp).padding(top = 140.dp, bottom = 120.dp)
                ) {
                    // Amount
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AMOUNT", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text("$", fontSize = 36.sp, fontWeight = FontWeight.Light, color = Primary.copy(alpha = 0.4f))
                            Spacer(Modifier.width(4.dp))
                            BasicTextField(
                                value = amount, onValueChange = { viewModel.onAmountChange(it) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold,
                                    color = Primary, fontFamily = InterFontFamily, textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(Primary),
                                decorationBox = { inner ->
                                    if (amount.isEmpty()) Text("0.00", fontSize = 64.sp, fontWeight = FontWeight.Bold,
                                        color = Primary.copy(alpha = 0.15f), fontFamily = InterFontFamily, textAlign = TextAlign.Center)
                                    inner()
                                },
                                modifier = Modifier.width(220.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // Description
                    Column {
                        Text("WHAT WAS IT FOR?", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = description, onValueChange = { viewModel.onDescriptionChange(it) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium,
                                color = OnSurface, fontFamily = InterFontFamily),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            decorationBox = { inner ->
                                if (description.isEmpty()) Text("Enter a description", fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium, color = OutlineVariant, fontFamily = InterFontFamily)
                                inner()
                            }
                        )
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    }

                    Spacer(Modifier.height(28.dp))

                    // Paid by
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLow).clickable { showPaidByDialog = true }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(PrimaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("PAID BY", fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.sp)
                                Text(paidBy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Primary)
                    }

                    Spacer(Modifier.height(28.dp))

                    // Category
                    Column {
                        Text("CATEGORY", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(12.dp))
                        val categories = listOf(
                            "food" to "🍕", "transport" to "🚗", "accommodation" to "🏠",
                            "entertainment" to "🎮", "shopping" to "🛒", "health" to "💊",
                            "utilities" to "💡", "other" to "📦"
                        )
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { (key, emoji) ->
                                val isSelected = category == key
                                Box(
                                    modifier = Modifier
                                        .shadow(if (isSelected) 4.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = Primary.copy(alpha = 0.12f))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Primary else SurfaceLow)
                                        .clickable { viewModel.onCategoryChange(key) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(emoji, fontSize = 20.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(key.replaceFirstChar { it.uppercase() }, fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) Color.White else OnSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Split mode
                    Column {
                        Text("SPLIT MODE", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLow).padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SplitMode.entries.forEach { mode ->
                                val isSelected = splitMode == mode
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .shadow(if (isSelected) 4.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = Primary.copy(alpha = 0.12f))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Brush.linearGradient(listOf(Primary, PrimaryContainer))
                                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                        )
                                        .clickable { viewModel.onSplitModeChange(mode) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else OnSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Participants
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("SPLIT WITH", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
                        Text("SELECT ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Primary, letterSpacing = 1.sp,
                            modifier = Modifier.clickable { viewModel.onSelectAll() })
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        participants.forEach { participant ->
                            EditParticipantRow(
                                participant     = participant,
                                splitMode       = splitMode,
                                onToggle        = { viewModel.onToggleParticipant(participant.id) },
                                onExactChange   = { viewModel.onExactShareChange(participant.id, it) },
                                onPercentChange = { viewModel.onPercentChange(participant.id, it) }
                            )
                        }
                    }

                    if (splitMode != SplitMode.EQUALLY) {
                        val total     = amount.toDoubleOrNull() ?: 0.0
                        val sumShares = participants.filter { it.isIncluded }.sumOf { it.share }
                        val remaining = total - sumShares
                        if (total > 0 && kotlin.math.abs(remaining) > 0.01) {
                            Spacer(Modifier.height(8.dp))
                            val label = if (splitMode == SplitMode.EXACT) "Remaining: $${"%.2f".format(remaining)}"
                            else "Remaining: ${"%.1f".format(remaining / total * 100)}%"
                            Text(label, fontSize = 12.sp,
                                color = if (remaining > 0) Color(0xFFB45309) else Color(0xFF84000C),
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Header
            Box(
                modifier = Modifier.fillMaxWidth().background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.statusBars).height(64.dp).align(Alignment.TopCenter)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, null, tint = Primary)
                    }
                    Text("Edit Expense", fontSize = 18.sp, fontWeight = FontWeight.Black,
                        color = Primary, letterSpacing = (-0.5).sp)
                    TextButton(onClick = { viewModel.saveExpense(groupId, expenseId) { onNavigateBack() } }) {
                        Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                    }
                }
            }

            // Save button
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .shadow(12.dp, RoundedCornerShape(50), spotColor = Primary.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(
                            if (isLoading) listOf(Primary.copy(alpha = 0.6f), PrimaryContainer.copy(alpha = 0.6f))
                            else listOf(Primary, PrimaryContainer)
                        ))
                        .clickable(enabled = !isLoading) {
                            viewModel.saveExpense(groupId, expenseId) { onNavigateBack() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    else Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun EditParticipantRow(
    participant: com.splitpay.viewmodel.Participant,
    splitMode: SplitMode = SplitMode.EQUALLY,
    onToggle: () -> Unit,
    onExactChange: (String) -> Unit = {},
    onPercentChange: (String) -> Unit = {}
) {
    var exactInput   by remember(participant.id, splitMode) { mutableStateOf(if (splitMode == SplitMode.EXACT && participant.share > 0) participant.share.toString() else "") }
    var percentInput by remember(participant.id, splitMode) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(SurfaceLowest).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).clickable { onToggle() }) {
                Box(modifier = Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (participant.isIncluded) Secondary else OutlineVariant.copy(alpha = 0.3f)))
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center) {
                    Text(participant.name.first().toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
                Column {
                    Text(participant.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OnSurface)
                    Text(
                        text = when {
                            !participant.isIncluded -> "Excluded"
                            splitMode == SplitMode.EQUALLY && participant.share > 0 -> "$${String.format("%.2f", participant.share)}"
                            else -> "Included"
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (participant.isIncluded) Secondary else OutlineVariant
                    )
                }
            }
            Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(if (participant.isIncluded) Primary else Color.Transparent)
                .border(2.dp, if (participant.isIncluded) Primary else OutlineVariant, CircleShape)
                .clickable { onToggle() }, contentAlignment = Alignment.Center) {
                if (participant.isIncluded) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        if (participant.isIncluded && splitMode != SplitMode.EQUALLY) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(SurfaceLow).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (splitMode == SplitMode.EXACT) "$" else "%",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary)
                BasicTextField(
                    value = if (splitMode == SplitMode.EXACT) exactInput else percentInput,
                    onValueChange = {
                        if (splitMode == SplitMode.EXACT) { exactInput = it; onExactChange(it) }
                        else { percentInput = it; onPercentChange(it) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if ((if (splitMode == SplitMode.EXACT) exactInput else percentInput).isEmpty())
                            Text(if (splitMode == SplitMode.EXACT) "0.00" else "0", fontSize = 15.sp, color = OutlineVariant)
                        inner()
                    }
                )
                if (participant.share > 0 && splitMode == SplitMode.PERCENT)
                    Text("= $${String.format("%.2f", participant.share)}", fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
    }
}
