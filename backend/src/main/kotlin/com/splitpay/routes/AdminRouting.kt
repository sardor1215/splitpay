package com.splitpay.routes

import com.splitpay.database.tables.AmlAlerts
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.ExpenseGroups
import com.splitpay.database.tables.GroupMembers
import com.splitpay.database.tables.Users
import com.splitpay.repository.UserRepository
import com.splitpay.service.AmlService
import com.splitpay.service.FcmService
import com.splitpay.service.GdprService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

// ── Response models ────────────────────────────────────────────────────────

@Serializable data class AdminStatsResponse(
    val totalUsers: Int,
    val totalGroups: Int,
    val totalExpenses: Int,
    val totalAmount: Double
)

@Serializable data class AdminUserResponse(
    val id: String,
    val name: String,
    val email: String,
    val isVerified: Boolean,
    val isAdmin: Boolean,
    val amlStatus: String,
    val kycStatus: String,
    val accountBalance: Double,
    val lastActivityAt: String?,
    val createdAt: String
)

@Serializable data class AdminBalanceUpdateRequest(
    val mode: String,       // "set" | "add" | "subtract"
    val amount: Double,
    val note: String? = null
)

@Serializable data class AdminGroupResponse(
    val id: String,
    val name: String,
    val emoji: String,
    val memberCount: Int,
    val expenseCount: Int,
    val totalAmount: Double,
    val createdAt: String
)

