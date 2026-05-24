package com.splitpay.data.network

// ── Auth ──────────────────────────────────────────────────────────────────
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val name: String, val email: String, val password: String, val phone: String? = null)
data class RefreshRequest(val refreshToken: String)
data class GoogleAuthRequest(val idToken: String)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String
)

data class MessageResponse(val message: String)

data class UserProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val avatarUrl: String?,
    val preferredCurrency: String,
    val isVerified: Boolean,
    val isAdmin: Boolean = false,
    val kycStatus: String? = null,
    val accountBalance: Double = 0.0,
    val requireConsent: Boolean = false
)

data class PendingInvitationResponse(
    val id: String,
    val type: String,          // "group" | "expense"
    val title: String,
    val subtitle: String,
    val emoji: String,
    val amount: Double?,
    val invitedByName: String,
    val createdAt: String
)

data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val preferredCurrency: String? = null
)

data class RequireConsentRequest(val value: Boolean)

// ── Groups ────────────────────────────────────────────────────────────────
data class GroupResponse(
    val id: String,
    val name: String,
    val emoji: String = "💰",
    val description: String?,
    val createdBy: String,
    val isArchived: Boolean,
    val inviteToken: String?,
    val memberCount: Int,
    val lastActivityAt: String = "",
    val userBalance: Double = 0.0
)

data class GroupMemberResponse(
    val userId: String,
    val name: String,
    val role: String,
    val joinedAt: String
)

data class CreateGroupRequest(val name: String, val emoji: String = "💰", val description: String? = null)

data class UpdateGroupRequest(val name: String, val emoji: String? = null, val description: String? = null)
data class AddMemberRequest(val userId: String)
data class LookupRequest(val phones: List<String>)
data class LookupUserResponse(val userId: String, val name: String, val phone: String, val email: String?)

// ── FCM ───────────────────────────────────────────────────────────────
data class FcmTokenRequest(val token: String)

// ── Admin ─────────────────────────────────────────────────────────────
data class AdminStatsResponse(
    val totalUsers: Int,
    val totalGroups: Int,
    val totalExpenses: Int,
    val totalAmount: Double
)

data class AdminUserResponse(
    val id: String,
    val name: String,
    val email: String,
    val isVerified: Boolean,
    val isAdmin: Boolean,
    val amlStatus: String? = null,
    val kycStatus: String? = null,
    val accountBalance: Double = 0.0,
    val lastActivityAt: String? = null,
    val createdAt: String
)

data class AdminBalanceUpdateRequest(
    val mode: String,       // "set" | "add" | "subtract"
    val amount: Double,
    val note: String? = null
)

// ── KYC ───────────────────────────────────────────────────────────────────
data class KycDocumentResponse(
    val id: String,
    val docType: String,
    val status: String,
    val rejectionReason: String?,
    val createdAt: String
)

data class KycUploadRequest(
    val docType:  String,
    val fileData: String,   // base64
    val fileName: String
)

data class KycStatusResponse(
    val kycStatus: String,
    val documents: List<KycDocumentResponse>
)

data class AdminKycDocResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val docType: String,
    val status: String,
    val fileUrl: String,
    val createdAt: String
)

data class KycReviewRequest(
    val status: String,
    val rejectionReason: String? = null
)

// ── AML ───────────────────────────────────────────────────────────────────
data class AmlAlertResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val alertType: String,
    val description: String,
    val amount: Double?,
    val expenseId: String?,
    val status: String,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val createdAt: String
)

data class ReviewAlertRequest(val status: String)

// ── GDPR Config ───────────────────────────────────────────────────────────
data class GdprConfigResponse(val config: Map<String, String>)

data class GdprConfigUpdateRequest(
    val archiveAfterMonths: Int? = null,
    val deleteAfterMonths: Int? = null,
    val largeTransactionThreshold: Double? = null,
    val highFrequencyCount: Int? = null,
    val highFrequencyWindowHours: Int? = null,
    val newAccountDays: Int? = null,
    val autoSuspendAfterAlerts: Int? = null
)

data class AdminGroupResponse(
    val id: String,
    val name: String,
    val emoji: String,
    val memberCount: Int,
    val expenseCount: Int,
    val totalAmount: Double,
    val createdAt: String
)

// ── Espaces (Logique Métier v1.3) ─────────────────────────────────────────
data class SpaceParticipantResponse(
    val userId:           String,
    val name:             String,
    val share:            Double,
    val acceptanceStatus: String,       // pending | accepted | declined
    val assistedBy:       String? = null
)

data class SpaceResponse(
    val id:                   String,
    val groupId:              String,
    val name:                 String,
    val category:             String,
    val totalAmount:          Double,
    val splitMode:            String,
    val settlementMode:       String,   // PAY | PLAN
    val dueDate:              String? = null,
    val launcherId:           String,
    val status:               String,   // pending_acceptance | active | settling | settled | cancelled | suspended
    val forceLaunched:        Boolean  = false,
    val earlySettleRequested: Boolean  = false,
    val createdBy:            String,
    val createdAt:            String,
    val myShare:              Double?  = null,
    val myAcceptanceStatus:   String?  = null,
    val myAssistedBy:         String?  = null,
    val myQuorumStatus:       String?  = null,
    val participants:         List<SpaceParticipantResponse> = emptyList()
)

data class SpaceParticipantInput(val userId: String, val share: Double? = null)

data class CreateSpaceRequest(
    val name:           String,
    val category:       String  = "autre",
    val totalAmount:    Double,
    val splitMode:      String  = "equally",
    val settlementMode: String  = "PAY",
    val dueDate:        String? = null,
    val launcherId:     String? = null,
    val participants:   List<SpaceParticipantInput>
)

data class PaymentHistoryItem(
    val id:         String,
    val amount:     Double,
    val method:     String?,
    val note:       String?,
    val paidAt:     String,
    val spaceName:  String?,
    val spaceId:    String?,
    val fromUserId: String,
    val fromName:   String?,
    val toUserId:   String,
    val toName:     String?,
    val direction:  String    // "sent" | "received"
)

data class EditSpaceRequest(
    val name:        String?  = null,
    val category:    String?  = null,
    val totalAmount: Double?  = null,
    val splitMode:   String?  = null,
    val dueDate:     String?  = null
)

data class DirectPayRequest(val toUserId: String, val amount: Double, val note: String? = null)
data class DirectPayResponse(val message: String, val newBalance: Double)
data class GroupDebtorResponse(val userId: String, val name: String, val amount: Double)

data class EarlySettleVoteRequest(val vote: String)     // accepted | declined
data class AssistRequest(val memberId: String)
data class TransferLauncherRequest(val toUserId: String)

data class SpaceAuditEntry(
    val id:        String,
    @com.google.gson.annotations.SerializedName("user_id")    val userId:    String?,
    @com.google.gson.annotations.SerializedName("user_name")  val userName:  String?,
    @com.google.gson.annotations.SerializedName("event_type") val eventType: String,
    @com.google.gson.annotations.SerializedName("created_at") val createdAt: String
)

data class PendingSpaceInvitation(
    val id:               String,
    val groupId:          String,
    val groupName:        String,
    val groupEmoji:       String,
    val name:             String,
    val category:         String,
    val totalAmount:      Double,
    val myShare:          Double,
    val settlementMode:   String,
    val status:           String,
    val acceptanceStatus: String,
    val quorumStatus:     String? = null,
    val createdByName:    String,
    val createdAt:        String
)
