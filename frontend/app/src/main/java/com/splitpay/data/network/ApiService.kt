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

    @POST("auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleAuthRequest): Response<AuthResponse>

    // ── Profile ───────────────────────────────────────────────────────────
    @GET("me")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<UserProfileResponse>

    @DELETE("me")
    suspend fun deleteAccount(): Response<MessageResponse>

    @GET("me/payments")
    suspend fun getPaymentHistory(): Response<List<PaymentHistoryItem>>

    @POST("me/pay")
    suspend fun directPay(@Body body: DirectPayRequest): Response<DirectPayResponse>

    @GET("groups/{groupId}/debtors")
    suspend fun getGroupDebtors(@Path("groupId") groupId: String): Response<List<GroupDebtorResponse>>

    @POST("me/require-consent")
    suspend fun setRequireConsent(@Body body: RequireConsentRequest): Response<MessageResponse>

    // ── Invitations ───────────────────────────────────────────────────────
    @GET("invitations/pending")
    suspend fun getPendingInvitations(): Response<List<PendingInvitationResponse>>

    @POST("invitations/{id}/accept")
    suspend fun acceptInvitation(@Path("id") id: String): Response<MessageResponse>

    @POST("invitations/{id}/decline")
    suspend fun declineInvitation(@Path("id") id: String): Response<MessageResponse>

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

    @POST("groups/{groupId}/leave")
    suspend fun leaveGroup(@Path("groupId") groupId: String): Response<MessageResponse>

    @PATCH("groups/{groupId}/archive")
    suspend fun archiveGroup(@Path("groupId") groupId: String): Response<MessageResponse>

    @PATCH("groups/{groupId}/unarchive")
    suspend fun unarchiveGroup(@Path("groupId") groupId: String): Response<MessageResponse>

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

    @PATCH("admin/users/{userId}/balance")
    suspend fun updateUserBalance(
        @Path("userId") userId: String,
        @Body body: AdminBalanceUpdateRequest
    ): Response<MessageResponse>

    // ── KYC (user) ────────────────────────────────────────────────────────
    @POST("kyc/documents")
    suspend fun uploadKycDocument(@Body body: KycUploadRequest): Response<KycDocumentResponse>

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

    // ── Espaces (Logique Métier v1.3) ─────────────────────────────────────
    @POST("groups/{groupId}/spaces")
    suspend fun createSpace(@Path("groupId") groupId: String, @Body body: CreateSpaceRequest): Response<SpaceResponse>

    @GET("groups/{groupId}/spaces")
    suspend fun getSpaces(@Path("groupId") groupId: String): Response<List<SpaceResponse>>

    @GET("groups/{groupId}/spaces/{spaceId}")
    suspend fun getSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<SpaceResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/accept")
    suspend fun acceptSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/decline")
    suspend fun declineSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/force-launch")
    suspend fun forceLaunchSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/settle")
    suspend fun settleSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/early-settle")
    suspend fun requestEarlySettle(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/early-settle/respond")
    suspend fun respondEarlySettle(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String, @Body body: EarlySettleVoteRequest): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/quorum/confirm")
    suspend fun confirmQuorum(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/quorum/reject")
    suspend fun rejectQuorum(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @PATCH("groups/{groupId}/spaces/{spaceId}")
    suspend fun editSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String, @Body body: EditSpaceRequest): Response<SpaceResponse>

    @DELETE("groups/{groupId}/spaces/{spaceId}")
    suspend fun deleteSpace(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/assist")
    suspend fun assistMember(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String, @Body body: AssistRequest): Response<MessageResponse>

    @POST("groups/{groupId}/spaces/{spaceId}/transfer-launcher")
    suspend fun transferLauncher(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String, @Body body: TransferLauncherRequest): Response<MessageResponse>

    @GET("groups/{groupId}/spaces/{spaceId}/audit")
    suspend fun getSpaceAudit(@Path("groupId") groupId: String, @Path("spaceId") spaceId: String): Response<List<SpaceAuditEntry>>

    @GET("spaces/pending")
    suspend fun getPendingSpaceInvitations(): Response<List<PendingSpaceInvitation>>
}
