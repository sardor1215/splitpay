package com.splitpay.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.splitpay.data.network.EditSpaceRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.SpaceResponse
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
private val Amber                 = Color(0xFFE65100)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpaceDetailScreen(
    groupId: String,
    spaceId: String,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    vm: SpaceViewModel = viewModel()
) {
    val space    by vm.currentSpace.collectAsStateWithLifecycle()
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()
    val audit    by vm.audit.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAssistDialog  by remember { mutableStateOf<String?>(null) }
    var showAudit         by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showEditSheet     by remember { mutableStateOf(false) }
    val editSheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        vm.loadSpace(groupId, spaceId)
        vm.loadAudit(groupId, spaceId)
    }
    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        if (space == null) {
            if (uiState is SpaceUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Primary)
            } else {
                Text(
                    "Space not found",
                    modifier = Modifier.align(Alignment.Center),
                    color = OnSurfaceVariant
                )
            }
        } else {
            val s          = space!!
            val isCreator  = s.createdBy == currentUserId
            val isLauncher = s.launcherId == currentUserId

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 140.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Status banner ──────────────────────────────────────────
                StatusBanner(s)

                // ── Amount card ────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLowest)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            SpaceViewModel.categoryLabel(s.category).uppercase(),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.5.sp
                        )
                        Text(
                            "€%.2f".format(s.totalAmount),
                            fontSize = 40.sp, fontWeight = FontWeight.Black,
                            color = Primary, letterSpacing = (-1.5).sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip(SpaceViewModel.splitModeLabel[s.splitMode] ?: s.splitMode)
                            InfoChip(if (s.settlementMode == "PAY") "Immediate" else "Deferred (PLAN)")
                            s.dueDate?.let { InfoChip("Due ${it.take(10)}") }
                        }
                        if (s.myShare != null) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("YOUR SHARE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = OnSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.sp)
                                Text("€%.2f".format(s.myShare), fontSize = 22.sp,
                                    fontWeight = FontWeight.Black, color = Primary)
                            }
                        }
                    }
                }

                // ── Actions ────────────────────────────────────────────────
                ActionSection(
                    space           = s,
                    currentUserId   = currentUserId,
                    isCreator       = isCreator,
                    isLauncher      = isLauncher,
                    isLoading       = uiState is SpaceUiState.Loading,
                    onAccept        = { vm.acceptSpace(groupId, spaceId) },
                    onDecline       = { vm.declineSpace(groupId, spaceId) },
                    onForceLaunch   = { vm.forceLaunch(groupId, spaceId) },
                    onSettle        = { vm.settle(groupId, spaceId) },
                    onEarlySettle   = { vm.requestEarlySettle(groupId, spaceId) },
                    onVoteYes       = { vm.respondEarlySettle(groupId, spaceId, true) },
                    onVoteNo        = { vm.respondEarlySettle(groupId, spaceId, false) },
                    onConfirmQuorum = { vm.confirmQuorum(groupId, spaceId) },
                    onRejectQuorum  = { vm.rejectQuorum(groupId, spaceId) }
                )

                // ── Participants ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLowest)
                        .padding(20.dp)
                ) {
                    Column {
                        Text("PARTICIPANTS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(16.dp))
                        s.participants.forEachIndexed { index, p ->
                            if (index > 0) HorizontalDivider(
                                color = OutlineVariant.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(CircleShape)
                                            .background(Primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(p.name.firstOrNull()?.uppercase() ?: "?",
                                            color = Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(p.name + if (p.userId == currentUserId) " (you)" else "",
                                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OnSurface)
                                        val sc = when (p.acceptanceStatus) {
                                            "accepted" -> Secondary; "declined" -> ErrorColor; else -> OnSurfaceVariant
                                        }
                                        Text(p.acceptanceStatus.replaceFirstChar { it.uppercase() },
                                            fontSize = 12.sp, color = sc)
                                        p.assistedBy?.let {
                                            Text("Assisted by another member", fontSize = 11.sp, color = Amber)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("€%.2f".format(p.share), fontWeight = FontWeight.Black,
                                        fontSize = 16.sp, color = Primary)
                                    if (s.status == "settling" && p.userId != currentUserId && p.assistedBy == null) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(50))
                                                .background(Primary.copy(alpha = 0.08f))
                                                .clickable { showAssistDialog = p.userId }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Assist", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Error ──────────────────────────────────────────────────
                if (uiState is SpaceUiState.Error) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(ErrorColor.copy(alpha = 0.1f)).padding(16.dp)
                    ) {
                        Text((uiState as SpaceUiState.Error).message, color = ErrorColor, fontSize = 13.sp)
                    }
                }

                // ── Audit log ──────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainerLowest)
                            .clickable { showAudit = !showAudit }
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("AUDIT LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
                            Text(
                                if (showAudit) "${audit.size} events  ▲" else "${audit.size} events  ▼",
                                fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (showAudit) {
                        audit.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainerLowest).padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.padding(top = 5.dp).size(8.dp)
                                    .clip(CircleShape).background(Primary.copy(alpha = 0.5f)))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(entry.eventType.replace("_", " ").replaceFirstChar { it.uppercase() },
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                                    Text("${entry.userName ?: "System"} · ${entry.createdAt.take(16).replace("T", " ")}",
                                        fontSize = 11.sp, color = OnSurfaceVariant)
                                }
                            }
                        }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Primary)
                }
                Text(
                    space?.name ?: "Expense",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                // Edit + Delete buttons — visible only to creator
                val isCreatorTop = space?.createdBy == currentUserId
                if (isCreatorTop) {
                    Row {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Primary)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorColor)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        // ── Delete confirmation dialog ─────────────────────────────────────────
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor   = SurfaceContainerLowest,
                shape            = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        "Delete expense?",
                        fontWeight = FontWeight.Bold,
                        color = ErrorColor,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        "\"${space?.name}\" will be permanently deleted and all members will be notified.",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ErrorColor)
                            .clickable(enabled = uiState !is SpaceUiState.Loading) {
                                vm.deleteSpace(groupId, spaceId) { onNavigateBack() }
                                showDeleteDialog = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                }
            )
        }

        // ── Snackbar ──────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // ── Edit bottom sheet ─────────────────────────────────────────────────────
    if (showEditSheet && space != null) {
        val s = space!!
        val canEditAmountMode = s.status == "pending_acceptance"

        var editName     by remember(s.name)     { mutableStateOf(s.name) }
        var editCategory by remember(s.category) { mutableStateOf(s.category) }
        var editAmount   by remember(s.totalAmount) { mutableStateOf(s.totalAmount.toString()) }
        var editMode     by remember(s.splitMode) { mutableStateOf(s.splitMode) }

        // equally constraint for edit
        val editAmtCents = ((editAmount.toDoubleOrNull() ?: 0.0) * 100).toLong()
        val participantCount = s.participants.size.toLong()
        val editEquallyOk = participantCount > 0 && editAmtCents > 0 && editAmtCents % participantCount == 0L

        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState       = editSheetState,
            containerColor   = SurfaceContainerLowest,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(OutlineVariant.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(4.dp))
                Text("Edit expense", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Primary)

                // Name
                EditField(label = "NAME") {
                    BasicTextField(
                        value = editName, onValueChange = { editName = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (editName.isEmpty()) Text("Expense name", fontSize = 15.sp, color = OutlineVariant)
                            inner()
                        }
                    )
                }

                // Category
                EditField(label = "CATEGORY") {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaceViewModel.categories.forEach { cat ->
                            val sel = editCategory == cat
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) Primary else SurfaceContainerLow)
                                    .clickable { editCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(SpaceViewModel.categoryLabel(cat), fontSize = 12.sp,
                                    color = if (sel) Color.White else OnSurfaceVariant,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                // Amount + split mode (only if pending_acceptance)
                if (canEditAmountMode) {
                    EditField(label = "TOTAL AMOUNT (€)") {
                        BasicTextField(
                            value = editAmount,
                            onValueChange = { editAmount = it.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 18.sp, color = OnSurface, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(Primary), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (editAmount.isEmpty()) Text("0.00", fontSize = 18.sp, color = OutlineVariant, fontWeight = FontWeight.Bold)
                                inner()
                            }
                        )
                    }
                    EditField(label = "SPLIT MODE") {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceViewModel.splitModes.forEach { mode ->
                                val sel      = editMode == mode
                                val disabled = mode == "equally" && !editEquallyOk
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(50))
                                        .background(if (sel) Primary else if (disabled) SurfaceContainerLow.copy(0.5f) else SurfaceContainerLow)
                                        .clickable(enabled = !disabled) { editMode = mode }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(SpaceViewModel.splitModeLabel[mode] ?: mode, fontSize = 12.sp,
                                        color = if (sel) Color.White else if (disabled) OutlineVariant else OnSurfaceVariant,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        if (!editEquallyOk && editAmtCents > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Amount cannot be split equally among ${s.participants.size} participants",
                                fontSize = 11.sp, color = ErrorColor)
                        }
                    }
                }

                // Save button
                val canSave = editName.isNotBlank() && uiState !is SpaceUiState.Loading
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                        .background(
                            if (canSave) Brush.linearGradient(listOf(Primary, PrimaryContainer))
                            else Brush.linearGradient(listOf(Primary.copy(0.4f), PrimaryContainer.copy(0.4f)))
                        )
                        .clickable(enabled = canSave) {
                            vm.editSpace(
                                groupId, spaceId,
                                EditSpaceRequest(
                                    name        = editName.trim(),
                                    category    = editCategory,
                                    totalAmount = if (canEditAmountMode) editAmount.toDoubleOrNull() else null,
                                    splitMode   = if (canEditAmountMode) editMode else null
                                )
                            ) { showEditSheet = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState is SpaceUiState.Loading)
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("Save changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // ── Assist dialog ─────────────────────────────────────────────────────────
    showAssistDialog?.let { memberId ->
        val memberName = space?.participants?.find { it.userId == memberId }?.name ?: memberId
        AlertDialog(
            onDismissRequest = { showAssistDialog = null },
            containerColor = SurfaceContainerLowest,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Cover $memberName's share?",
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This will be recorded as a debt from $memberName to you, visible in both dashboards.",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                        .clickable { vm.assist(groupId, spaceId, memberId); showAssistDialog = null }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssistDialog = null }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }
}

// ── Status Banner ─────────────────────────────────────────────────────────────
@Composable
private fun StatusBanner(space: SpaceResponse) {
    val (bg, fg, text) = when (space.status) {
        "pending_acceptance" -> Triple(Color(0xFFFFF3E0), Amber,       "⏳ ${SpaceViewModel.statusLabel(space.status)}")
        "active"             -> Triple(Color(0xFFE8F5E9), Secondary,   "✅ ${SpaceViewModel.statusLabel(space.status)}")
        "settling"           -> Triple(Color(0xFFEEF2FF), Primary,     "🔄 ${SpaceViewModel.statusLabel(space.status)}")
        "settled"            -> Triple(Color(0xFFE8F5E9), Secondary,   "🎉 ${SpaceViewModel.statusLabel(space.status)}")
        "cancelled"          -> Triple(Color(0xFFFFEBEE), ErrorColor,  "❌ ${SpaceViewModel.statusLabel(space.status)}")
        "suspended"          -> Triple(Color(0xFFFCE4EC), ErrorColor,  "🚫 ${SpaceViewModel.statusLabel(space.status)}")
        else                 -> Triple(SurfaceContainerLow, OnSurfaceVariant, space.status)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
    }
}

// ── Action Section ────────────────────────────────────────────────────────────
@Composable
private fun ActionSection(
    space: SpaceResponse,
    currentUserId: String,
    isCreator: Boolean,
    isLauncher: Boolean,
    isLoading: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onForceLaunch: () -> Unit,
    onSettle: () -> Unit,
    onEarlySettle: () -> Unit,
    onVoteYes: () -> Unit,
    onVoteNo: () -> Unit,
    onConfirmQuorum: () -> Unit,
    onRejectQuorum: () -> Unit
) {
    val myStatus = space.myAcceptanceStatus
    val myQuorum = space.myQuorumStatus

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Accept / Decline invitation ───────────────────────────────────
        if (space.status == "pending_acceptance" && myStatus == "pending") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (!isLoading) Secondary else Secondary.copy(alpha = 0.4f))
                        .clickable(enabled = !isLoading) { onAccept() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Accept", color = Color.White, fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(ErrorColor.copy(alpha = 0.1f))
                        .clickable(enabled = !isLoading) { onDecline() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Decline", color = ErrorColor, fontWeight = FontWeight.Bold) }
            }
        }

        // ── Force launch (creator with mixed responses) ───────────────────
        if (space.status == "pending_acceptance" && isCreator) {
            val hasAccepted = space.participants.any { it.acceptanceStatus == "accepted" }
            val hasDeclined = space.participants.any { it.acceptanceStatus == "declined" }
            if (hasDeclined && hasAccepted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(OnSurfaceVariant.copy(alpha = 0.08f))
                        .clickable(enabled = !isLoading) { onForceLaunch() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Launch with accepting members only",
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ── Trigger settlement ────────────────────────────────────────────
        if (space.status == "active" && (isCreator || isLauncher)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (!isLoading) Brush.linearGradient(listOf(Primary, PrimaryContainer))
                        else Brush.linearGradient(listOf(Primary.copy(0.4f), PrimaryContainer.copy(0.4f)))
                    )
                    .clickable(enabled = !isLoading) { onSettle() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Trigger settlement", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        // ── Request early settlement ──────────────────────────────────────
        if (space.status == "active" && space.settlementMode == "PLAN"
            && (isCreator || isLauncher) && !space.earlySettleRequested
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Primary.copy(alpha = 0.08f))
                    .clickable { onEarlySettle() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Request early settlement", color = Primary, fontWeight = FontWeight.Bold) }
        }

        // ── Vote on early settlement ──────────────────────────────────────
        if (space.earlySettleRequested && myStatus == "accepted") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerLowest)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "The launcher requests early settlement. Do you agree?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        lineHeight = 20.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(Secondary).clickable(enabled = !isLoading) { onVoteYes() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Yes", color = Color.White, fontWeight = FontWeight.Bold) }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(ErrorColor.copy(alpha = 0.1f)).clickable(enabled = !isLoading) { onVoteNo() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("No", color = ErrorColor, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // ── Quorum confirmation ───────────────────────────────────────────
        if (space.status == "settling" && myQuorum == "pending") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Primary.copy(alpha = 0.06f))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "⚠️ Your confirmation is required",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Primary
                    )
                    Text(
                        "You are part of the confirmation quorum. You have 5 minutes to respond.",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                                .clickable(enabled = !isLoading) { onConfirmQuorum() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold) }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(ErrorColor.copy(alpha = 0.1f))
                                .clickable(enabled = !isLoading) { onRejectQuorum() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Reject", color = ErrorColor, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Primary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditField(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.2.sp)
        content()
    }
}
