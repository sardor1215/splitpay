package com.splitpay.ui.kyc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.data.network.KycDocumentResponse
import com.splitpay.ui.theme.LocalAppColors
import com.splitpay.viewmodel.KycViewModel

private val Warning = Color(0xFFB45309)

private val DOC_TYPES = listOf(
    "id_front"  to "ID Card — Front",
    "id_back"   to "ID Card — Back",
    "passport"  to "Passport",
    "selfie"    to "Selfie with ID"
)

@Composable
fun KycScreen(
    onNavigateBack: () -> Unit,
    viewModel: KycViewModel = viewModel()
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

    val kycStatus      by viewModel.kycStatus.collectAsStateWithLifecycle()
    val isLoading      by viewModel.isLoading.collectAsStateWithLifecycle()
    val uploadingDoc   by viewModel.uploadingDoc.collectAsStateWithLifecycle()
    val error          by viewModel.error.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) { snackbarHostState.showSnackbar(error!!); viewModel.clearMessages() }
    }
    LaunchedEffect(successMessage) {
        if (successMessage != null) { snackbarHostState.showSnackbar(successMessage!!); viewModel.clearMessages() }
    }

    // Pending upload: which docType are we picking for
    var pendingDocType by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadDocument(context.contentResolver, it, pendingDocType!!) }
        pendingDocType = null
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = Surface) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 80.dp, start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else {
                    val status = kycStatus?.kycStatus ?: "none"
                    val docs   = kycStatus?.documents ?: emptyList()

                    // Status banner
                    KycStatusBanner(status)

                    Spacer(Modifier.height(24.dp))

                    // Explanation + progress
                    if (status == "none" || status == "rejected") {
                        Text(
                            "To use all features you need to verify your identity. Upload all 4 documents below, then tap Submit.",
                            fontSize = 13.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        // Progress bar
                        val uploadedCount = docs.size
                        val total = DOC_TYPES.size
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Documents uploaded", fontSize = 12.sp, color = OnSurfaceVariant)
                                Text("$uploadedCount / $total", fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uploadedCount == total) Secondary else Primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uploadedCount.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(
                                    androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                ),
                                color = if (uploadedCount == total) Secondary else Primary,
                                trackColor = Primary.copy(alpha = 0.12f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Document cards
                    DOC_TYPES.forEach { (type, label) ->
                        val existing = docs.find { it.docType == type }
                        DocumentCard(
                            label       = label,
                            docType     = type,
                            existing    = existing,
                            isUploading = uploadingDoc == type,
                            canUpload   = status != "approved" && status != "pending"
                        ) {
                            pendingDocType = type
                            picker.launch("image/*")
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Submit button — only when all docs uploaded and not yet submitted
                    if (status == "none" || status == "rejected") {
                        val allUploaded = viewModel.hasAllDocuments()
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.submitForReview() },
                            enabled = allUploaded && uploadingDoc == null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(
                                if (allUploaded) "Submit for Review" else "Upload all documents to continue",
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (status == "approved") {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Your identity has been verified. All features are available.",
                            fontSize = 13.sp, color = Secondary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium
                        )
                    }

                    if (status == "pending") {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "All documents have been submitted and are under review. You'll be notified of the result.",
                            fontSize = 13.sp, color = Warning, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(70.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Text("Identity Verification", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Spacer(Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun KycStatusBanner(status: String) {
    val c = LocalAppColors.current
    val Primary   = c.primary
    val Secondary = c.secondary
    val Tertiary  = c.tertiary

    val (icon, color, title, sub) = when (status) {
        "approved" -> KycBannerData(Icons.Default.CheckCircle,  Secondary, "Verified",        "Your identity is confirmed")
        "pending"  -> KycBannerData(Icons.Default.HourglassTop, Warning,   "Under Review",    "We'll notify you when done")
        "rejected" -> KycBannerData(Icons.Default.Warning,      Tertiary,  "Action Required", "Some documents were rejected")
        else       -> KycBannerData(Icons.Default.CloudUpload,  Primary,   "Not Verified",    "Upload documents to get started")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(sub, fontSize = 12.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

private data class KycBannerData(val icon: ImageVector, val color: Color, val title: String, val sub: String)

@Composable
private fun DocumentCard(
    label: String,
    docType: String,
    existing: KycDocumentResponse?,
    isUploading: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit
) {
    val c = LocalAppColors.current
    val Primary          = c.primary
    val Secondary        = c.secondary
    val Tertiary         = c.tertiary
    val SurfaceLowest    = c.surfaceLowest
    val OnSurface        = c.onSurface
    val OnSurfaceVariant = c.onSurfaceVariant

    val statusColor = when (existing?.status) {
        "approved" -> Secondary
        "rejected" -> Tertiary
        "pending"  -> Warning
        else       -> OnSurfaceVariant
    }
    val statusLabel = existing?.status?.replaceFirstChar { it.uppercase() } ?: "Not uploaded"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLowest)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            val icon = when (existing?.status) {
                "approved" -> Icons.Default.CheckCircle
                "rejected" -> Icons.Default.Warning
                "pending"  -> Icons.Default.HourglassTop
                else       -> Icons.Default.CloudUpload
            }
            Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
            if (existing?.rejectionReason != null) {
                Text(existing.rejectionReason, fontSize = 10.sp, color = Tertiary)
            }
        }

        if (canUpload) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
            } else {
                val btnLabel = if (existing != null) "Replace" else "Upload"
                OutlinedButton(
                    onClick = onUpload,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                ) { Text(btnLabel, fontSize = 12.sp) }
            }
        }
    }
}
