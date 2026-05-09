package com.splitpay.data.network

// ── Auth ──────────────────────────────────────────────────────────────────
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val name: String, val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)

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
    val accountBalance: Double = 0.0
)

data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val preferredCurrency: String? = null
)

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

// ── Expenses ──────────────────────────────────────────────────────────────
data class CreateExpenseRequest(
    val title: String,
    val amount: Double,
    val paidBy: String,
    val splitMode: String = "equally",
    val category: String = "other",
    val participants: List<ParticipantRequest>
)

data class UpdateExpenseRequest(
    val title: String,
    val amount: Double,
    val paidBy: String,
    val splitMode: String = "equally",
    val category: String = "other",
    val participants: List<ParticipantRequest>
)

data class ParticipantRequest(val userId: String, val share: Double? = null)

data class ExpenseResponse(
    val id: String,
    val groupId: String,
    val title: String,
    val amount: Double,
    val paidBy: String,
    val paidByName: String,
    val splitMode: String? = null,
    val category: String? = null,
    val participants: List<ParticipantResponse>,
    val createdAt: String,
    val updatedAt: String? = null
)

data class ParticipantResponse(val userId: String, val name: String, val share: Double)

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
    val lastActivityAt: String? = null,
    val createdAt: String
)

// ── KYC ───────────────────────────────────────────────────────────────────
data class KycDocumentResponse(
    val id: String,
    val docType: String,
    val status: String,
    val rejectionReason: String?,
    val createdAt: String
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

// ── Expense detail ────────────────────────────────────────────────────────
data class ExpenseActivityResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val action: String,
    val details: String?,
    val createdAt: String
)

data class ExpenseDetailResponse(
    val expense: ExpenseResponse,
    val activities: List<ExpenseActivityResponse>
)

// ── Balances & Settlements ────────────────────────────────────────────────
data class BalanceResponse(val userId: String, val name: String, val amount: Double)

data class SettlementResponse(
    val fromUserId: String,
    val fromName: String,
    val toUserId: String,
    val toName: String,
    val amount: Double
)
