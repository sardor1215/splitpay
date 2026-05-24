package com.splitpay.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.CreateSpaceRequest
import com.splitpay.data.network.GroupMemberResponse
import com.splitpay.data.network.SpaceParticipantInput
import com.splitpay.viewmodel.SpaceUiState
import com.splitpay.viewmodel.SpaceViewModel

private val Primary               = Color(0xFF2B348D)
private val PrimaryContainer      = Color(0xFF444DA6)
private val Secondary             = Color(0xFF1B6D24)
private val Surface               = Color(0xFFF9F9FC)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val SurfaceContainerLow   = Color(0xFFF3F3F6)
private val OnSurface             = Color(0xFF1A1C1E)
private val OnSurfaceVariant      = Color(0xFF3F4949)
private val OutlineVariant        = Color(0xFFBEC8C9)
private val ErrorColor            = Color(0xFFBA1A1A)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateSpaceScreen(
    groupId: String,
    members: List<GroupMemberResponse>,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onSpaceCreated: (spaceId: String) -> Unit,
    vm: SpaceViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var name           by remember { mutableStateOf("") }
    var category       by remember { mutableStateOf("autre") }
    var totalAmount    by remember { mutableStateOf("") }
    var splitMode      by remember { mutableStateOf("equally") }
    var settlementMode by remember { mutableStateOf("PAY") }
    var dueDate        by remember { mutableStateOf("") }
    var launcherId     by remember { mutableStateOf(currentUserId) }

    val selectedParticipants = remember { mutableStateMapOf<String, Boolean>() }
    val participantShares    = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(members) {
        members.forEach { m ->
            if (!selectedParticipants.containsKey(m.userId))
                selectedParticipants[m.userId] = true
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is SpaceUiState.Success) vm.resetState()
    }

    // ── Date picker ────────────────────────────────────────────────────────────
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        dueDate = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }
                }) { Text("OK", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        if (members.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Primary)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 140.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Space name ─────────────────────────────────────────────
                FormSection(label = "SPACE NAME") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = OnSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isEmpty()) Text(
                                    "Ex: Barcelona trip",
                                    fontSize = 16.sp,
                                    color = OutlineVariant
                                )
                                inner()
                            }
                        )
                    }
                }

                // ── Category ───────────────────────────────────────────────
                FormSection(label = "CATEGORY") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpaceViewModel.categories.forEach { cat ->
                            val selected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Primary else SurfaceContainerLow)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) Color.Transparent else OutlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { category = cat }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    SpaceViewModel.categoryLabel(cat),
                                    fontSize = 13.sp,
                                    color = if (selected) Color.White else OnSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // ── Total amount ───────────────────────────────────────────
                FormSection(label = "TOTAL AMOUNT (€)") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        BasicTextField(
                            value = totalAmount,
                            onValueChange = { totalAmount = it.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (totalAmount.isEmpty()) Text(
                                    "0.00",
                                    fontSize = 20.sp,
                                    color = OutlineVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                inner()
                            }
                        )
                    }
                }

                // ── Split mode ─────────────────────────────────────────────
                val selectedCount = selectedParticipants.count { it.value }
                val amtCents = ((totalAmount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                val equallyAllowed = selectedCount > 0 && amtCents > 0 && amtCents % selectedCount == 0L

                // Auto-switch away from equally if constraint no longer met
                LaunchedEffect(equallyAllowed) {
                    if (!equallyAllowed && splitMode == "equally") splitMode = "exact"
                }

                FormSection(label = "SPLIT MODE") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpaceViewModel.splitModes.forEach { mode ->
                            val selected  = splitMode == mode
                            val disabled  = mode == "equally" && !equallyAllowed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            selected  -> Primary
                                            disabled  -> SurfaceContainerLow.copy(alpha = 0.5f)
                                            else      -> SurfaceContainerLow
                                        }
                                    )
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) Color.Transparent else OutlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable(enabled = !disabled) { splitMode = mode }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    SpaceViewModel.splitModeLabel[mode] ?: mode,
                                    fontSize = 13.sp,
                                    color = when {
                                        selected -> Color.White
                                        disabled -> OutlineVariant
                                        else     -> OnSurfaceVariant
                                    },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (!equallyAllowed && amtCents > 0 && selectedCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "€${totalAmount} cannot be split equally among $selectedCount participants",
                            fontSize = 11.sp,
                            color = ErrorColor
                        )
                    }
                }

                // ── Settlement mode ────────────────────────────────────────
                FormSection(label = "SETTLEMENT MODE") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PAY" to "Immediate (PAY)", "PLAN" to "Deferred (PLAN)").forEach { (v, label) ->
                            val selected = settlementMode == v
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Primary else SurfaceContainerLow)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) Color.Transparent else OutlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { settlementMode = v }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    color = if (selected) Color.White else OnSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (settlementMode == "PLAN") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerLow)
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (dueDate.isNotBlank()) dueDate else "Select due date",
                                fontSize = 15.sp,
                                color = if (dueDate.isNotBlank()) OnSurface else OutlineVariant,
                                fontWeight = if (dueDate.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                            )
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (dueDate.isNotBlank()) Primary else OutlineVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Participants ───────────────────────────────────────────
                FormSection(label = "PARTICIPANTS (${selectedParticipants.count { it.value }})") {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        members.forEachIndexed { index, member ->
                            if (index > 0) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedParticipants[member.userId] =
                                            !(selectedParticipants[member.userId] ?: true)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            member.name.firstOrNull()?.uppercase() ?: "?",
                                            color = Primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            member.name + if (member.userId == currentUserId) " (you)" else "",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = OnSurface
                                        )
                                        if (member.userId == launcherId) {
                                            Text("Launcher", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                val checked = selectedParticipants[member.userId] ?: true
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (checked) Primary else SurfaceContainerLow)
                                        .border(
                                            width = if (checked) 0.dp else 1.5.dp,
                                            color = if (checked) Color.Transparent else OutlineVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Payment launcher ───────────────────────────────────────
                val selectedMembers = members.filter { selectedParticipants[it.userId] == true }
                if (selectedMembers.isNotEmpty()) {
                    FormSection(label = "PAYMENT LAUNCHER") {
                        Text(
                            "Only the creator or this person can trigger the payment.",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            selectedMembers.forEachIndexed { index, member ->
                                if (index > 0) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                                val isSelected = launcherId == member.userId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launcherId = member.userId }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Primary.copy(alpha = 0.15f)
                                                    else SurfaceContainerLow
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                member.name.firstOrNull()?.uppercase() ?: "?",
                                                color = if (isSelected) Primary else OnSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Text(
                                            member.name + if (member.userId == currentUserId) " (you)" else "",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Primary else OnSurface,
                                            fontSize = 15.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Error ──────────────────────────────────────────────────
                if (uiState is SpaceUiState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ErrorColor.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Text(
                            (uiState as SpaceUiState.Error).message,
                            color = ErrorColor,
                            fontSize = 13.sp
                        )
                    }
                }

                // ── Share breakdown (exact / percentage) ──────────────────
                val selectedMembers2 = members.filter { selectedParticipants[it.userId] == true }
                if (splitMode != "equally" && selectedMembers2.isNotEmpty()) {
                    val amt = totalAmount.toDoubleOrNull() ?: 0.0
                    val usedTotal = selectedMembers2.sumOf { participantShares[it.userId]?.toDoubleOrNull() ?: 0.0 }
                    val remaining = if (splitMode == "exact") amt - usedTotal else 100.0 - usedTotal
                    val unit = if (splitMode == "exact") "€" else "%"
                    val label = if (splitMode == "exact") "EXACT AMOUNTS (€)" else "PERCENTAGES (%)"

                    FormSection(label = label) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedMembers2.forEach { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        member.name + if (member.userId == currentUserId) " (you)" else "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OnSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainerLow)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(unit, fontSize = 14.sp, color = OnSurfaceVariant)
                                        BasicTextField(
                                            value = participantShares[member.userId] ?: "",
                                            onValueChange = { v ->
                                                participantShares[member.userId] = v.filter { c -> c.isDigit() || c == '.' }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 14.sp, color = OnSurface, fontWeight = FontWeight.SemiBold),
                                            cursorBrush = SolidColor(Primary),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if ((participantShares[member.userId] ?: "").isEmpty())
                                                    Text("0.00", fontSize = 14.sp, color = OutlineVariant)
                                                inner()
                                            }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Remaining", fontSize = 13.sp, color = OnSurfaceVariant)
                                Text(
                                    "$unit${"%.2f".format(remaining)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (kotlin.math.abs(remaining) < 0.01) Secondary else ErrorColor
                                )
                            }
                        }
                    }
                }

                // ── Submit ─────────────────────────────────────────────────
                val selectedMembers3 = members.filter { selectedParticipants[it.userId] == true }
                val amt0 = totalAmount.toDoubleOrNull() ?: 0.0
                val sharesValid = when (splitMode) {
                    "exact" -> {
                        val total = selectedMembers3.sumOf { participantShares[it.userId]?.toDoubleOrNull() ?: 0.0 }
                        kotlin.math.abs(total - amt0) < 0.01
                    }
                    "percentage" -> {
                        val total = selectedMembers3.sumOf { participantShares[it.userId]?.toDoubleOrNull() ?: 0.0 }
                        kotlin.math.abs(total - 100.0) < 0.01
                    }
                    else -> true
                }
                val pts = selectedMembers3.map { member ->
                    val share = when (splitMode) {
                        "exact"      -> participantShares[member.userId]?.toDoubleOrNull()
                        "percentage" -> {
                            val pct = participantShares[member.userId]?.toDoubleOrNull() ?: 0.0
                            amt0 * pct / 100.0
                        }
                        else -> null
                    }
                    SpaceParticipantInput(member.userId, share)
                }
                val canSubmit = uiState !is SpaceUiState.Loading
                    && name.isNotBlank()
                    && totalAmount.isNotBlank()
                    && totalAmount.toDoubleOrNull() != null
                    && pts.isNotEmpty()
                    && sharesValid

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (canSubmit) Brush.linearGradient(listOf(Primary, PrimaryContainer))
                            else Brush.linearGradient(listOf(Primary.copy(0.4f), PrimaryContainer.copy(0.4f)))
                        )
                        .clickable(enabled = canSubmit) {
                            val amt = totalAmount.toDoubleOrNull() ?: return@clickable
                            vm.createSpace(
                                groupId,
                                CreateSpaceRequest(
                                    name           = name.trim(),
                                    category       = category,
                                    totalAmount    = amt,
                                    splitMode      = splitMode,
                                    settlementMode = settlementMode,
                                    dueDate        = dueDate.takeIf { it.isNotBlank() },
                                    launcherId     = launcherId.takeIf { it.isNotBlank() },
                                    participants   = pts
                                )
                            ) { space -> onSpaceCreated(space.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState is SpaceUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Create Expense", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Primary
                    )
                }
                Text("New Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun FormSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLowest)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 1.5.sp
        )
        content()
    }
}
