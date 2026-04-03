package com.splitpay.network

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val identifier: String, // email or phone number
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

data class AuthResponse(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("userId")       val userId: String,
    @SerializedName("name")         val name: String,
    @SerializedName("email")        val email: String
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class RefreshResponse(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)

// ── User ──────────────────────────────────────────────────────────────────────

data class UserResponse(
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
    val preferredCurrency: String? = null
)

// ── Groups ────────────────────────────────────────────────────────────────────

data class CreateGroupRequest(
    val name: String,
    val description: String? = null
)

data class GroupResponse(
    val id: String,
    val name: String,
    val description: String?,
    val createdBy: String,
    val isArchived: Boolean,
    val inviteToken: String?,
    val memberCount: Int = 0
)

data class GroupMemberResponse(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val joinedAt: String
)

data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null
)

// ── Members ───────────────────────────────────────────────────────────────────

data class AddMemberRequest(
    @SerializedName("userId") val userId: String
)

data class PhoneLookupRequest(
    @SerializedName("phones") val phones: List<String>
)

data class UserLookupResult(
    @SerializedName("phone")  val phone: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("name")   val name: String
)

// ── Generic ───────────────────────────────────────────────────────────────────

data class MessageResponse(
    val message: String
)

data class ApiError(
    val error: String
)

// ── Expenses ──────────────────────────────────────────────────────────────────

data class CreateExpenseRequest(
    @SerializedName("title")          val title: String,
    @SerializedName("amount")         val amount: Double,
    @SerializedName("paidByUserId")   val paidByUserId: String,
    @SerializedName("splitMode")      val splitMode: String = "equally",
    @SerializedName("participants")   val participants: List<ExpenseParticipantRequest>
)

data class ExpenseParticipantRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("share")  val share: Double
)

data class ExpenseResponse(
    @SerializedName("id")           val id: String,
    @SerializedName("title")        val title: String,
    @SerializedName("amount")       val amount: Double,
    @SerializedName("paidByUserId") val paidByUserId: String,
    @SerializedName("paidByName")   val paidByName: String,
    @SerializedName("splitMode")    val splitMode: String,
    @SerializedName("createdAt")    val createdAt: String,
    @SerializedName("participants") val participants: List<ExpenseParticipantResponse>
)

data class ExpenseParticipantResponse(
    @SerializedName("userId") val userId: String,
    @SerializedName("name")   val name: String,
    @SerializedName("share")  val share: Double
)
