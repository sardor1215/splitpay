package com.splitpay.network

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    // Synchronous version used by AuthInterceptor (OkHttp thread — no coroutines)
    @POST("auth/refresh")
    fun refreshSync(@Body request: RefreshRequest): Call<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    // ── User ──────────────────────────────────────────────────────────────
    @GET("me")
    suspend fun getProfile(): Response<UserResponse>

    @PATCH("me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserResponse>

    @DELETE("me")
    suspend fun deleteAccount(): Response<MessageResponse>

    // ── Groups ────────────────────────────────────────────────────────────
    @POST("groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupResponse>

    @GET("groups")
    suspend fun getGroups(): Response<List<GroupResponse>>

    @GET("groups/archived")
    suspend fun getArchivedGroups(): Response<List<GroupResponse>>

    @GET("groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Response<GroupResponse>

    @PATCH("groups/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body request: UpdateGroupRequest): Response<GroupResponse>

    @DELETE("groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<MessageResponse>

    @GET("groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") id: String): Response<List<GroupMemberResponse>>

    @POST("groups/{id}/members")
    suspend fun addMemberToGroup(@Path("id") id: String, @Body request: AddMemberRequest): Response<MessageResponse>

    @DELETE("groups/{id}/members/{memberId}")
    suspend fun removeMember(@Path("id") id: String, @Path("memberId") memberId: String): Response<MessageResponse>

    @POST("groups/join/{token}")
    suspend fun joinGroup(@Path("token") token: String): Response<MessageResponse>

    @POST("groups/{id}/leave")
    suspend fun leaveGroup(@Path("id") id: String): Response<MessageResponse>

    @PATCH("groups/{id}/archive")
    suspend fun archiveGroup(@Path("id") id: String): Response<MessageResponse>

    @PATCH("groups/{id}/unarchive")
    suspend fun unarchiveGroup(@Path("id") id: String): Response<MessageResponse>

    // ── User lookup ───────────────────────────────────────────────────────
    @POST("users/lookup")
    suspend fun lookupUsers(@Body request: PhoneLookupRequest): Response<List<UserLookupResult>>

    // ── Expenses ──────────────────────────────────────────────────────────
    @POST("groups/{groupId}/expenses")
    suspend fun createExpense(@Path("groupId") groupId: String, @Body request: CreateExpenseRequest): Response<ExpenseResponse>

    @GET("groups/{groupId}/expenses")
    suspend fun getExpenses(@Path("groupId") groupId: String): Response<List<ExpenseResponse>>
}
