package com.splitpay.ui.expense

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import com.splitpay.viewmodel.AddExpenseViewModel
import com.splitpay.viewmodel.Participant
import com.splitpay.viewmodel.SplitMode

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary           = Color(0xFF2B348D)
private val PrimaryContainer  = Color(0xFF444DA6)
private val Secondary         = Color(0xFF1B6D24)
private val Surface           = Color(0xFFF9F9FC)
private val SurfaceLowest     = Color(0xFFFFFFFF)
private val SurfaceLow        = Color(0xFFF3F3F6)
private val OnSurface         = Color(0xFF1A1C1E)
private val OnSurfaceVariant  = Color(0xFF3F4949)
private val OutlineVariant    = Color(0xFFBEC8C9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = viewModel()
) {
    val amount       by viewModel.amount.collectAsStateWithLifecycle()
    val description  by viewModel.description.collectAsStateWithLifecycle()
    val paidBy       by viewModel.paidBy.collectAsStateWithLifecycle()
    val splitMode    by viewModel.splitMode.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    var showPaidBySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Paid By bottom sheet ──────────────────────────────────────────────
    if (showPaidBySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPaidBySheet = false },
            sheetState = sheetState,
            containerColor = SurfaceLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Title
                Text(
                    text = "Who paid?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Select the person who covered this expense",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Search bar
                var paidBySearch by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLow)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
                        value = paidBySearch,
                        onValueChange = { paidBySearch = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (paidBySearch.isEmpty()) Text("Search…", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                    if (paidBySearch.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { paidBySearch = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val filteredParticipants = remember(participants, paidBySearch) {
                    if (paidBySearch.isBlank()) participants
                    else participants.filter { it.name.contains(paidBySearch, ignoreCase = true) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredParticipants.forEach { participant ->
                        val isSelected = participant.name == paidBy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) Primary.copy(alpha = 0.08f)
                                    else Surface
                                )
                                .then(
                                    if (participant.isOnApp)
                                        Modifier.clickable {
                                            viewModel.onPaidByChange(participant.name)
                                            showPaidBySheet = false
                                        }
                                    else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (participant.isOnApp) Primary.copy(alpha = 0.1f)
                                            else OutlineVariant.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = participant.name.first().toString(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (participant.isOnApp) Primary else OnSurfaceVariant
                                    )
                                }
                                Column {
                                    Text(
                                        text = participant.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (participant.isOnApp) OnSurface
                                                else OnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    if (!participant.isOnApp) {
                                        Text(
                                            text = "Not on SplitPay",
                                            fontSize = 11.sp,
                                            color = OutlineVariant
                                        )
                                    }
                                }
                            }

                            if (participant.isOnApp) {
                                // Checkmark si sélectionné
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Primary else Color.Transparent)
                                        .border(2.dp, if (isSelected) Primary else OutlineVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                // Bouton Invite
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Primary.copy(alpha = 0.08f))
                                        .clickable { /* TODO: envoyer invitation */ }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Invite",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {

        // ── Scrollable content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 120.dp)
        ) {

            // ── Amount input ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AMOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        color = Primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { viewModel.onAmountChange(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            fontFamily = InterFontFamily,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Primary),
                        decorationBox = { innerTextField ->
                            if (amount.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary.copy(alpha = 0.15f),
                                    fontFamily = InterFontFamily,
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.width(220.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Description ───────────────────────────────────────────────
            Column {
                Text(
                    text = "WHAT WAS IT FOR?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface,
                        fontFamily = InterFontFamily
                    ),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        if (description.isEmpty()) {
                            Text(
                                text = "Enter a description",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = OutlineVariant,
                                fontFamily = InterFontFamily
                            )
                        }
                        innerTextField()
                    }
                )
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Paid by ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLow)
                    .clickable { showPaidBySheet = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PAID BY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = paidBy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Primary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Split mode ────────────────────────────────────────────────
            Column {
                Text(
                    text = "SPLIT MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLow)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SplitMode.entries.forEach { mode ->
                        val isSelected = splitMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    elevation = if (isSelected) 4.dp else 0.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = Primary.copy(alpha = 0.12f)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected)
                                        Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                                    else
                                        Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                                )
                                .clickable { viewModel.onSplitModeChange(mode) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Participants ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPLIT WITH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "SELECT ALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { viewModel.onSelectAll() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                participants.forEach { participant ->
                    ParticipantRow(
                        participant = participant,
                        onToggle = { viewModel.onToggleParticipant(participant.id) }
                    )
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
                    text = "Add Expense",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                TextButton(onClick = {
                    viewModel.saveExpense { onNavigateBack() }
                }) {
                    Text(
                        text = "Save",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                }
            }
        }

        // ── Save button ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(50), spotColor = Primary.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary, PrimaryContainer)
                        )
                    )
                    .clickable { viewModel.saveExpense { onNavigateBack() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Expense",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ── Participant Row ────────────────────────────────────────────────────────────
@Composable
fun ParticipantRow(
    participant: Participant,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLowest)
            .clickable { onToggle() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (participant.isIncluded) Secondary
                        else OutlineVariant.copy(alpha = 0.3f)
                    )
            )
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.name.first().toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Column {
                Text(
                    text = participant.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = OnSurface
                )
                Text(
                    text = if (participant.isIncluded) "Included" else "Excluded",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (participant.isIncluded) Secondary else OutlineVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (participant.isIncluded && participant.share > 0) {
                Text(
                    text = "$${String.format("%.2f", participant.share)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (participant.isIncluded) Primary else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (participant.isIncluded) Primary else OutlineVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (participant.isIncluded) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
