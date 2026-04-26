package com.splitpay.data.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<MessageResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<MessageResponse>

    // ── Profile ───────────────────────────────────────────────────────────
    @GET("me")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<UserProfileResponse>

    @DELETE("me")
    suspend fun deleteAccount(): Response<MessageResponse>

    // ── Groups ────────────────────────────────────────────────────────────
    @GET("groups")
    suspend fun getGroups(): Response<List<GroupResponse>>

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): Response<GroupResponse>

    @GET("groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): Response<GroupResponse>

    @PATCH("groups/{groupId}")
    suspend fun updateGroup(@Path("groupId") groupId: String, @Body body: UpdateGroupRequest): Response<GroupResponse>

    @DELETE("groups/{groupId}")
    suspend fun deleteGroup(@Path("groupId") groupId: String): Response<MessageResponse>

    @GET("groups/{groupId}/members")
    suspend fun getMembers(@Path("groupId") groupId: String): Response<List<GroupMemberResponse>>

    @POST("groups/{groupId}/members")
    suspend fun addMember(@Path("groupId") groupId: String, @Body body: AddMemberRequest): Response<MessageResponse>

    @POST("users/lookup")
    suspend fun lookupByPhones(@Body body: LookupRequest): Response<List<LookupUserResponse>>

    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeMember(@Path("groupId") groupId: String, @Path("userId") userId: String): Response<MessageResponse>

    @PATCH("groups/{groupId}/archive")
    suspend fun archiveGroup(@Path("groupId") groupId: String): Response<MessageResponse>

    @PATCH("groups/{groupId}/unarchive")
    suspend fun unarchiveGroup(@Path("groupId") groupId: String): Response<MessageResponse>

    // ── Expenses ──────────────────────────────────────────────────────────
    @GET("groups/{groupId}/expenses")
    suspend fun getExpenses(@Path("groupId") groupId: String): Response<List<ExpenseResponse>>

    @POST("groups/{groupId}/expenses")
    suspend fun createExpense(@Path("groupId") groupId: String, @Body body: CreateExpenseRequest): Response<ExpenseResponse>

    @PATCH("groups/{groupId}/expenses/{expenseId}")
    suspend fun updateExpense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String, @Body body: UpdateExpenseRequest): Response<ExpenseResponse>

    @DELETE("groups/{groupId}/expenses/{expenseId}")
    suspend fun deleteExpense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String): Response<MessageResponse>

    // ── Balances & Settlements ────────────────────────────────────────────
    @GET("groups/{groupId}/balances")
    suspend fun getBalances(@Path("groupId") groupId: String): Response<List<BalanceResponse>>

    @GET("groups/{groupId}/settlements")
    suspend fun getSettlements(@Path("groupId") groupId: String): Response<List<SettlementResponse>>
}
