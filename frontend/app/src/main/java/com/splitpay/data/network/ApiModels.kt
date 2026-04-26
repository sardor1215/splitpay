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
    val isVerified: Boolean
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

// ── Balances & Settlements ────────────────────────────────────────────────
data class BalanceResponse(val userId: String, val name: String, val amount: Double)

data class SettlementResponse(
    val fromUserId: String,
    val fromName: String,
    val toUserId: String,
    val toName: String,
    val amount: Double
)
