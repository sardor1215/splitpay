package com.splitpay.routes

import com.splitpay.repository.ExpenseRepository
import com.splitpay.repository.GroupRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class CreateExpenseRequest(
    val title: String,
    val amount: Double,
    val paidByUserId: String,
    val splitMode: String = "equally",
    val participants: List<ExpenseParticipantRequest>
)

@Serializable
data class ExpenseParticipantRequest(
    val userId: String,
    val share: Double
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val title: String,
    val amount: Double,
    val paidByUserId: String,
    val paidByName: String,
    val splitMode: String,
    val createdAt: String,
    val participants: List<ExpenseParticipantResponse>
)

@Serializable
data class ExpenseParticipantResponse(
    val userId: String,
    val name: String,
    val share: Double
)

fun Route.expenseRoutes() {
    authenticate("auth-jwt") {
        route("/groups/{groupId}/expenses") {

            // POST /groups/:id/expenses
            post {
                val groupId = call.parameters["groupId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

                val userId = call.currentUserId()

                if (!GroupRepository.isMember(groupId, userId))
                    return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Not a member of this group"))

                val body = call.receive<CreateExpenseRequest>()

                if (body.title.isBlank())
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Title is required"))
                if (body.amount <= 0)
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Amount must be positive"))
                if (body.participants.isEmpty())
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("At least one participant required"))

                val paidByUserId = runCatching { UUID.fromString(body.paidByUserId) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid paidByUserId"))

                val participants = body.participants.mapNotNull { p ->
                    val uid = runCatching { UUID.fromString(p.userId) }.getOrNull() ?: return@mapNotNull null
                    uid to BigDecimal.valueOf(p.share)
                }

                val expense = ExpenseRepository.create(
                    groupId      = groupId,
                    title        = body.title,
                    amount       = BigDecimal.valueOf(body.amount),
                    paidBy       = paidByUserId,
                    splitMode    = body.splitMode,
                    participants = participants
                )

                call.respond(HttpStatusCode.Created, expense.toResponse())
            }

            // GET /groups/:id/expenses
            get {
                val groupId = call.parameters["groupId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))

                val userId = call.currentUserId()

                if (!GroupRepository.isMember(groupId, userId))
                    return@get call.respond(HttpStatusCode.Forbidden, MessageResponse("Not a member of this group"))

                val expenses = ExpenseRepository.findByGroup(groupId).map { it.toResponse() }
                call.respond(expenses)
            }
        }
    }
}

private fun com.splitpay.repository.Expense.toResponse() = ExpenseResponse(
    id           = id.toString(),
    title        = title,
    amount       = amount.toDouble(),
    paidByUserId = paidBy.toString(),
    paidByName   = paidByName,
    splitMode    = splitMode,
    createdAt    = createdAt.toLocalDate().toString(),
    participants = participants.map { p ->
        ExpenseParticipantResponse(
            userId = p.userId.toString(),
            name   = p.name,
            share  = p.share.toDouble()
        )
    }
)
