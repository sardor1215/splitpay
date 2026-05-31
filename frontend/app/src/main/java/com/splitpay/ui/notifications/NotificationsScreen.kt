package com.splitpay.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.PendingInvitationResponse
import com.splitpay.data.network.PendingSpaceInvitation
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.NotificationsViewModel
import com.splitpay.viewmodel.SpaceViewModel

private val Amber = Color(0xFFE65100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpace: ((groupId: String, spaceId: String) -> Unit)? = null,
    viewModel: NotificationsViewModel = viewModel()
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val PrimaryContainer = c.primaryContainer
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val Surface          = c.surface
    val SurfaceLowest    = c.surfaceLowest
    val SurfaceLow       = c.surfaceLow
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant

    val invitations      by viewModel.invitations.collectAsStateWithLifecycle()
    val spaceInvitations by viewModel.spaceInvitations.collectAsStateWithLifecycle()
    val isLoading        by viewModel.isLoading.collectAsStateWithLifecycle()

    val hasAny = invitations.isNotEmpty() || spaceInvitations.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (!hasAny) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, contentDescription = null,
                        tint = Primary.copy(alpha = 0.2f), modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("You're all caught up!", fontSize = 14.sp, color = OnSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 86.dp, bottom = 32.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Invitations groupe / dépense ──────────────────────────
                if (invitations.isNotEmpty()) {
                    item {
                        SectionHeader("INVITATIONS")
                    }
                    items(invitations, key = { it.id }) { inv ->
                        InvitationCard(
                            invitation = inv,
                            onAccept   = { viewModel.accept(inv) },
                            onDecline  = { viewModel.decline(inv) }
                        )
                    }
                }

                // ── Invitations espace ────────────────────────────────────
                if (spaceInvitations.isNotEmpty()) {
                    item {
                        if (invitations.isNotEmpty()) Spacer(Modifier.height(4.dp))
                        SectionHeader("SPACES")
                    }
                    items(spaceInvitations, key = { it.id }) { inv ->
                        val isQuorum      = inv.quorumStatus == "pending"
                        val isEarlySettle = inv.status == "active" && inv.acceptanceStatus == "accepted"
                        val onAccept: () -> Unit = when {
                            isQuorum      -> { { viewModel.confirmQuorum(inv) } }
                            isEarlySettle -> { { viewModel.acceptEarlySettle(inv) } }
                            else          -> { { viewModel.acceptSpace(inv) } }
                        }
                        val onDecline: () -> Unit = when {
                            isQuorum      -> { { viewModel.rejectQuorum(inv) } }
                            isEarlySettle -> { { viewModel.declineEarlySettle(inv) } }
                            else          -> { { viewModel.declineSpace(inv) } }
                        }
                        SpaceInvitationCard(
                            invitation  = inv,
                            onAccept    = onAccept,
                            onDecline   = onDecline,
                            onOpenSpace = onNavigateToSpace?.let { nav -> { nav(inv.groupId, inv.id) } }
                        )
                    }
                }
            }
        }
        } // end PullToRefreshBox

        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLowest)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(70.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Text("Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                Spacer(Modifier.size(40.dp))
            }
        }
    }
}

// ── Card invitation groupe / dépense (existante) ──────────────────────────────
@Composable
private fun InvitationCard(
    invitation: PendingInvitationResponse,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    val accentColor = if (invitation.type == "expense") Color(0xFF6B4EFF) else Primary
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(SurfaceLowest).padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { Text(invitation.emoji, fontSize = 26.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(invitation.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.weight(1f))
                        if (invitation.amount != null)
                            Text("€%.2f".format(invitation.amount), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
                    }
                    Text(invitation.subtitle, fontSize = 13.sp, color = OnSurfaceVariant)
                    Text(invitation.createdAt.take(10), fontSize = 11.sp, color = OnSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            ActionRow(onAccept = onAccept, onDecline = onDecline)
        }
    }
}

// ── Card invitation espace ────────────────────────────────────────────────────
@Composable
private fun SpaceInvitationCard(
    invitation: PendingSpaceInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenSpace: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant
    val OutlineVariant   = c.outlineVariant

    val isQuorum        = invitation.quorumStatus == "pending"
    val isEarlySettle   = invitation.status == "active" && invitation.acceptanceStatus == "accepted"
    val accentColor     = when {
        isQuorum      -> Color(0xFF1565C0)
        isEarlySettle -> Amber
        else          -> Primary
    }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceLowest).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // En-tête
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { Text(SpaceViewModel.categoryLabel(invitation.category).take(2), fontSize = 26.sp) }

                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(invitation.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.weight(1f))
                        Text("€%.2f".format(invitation.myShare), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
                    }
                    Text(
                        when {
                            isQuorum      -> "⚠️ Your quorum confirmation is required (5 min)"
                            isEarlySettle -> "The launcher requests early settlement"
                            else          -> "Added by ${invitation.createdByName} · ${invitation.groupEmoji} ${invitation.groupName}"
                        },
                        fontSize = 12.sp, color = OnSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypeChip(
                            label = if (isQuorum) "Quorum" else if (isEarlySettle) "Early settle" else "Space",
                            color = accentColor
                        )
                        TypeChip(label = SpaceViewModel.statusLabel(invitation.status), color = Color.Gray)
                    }
                }
            }

            // Actions contextuelles
            when {
                isQuorum -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionBtn("Confirm", Color(0xFF1565C0), onAccept)
                        ActionBtn("Reject", Tertiary, onDecline, outlined = true)
                    }
                }
                isEarlySettle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionBtn("Accept early settlement", Amber, onAccept)
                        ActionBtn("Keep date", Tertiary, onDecline, outlined = true)
                    }
                }
                else -> {
                    // Invitation classique accept/decline
                    ActionRow(onAccept = onAccept, onDecline = onDecline)
                }
            }

            // Lien vers l'espace
            if (onOpenSpace != null) {
                TextButton(onClick = onOpenSpace, contentPadding = PaddingValues(0.dp)) {
                    Text("View space details →", fontSize = 12.sp, color = Primary)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onAccept: () -> Unit, onDecline: () -> Unit) {
    val c = LocalAppColors.current
    val Secondary = c.secondary
    val Tertiary  = c.tertiary

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionBtn("Accept", Secondary, onAccept)
        ActionBtn("Decline", Tertiary, onDecline, outlined = true)
    }
}

@Composable
private fun RowScope.ActionBtn(label: String, color: Color, onClick: () -> Unit, outlined: Boolean = false) {
    Box(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (outlined) 0.08f else 0.1f))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color) }
}

@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    val OnSurfaceVariant = c.onSurfaceVariant

    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = OnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TypeChip(label: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}
