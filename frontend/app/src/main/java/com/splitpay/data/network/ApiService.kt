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

    @GET("groups/{groupId}/expenses/{expenseId}")
    suspend fun getExpenseDetail(
        @Path("groupId") groupId: String,
        @Path("expenseId") expenseId: String
    ): Response<ExpenseDetailResponse>

    @POST("groups/{groupId}/expenses")
    suspend fun createExpense(@Path("groupId") groupId: String, @Body body: CreateExpenseRequest): Response<ExpenseResponse>

    @PATCH("groups/{groupId}/expenses/{expenseId}")
    suspend fun updateExpense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String, @Body body: UpdateExpenseRequest): Response<ExpenseResponse>

    @DELETE("groups/{groupId}/expenses/{expenseId}")
    suspend fun deleteExpense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String): Response<MessageResponse>

    // ── FCM ───────────────────────────────────────────────────────────────
    @POST("users/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest): Response<MessageResponse>

    // ── Admin ─────────────────────────────────────────────────────────────
    @GET("admin/stats")
    suspend fun getAdminStats(): Response<AdminStatsResponse>

    @GET("admin/users")
    suspend fun getAdminUsers(): Response<List<AdminUserResponse>>

    @GET("admin/groups")
    suspend fun getAdminGroups(): Response<List<AdminGroupResponse>>

    // ── AML ───────────────────────────────────────────────────────────────
    @GET("admin/aml/alerts")
    suspend fun getAmlAlerts(@Query("status") status: String? = null): Response<List<AmlAlertResponse>>

    @PATCH("admin/aml/alerts/{alertId}")
    suspend fun reviewAmlAlert(@Path("alertId") alertId: String, @Body body: ReviewAlertRequest): Response<MessageResponse>

    @PATCH("admin/users/{userId}/aml-status")
    suspend fun updateUserAmlStatus(@Path("userId") userId: String, @Body body: ReviewAlertRequest): Response<MessageResponse>

    // ── GDPR Config ───────────────────────────────────────────────────────
    @GET("admin/gdpr/config")
    suspend fun getGdprConfig(): Response<GdprConfigResponse>

    @PUT("admin/gdpr/config")
    suspend fun updateGdprConfig(@Body body: GdprConfigUpdateRequest): Response<MessageResponse>

    @POST("admin/gdpr/run-cleanup")
    suspend fun runGdprCleanup(): Response<MessageResponse>

    @POST("admin/users/{userId}/lift-suspension")
    suspend fun liftSuspension(@Path("userId") userId: String): Response<MessageResponse>

    // ── KYC (user) ────────────────────────────────────────────────────────
    @Multipart
    @POST("kyc/documents")
    suspend fun uploadKycDocument(
        @Part("docType") docType: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<KycDocumentResponse>

    @GET("kyc/status")
    suspend fun getKycStatus(): Response<KycStatusResponse>

    @POST("kyc/submit")
    suspend fun submitKycForReview(): Response<MessageResponse>

    // ── KYC (admin) ───────────────────────────────────────────────────────
    @GET("admin/kyc/pending")
    suspend fun getKycPending(): Response<List<AdminKycDocResponse>>

    @PATCH("admin/kyc/documents/{docId}")
    suspend fun reviewKycDocument(
        @Path("docId") docId: String,
        @Body body: KycReviewRequest
    ): Response<MessageResponse>

    @POST("admin/kyc/users/{userId}/approve-all")
    suspend fun approveAllKycForUser(@Path("userId") userId: String): Response<MessageResponse>

    // ── Balances & Settlements ────────────────────────────────────────────
    @GET("groups/{groupId}/balances")
    suspend fun getBalances(@Path("groupId") groupId: String): Response<List<BalanceResponse>>

    @GET("groups/{groupId}/settlements")
    suspend fun getSettlements(@Path("groupId") groupId: String): Response<List<SettlementResponse>>
}
