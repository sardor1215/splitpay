package com.splitpay.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.viewmodel.Contact
import com.splitpay.viewmodel.CreateGroupViewModel

// ── Brand colors ──────────────────────────────────────────────────────────────
private val Primary          = Color(0xFF2B348D)
private val PrimaryContainer = Color(0xFF444DA6)
private val PrimaryFixed     = Color(0xFFE0E0FF)
private val Secondary        = Color(0xFF1B6D24)
private val Surface          = Color(0xFFF9F9FC)
private val SurfaceLowest    = Color(0xFFFFFFFF)
private val SurfaceLow       = Color(0xFFF3F3F6)
private val OnSurface        = Color(0xFF1A1C1E)
private val OnSurfaceVariant = Color(0xFF3F4949)
private val OutlineVariant   = Color(0xFFBEC8C9)

@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val groupName     by viewModel.groupName.collectAsStateWithLifecycle()
    val selectedEmoji by viewModel.selectedEmoji.collectAsStateWithLifecycle()
    val contacts      by viewModel.contacts.collectAsStateWithLifecycle()
    val addedCount    = contacts.count { it.isAdded }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Scrollable content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 140.dp, bottom = 120.dp)
        ) {

            // ── Group icon + name ─────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selected emoji preview
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(colors = listOf(Primary.copy(alpha = 0.08f), PrimaryFixed.copy(alpha = 0.4f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedEmoji, fontSize = 44.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Group name field
                Text(
                    text = "GROUP NAME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = groupName,
                    onValueChange = { viewModel.onGroupNameChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (groupName.isEmpty()) {
                            Text(
                                text = "e.g. Trip to Paris",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = OutlineVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Emoji picker ──────────────────────────────────────────────
            Text(
                text = "CHOOSE AN ICON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Fixed height grid — 3 rows of 6
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(viewModel.emojis) { emoji ->
                    val isSelected = emoji == selectedEmoji
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Primary.copy(alpha = 0.12f)
                                else SurfaceLowest
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Primary else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Members ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ADD MEMBERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
                if (addedCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$addedCount added",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.forEach { contact ->
                    ContactRow(
                        contact = contact,
                        onToggle = { viewModel.onToggleContact(contact.id) }
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "New Group",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                TextButton(onClick = {
                    viewModel.createGroup { onNavigateBack() }
                }) {
                    Text(
                        text = "Create",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                }
            }
        }

        // ── Bottom CTA ────────────────────────────────────────────────────
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
                        Brush.linearGradient(colors = listOf(Primary, PrimaryContainer))
                    )
                    .clickable { viewModel.createGroup { onNavigateBack() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (addedCount > 0) "Create Group · $addedCount member${if (addedCount > 1) "s" else ""}"
                           else "Create Group",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ── Contact Row ───────────────────────────────────────────────────────────────
@Composable
private fun ContactRow(contact: Contact, onToggle: () -> Unit) {
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
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.first().toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Text(
                text = contact.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
        }

        // Checkbox
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (contact.isAdded) Primary else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (contact.isAdded) Primary else OutlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (contact.isAdded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