@Serializable data class AmlAlertResponse(
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

@Serializable data class ReviewAlertRequest(
    val status: String   // cleared | suspended
)

@Serializable data class GdprConfigResponse(val config: Map<String, String>)

@Serializable data class GdprConfigUpdateRequest(
    val archiveAfterMonths: Int? = null,
    val deleteAfterMonths: Int? = null,
    val largeTransactionThreshold: Double? = null,
    val highFrequencyCount: Int? = null,
    val highFrequencyWindowHours: Int? = null,
    val newAccountDays: Int? = null,
    val autoSuspendAfterAlerts: Int? = null
)

// ── Helper ─────────────────────────────────────────────────────────────────

private suspend fun ApplicationCall.requireAdmin(): Boolean {
    if (!UserRepository.isAdmin(currentUserId())) {
        respond(HttpStatusCode.Forbidden, MessageResponse("Admin access required"))
        return false
    }
    return true
}

// ── Routes ─────────────────────────────────────────────────────────────────

fun Route.adminRoutes() {
    authenticate("auth-jwt") {
        route("/admin") {

            // GET /admin/stats
            get("/stats") {
                if (!call.requireAdmin()) return@get
                val stats = transaction {
                    val totalUsers    = Users.select { Users.isDeleted eq false }.count().toInt()
                    val totalGroups   = ExpenseGroups.selectAll().count().toInt()
                    val totalExpenses = Expenses.selectAll().count().toInt()
                    val totalAmount   = Expenses.slice(Expenses.amount.sum())
                        .selectAll().firstOrNull()
                        ?.getOrNull(Expenses.amount.sum())?.toDouble() ?: 0.0
                    AdminStatsResponse(totalUsers, totalGroups, totalExpenses, totalAmount)
                }
                call.respond(stats)
            }

            // GET /admin/users
            get("/users") {
                if (!call.requireAdmin()) return@get
                val users = UserRepository.findAll().map {
                    AdminUserResponse(
                        id             = it.id.toString(),
                        name           = it.name,
                        email          = it.email,
                        isVerified     = it.isVerified,
                        isAdmin        = it.isAdmin,
                        amlStatus      = it.amlStatus,
                        kycStatus      = it.kycStatus,
                        accountBalance = it.accountBalance.toDouble(),
                        lastActivityAt = it.lastActivityAt?.toString(),
                        createdAt      = it.createdAt.toString()
                    )
                }
                call.respond(users)
            }

            // GET /admin/groups
            get("/groups") {
                if (!call.requireAdmin()) return@get
                val groups = transaction {
                    ExpenseGroups.selectAll().map { row ->
                        val groupId      = row[ExpenseGroups.id]
                        val memberCount  = GroupMembers.select { GroupMembers.groupId eq groupId }.count().toInt()
                        val expenseCount = Expenses.select { Expenses.groupId eq groupId }.count().toInt()
                        val totalAmount  = Expenses.slice(Expenses.amount.sum())
                            .select { Expenses.groupId eq groupId }
                            .firstOrNull()?.getOrNull(Expenses.amount.sum())?.toDouble() ?: 0.0
                        AdminGroupResponse(
                            id           = groupId.toString(),
                            name         = row[ExpenseGroups.name],
                            emoji        = row[ExpenseGroups.emoji],
                            memberCount  = memberCount,
                            expenseCount = expenseCount,
                            totalAmount  = totalAmount,
                            createdAt    = row[ExpenseGroups.createdAt].toString()
                        )
                    }
                }
                call.respond(groups)
            }

            // ── AML endpoints ──────────────────────────────────────────────

            // GET /admin/aml/alerts?status=pending
            get("/aml/alerts") {
                if (!call.requireAdmin()) return@get
                val statusFilter = call.request.queryParameters["status"]
                val alerts = transaction {
                    val query = if (statusFilter != null)
                        AmlAlerts.select { AmlAlerts.status eq statusFilter }
                    else
                        AmlAlerts.selectAll()
                    query.orderBy(AmlAlerts.createdAt, SortOrder.DESC).map { row ->
                        val userId   = row[AmlAlerts.userId]
                        val userName = Users.select { Users.id eq userId }
                            .singleOrNull()?.get(Users.name) ?: "Unknown"
                        val reviewer = row[AmlAlerts.reviewedBy]?.let { rid ->
                            Users.select { Users.id eq rid }.singleOrNull()?.get(Users.name)
                        }
                        AmlAlertResponse(
                            id          = row[AmlAlerts.id].toString(),
                            userId      = userId.toString(),
                            userName    = userName,
                            alertType   = row[AmlAlerts.alertType],
                            description = row[AmlAlerts.description],
                            amount      = row[AmlAlerts.amount]?.toDouble(),
                            expenseId   = row[AmlAlerts.expenseId]?.toString(),
                            status      = row[AmlAlerts.status],
                            reviewedBy  = reviewer,
                            reviewedAt  = row[AmlAlerts.reviewedAt]?.toString(),
                            createdAt   = row[AmlAlerts.createdAt].toString()
                        )
                    }
                }
                call.respond(alerts)
            }

            // PATCH /admin/aml/alerts/{alertId}
            patch("/aml/alerts/{alertId}") {
                if (!call.requireAdmin()) return@patch
                val alertId = call.parameters["alertId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid alert ID"))

                val body = call.receive<ReviewAlertRequest>()
                if (body.status !in listOf("cleared", "suspended"))
                    return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Status must be 'cleared' or 'suspended'"))

                val reviewerId = call.currentUserId()
                transaction {
                    AmlAlerts.update({ AmlAlerts.id eq alertId }) {
                        it[AmlAlerts.status]     = body.status
                        it[AmlAlerts.reviewedBy] = reviewerId
                        it[AmlAlerts.reviewedAt] = OffsetDateTime.now()
                    }
                    // If cleared: reset user amlStatus to clear (if no other pending alerts)
                    val userId = AmlAlerts.select { AmlAlerts.id eq alertId }
                        .singleOrNull()?.get(AmlAlerts.userId) ?: return@transaction
                    val hasPending = AmlAlerts.select {
                        (AmlAlerts.userId eq userId) and (AmlAlerts.status eq "pending")
                    }.count() > 0
                    val newStatus = when {
                        body.status == "suspended" -> "suspended"
                        hasPending                 -> "flagged"
                        else                       -> "clear"
                    }
                    UserRepository.updateAmlStatus(userId, newStatus)
                }
                call.respond(MessageResponse("Alert updated"))
            }

            // ── GDPR endpoints ─────────────────────────────────────────────

            // GET /admin/gdpr/config
            get("/gdpr/config") {
                if (!call.requireAdmin()) return@get
                call.respond(GdprConfigResponse(GdprService.getConfig()))
            }

            // PUT /admin/gdpr/config
            put("/gdpr/config") {
                if (!call.requireAdmin()) return@put
                val body = call.receive<GdprConfigUpdateRequest>()
                body.archiveAfterMonths?.let          { GdprService.setConfig("archive_after_months", it.toString()) }
                body.deleteAfterMonths?.let           { GdprService.setConfig("delete_after_months", it.toString()) }
                body.largeTransactionThreshold?.let   {
                    GdprService.setConfig("large_transaction_threshold", it.toString())
                    AmlService.largeTransactionThreshold = java.math.BigDecimal(it.toString())
                }
                body.highFrequencyCount?.let          {
                    GdprService.setConfig("high_frequency_count", it.toString())
                    AmlService.highFrequencyCount = it
                }
                body.highFrequencyWindowHours?.let    {
                    GdprService.setConfig("high_frequency_window_hours", it.toString())
                    AmlService.highFrequencyWindowHours = it
                }
                body.newAccountDays?.let              {
                    GdprService.setConfig("new_account_days", it.toString())
                    AmlService.newAccountDays = it
                }
                body.autoSuspendAfterAlerts?.let      {
                    GdprService.setConfig("auto_suspend_after_alerts", it.toString())
                    AmlService.autoSuspendAfterAlerts = it
                }
                GdprService.reloadConfig()
                call.respond(MessageResponse("Configuration updated"))
            }

            // POST /admin/gdpr/run-cleanup
            post("/gdpr/run-cleanup") {
                if (!call.requireAdmin()) return@post
                runCatching { GdprService.runDailyCleanup() }
                    .onSuccess { call.respond(MessageResponse("Cleanup completed")) }
                    .onFailure { call.respond(HttpStatusCode.InternalServerError, MessageResponse("Cleanup failed: ${it.message}")) }
            }

            // POST /admin/users/{userId}/lift-suspension
            post("/users/{userId}/lift-suspension") {
                if (!call.requireAdmin()) return@post
                val targetId = call.parameters["userId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

                // Clear all pending AML alerts for this user and lift the suspension
                transaction {
                    AmlAlerts.update({
                        (AmlAlerts.userId eq targetId) and (AmlAlerts.status eq "pending")
                    }) {
                        it[AmlAlerts.status]     = "cleared"
                        it[AmlAlerts.reviewedBy] = call.currentUserId()
                        it[AmlAlerts.reviewedAt] = OffsetDateTime.now()
                    }
                    UserRepository.updateAmlStatus(targetId, "clear")
                }

                // Notify user
                runCatching {
                    FcmService.notifyUser(
                        userId = targetId,
                        title  = "Account Restored",
                        body   = "Your account suspension has been lifted. You can resume using SplitPay."
                    )
                }
                call.respond(MessageResponse("Suspension lifted"))
            }

            // PATCH /admin/users/{userId}/aml-status
            patch("/users/{userId}/aml-status") {
                if (!call.requireAdmin()) return@patch
                val targetId = call.parameters["userId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))
                val body = call.receive<ReviewAlertRequest>()
                UserRepository.updateAmlStatus(targetId, body.status)
                call.respond(MessageResponse("User AML status updated"))
            }

            // PATCH /admin/users/{userId}/balance
            patch("/users/{userId}/balance") {
                if (!call.requireAdmin()) return@patch
                val targetId = call.parameters["userId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

                val body = call.receive<AdminBalanceUpdateRequest>()
                if (body.amount < 0)
                    return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Amount must be positive"))
                if (body.mode !in listOf("set", "add", "subtract"))
                    return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Mode must be 'set', 'add' or 'subtract'"))

                val amount = java.math.BigDecimal(body.amount.toString())
                val newBalance = when (body.mode) {
                    "set"      -> UserRepository.setAccountBalance(targetId, amount)
                    "add"      -> UserRepository.adjustAccountBalance(targetId, amount)
                    "subtract" -> UserRepository.adjustAccountBalance(targetId, amount.negate())
                    else       -> return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid mode"))
                }

                call.respond(mapOf(
                    "message"    to "Balance updated",
                    "newBalance" to newBalance.toDouble().toString()
                ))
            }
        }
    }
}
