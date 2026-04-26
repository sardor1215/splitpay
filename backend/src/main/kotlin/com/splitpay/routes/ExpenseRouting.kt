package com.splitpay.routes

import com.splitpay.repository.Expense
import com.splitpay.repository.ExpenseRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Serializable data class CreateExpenseRequest(
    val title: String,
    val amount: Double,
    val paidBy: String,
    val splitMode: String = "equally",
    val category: String = "other",
    val participants: List<ParticipantRequest>
)

@Serializable data class UpdateExpenseRequest(
    val title: String,
    val amount: Double,
    val paidBy: String,
    val splitMode: String = "equally",
    val category: String = "other",
    val participants: List<ParticipantRequest>
)

@Serializable data class ParticipantRequest(
    val userId: String,
    val share: Double? = null
)

@Serializable data class ExpenseResponse(
    val id: String,
    val groupId: String,
    val title: String,
    val amount: Double,
    val paidBy: String,
    val paidByName: String,
    val splitMode: String,
    val category: String,
    val participants: List<ParticipantResponse>,
    val createdAt: String,
    val updatedAt: String? = null
)

@Serializable data class ParticipantResponse(
    val userId: String,
    val name: String,
    val share: Double
)

@Serializable data class BalanceResponse(
    val userId: String,
    val name: String,
    val amount: Double
)

@Serializable data class SettlementResponse(
    val fromUserId: String,
    val fromName: String,
    val toUserId: String,
    val toName: String,
    val amount: Double
)

fun Route.expenseRoutes() {
    authenticate("auth-jwt") {

        route("/groups/{groupId}/expenses") {

            // POST — create expense
            post {
                val groupId = call.parameters["groupId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

                val body = call.receive<CreateExpenseRequest>()

                if (body.title.isBlank())
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Title is required"))
                if (body.amount <= 0)
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Amount must be positive"))
                if (body.participants.isEmpty())
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("At least one participant required"))

                val paidBy = runCatching { UUID.fromString(body.paidBy) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid paidBy user ID"))

                val total = BigDecimal(body.amount)
                val count = body.participants.size

                val participants: List<Pair<UUID, BigDecimal>> = when (body.splitMode) {
                    "equally" -> {
                        val share = total.divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
                        body.participants.map { UUID.fromString(it.userId) to share }
                    }
                    "exact" -> {
                        body.participants.map {
                            UUID.fromString(it.userId) to BigDecimal(it.share ?: 0.0).setScale(2, RoundingMode.HALF_UP)
                        }
                    }
                    "percentage" -> {
                        body.participants.map {
                            val share = total.multiply(BigDecimal(it.share ?: 0.0))
                                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                            UUID.fromString(it.userId) to share
                        }
                    }
                    else -> return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("splitMode must be equally, exact, or percentage"))
                }

                val expense = ExpenseRepository.create(groupId, body.title, total, paidBy, body.splitMode, body.category, participants)
                call.respond(HttpStatusCode.Created, expense.toResponse())
            }

            // GET — list expenses for group
            get {
                val groupId = call.parameters["groupId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

                val expenses = ExpenseRepository.findByGroup(groupId)
                call.respond(expenses.map { it.toResponse() })
            }

            route("/{expenseId}") {

                // PATCH — update expense
                patch {
                    val groupId = call.parameters["groupId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))
                    val expenseId = call.parameters["expenseId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid expense ID"))

                    val body = call.receive<UpdateExpenseRequest>()
                    if (body.title.isBlank())
                        return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Title is required"))
                    if (body.amount <= 0)
                        return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Amount must be positive"))

                    val paidBy = runCatching { UUID.fromString(body.paidBy) }.getOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid paidBy user ID"))

                    val total = BigDecimal(body.amount)
                    val count = body.participants.size

                    val participants: List<Pair<UUID, BigDecimal>> = when (body.splitMode) {
                        "equally" -> {
                            val share = total.divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
                            body.participants.map { UUID.fromString(it.userId) to share }
                        }
                        "exact" -> body.participants.map {
                            UUID.fromString(it.userId) to BigDecimal(it.share ?: 0.0).setScale(2, RoundingMode.HALF_UP)
                        }
                        "percentage" -> body.participants.map {
                            val share = total.multiply(BigDecimal(it.share ?: 0.0))
                                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                            UUID.fromString(it.userId) to share
                        }
                        else -> return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid splitMode"))
                    }

                    val updated = ExpenseRepository.update(expenseId, body.title, total, paidBy, body.splitMode, body.category, participants)
                        ?: return@patch call.respond(HttpStatusCode.NotFound, MessageResponse("Expense not found"))
                    call.respond(updated.toResponse())
                }

                // DELETE — delete expense
                delete {
                    val expenseId = call.parameters["expenseId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid expense ID"))

                    val deleted = ExpenseRepository.delete(expenseId)
                    if (deleted) call.respond(HttpStatusCode.OK, MessageResponse("Expense deleted"))
                    else call.respond(HttpStatusCode.NotFound, MessageResponse("Expense not found"))
                }
            }
        }

        // GET /groups/:groupId/balances
        get("/groups/{groupId}/balances") {
            val groupId = call.parameters["groupId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

            val balances = ExpenseRepository.calculateBalances(groupId)
            call.respond(balances.map { BalanceResponse(it.userId.toString(), it.name, it.amount.toDouble()) })
        }

        // GET /groups/:groupId/settlements
        get("/groups/{groupId}/settlements") {
            val groupId = call.parameters["groupId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

            val settlements = ExpenseRepository.calculateSettlements(groupId)
            call.respond(settlements.map {
                SettlementResponse(it.fromUserId.toString(), it.fromName, it.toUserId.toString(), it.toName, it.amount.toDouble())
            })
        }
    }
}

private fun Expense.toResponse() = ExpenseResponse(
    id           = id.toString(),
    groupId      = groupId.toString(),
    title        = title,
    amount       = amount.toDouble(),
    paidBy       = paidBy.toString(),
    paidByName   = paidByName,
    splitMode    = splitMode,
    category     = category,
    participants = participants.map { ParticipantResponse(it.userId.toString(), it.name, it.share.toDouble()) },
    createdAt    = createdAt.toString(),
    updatedAt    = updatedAt?.toString()
)
