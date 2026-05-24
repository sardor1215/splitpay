package com.splitpay.repository

import com.splitpay.database.tables.ExpenseInvitations
import com.splitpay.database.tables.ExpenseParticipants
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

data class ExpenseInvitationDetail(
    val id: UUID,
    val expenseId: UUID,
    val expenseTitle: String,
    val expenseAmount: Double,
    val groupId: UUID,
    val invitedUserId: UUID,
    val invitedById: UUID,
    val invitedByName: String,
    val share: Double,
    val status: String,
    val createdAt: OffsetDateTime
)

object ExpenseInvitationRepository {

    fun create(expenseId: UUID, groupId: UUID, invitedUserId: UUID, invitedById: UUID): UUID = transaction {
        ExpenseInvitations.insert {
            it[ExpenseInvitations.expenseId]     = expenseId
            it[ExpenseInvitations.groupId]       = groupId
            it[ExpenseInvitations.invitedUserId] = invitedUserId
            it[ExpenseInvitations.invitedById]   = invitedById
            it[ExpenseInvitations.status]        = "pending"
            it[ExpenseInvitations.createdAt]     = OffsetDateTime.now()
        }[ExpenseInvitations.id]
    }

    fun findPendingForUser(userId: UUID): List<ExpenseInvitationDetail> = transaction {
        (ExpenseInvitations innerJoin Expenses)
            .select {
                (ExpenseInvitations.invitedUserId eq userId) and
                (ExpenseInvitations.status eq "pending")
            }
            .map { row ->
                val inviterName = Users.select { Users.id eq row[ExpenseInvitations.invitedById] }
                    .singleOrNull()?.get(Users.name) ?: "Unknown"
                val share = ExpenseParticipants
                    .select { (ExpenseParticipants.expenseId eq row[ExpenseInvitations.expenseId]) and
                              (ExpenseParticipants.userId eq userId) }
                    .singleOrNull()?.get(ExpenseParticipants.share)?.toDouble() ?: 0.0
                ExpenseInvitationDetail(
                    id             = row[ExpenseInvitations.id],
                    expenseId      = row[ExpenseInvitations.expenseId],
                    expenseTitle   = row[Expenses.title],
                    expenseAmount  = row[Expenses.amount].toDouble(),
                    groupId        = row[ExpenseInvitations.groupId],
                    invitedUserId  = row[ExpenseInvitations.invitedUserId],
                    invitedById    = row[ExpenseInvitations.invitedById],
                    invitedByName  = inviterName,
                    share          = share,
                    status         = row[ExpenseInvitations.status],
                    createdAt      = row[ExpenseInvitations.createdAt]
                )
            }
    }

    fun findById(id: UUID): ExpenseInvitationDetail? = transaction {
        (ExpenseInvitations innerJoin Expenses)
            .select { ExpenseInvitations.id eq id }
            .singleOrNull()?.let { row ->
                val inviterName = Users.select { Users.id eq row[ExpenseInvitations.invitedById] }
                    .singleOrNull()?.get(Users.name) ?: "Unknown"
                val share = ExpenseParticipants
                    .select { (ExpenseParticipants.expenseId eq row[ExpenseInvitations.expenseId]) and
                              (ExpenseParticipants.userId eq row[ExpenseInvitations.invitedUserId]) }
                    .singleOrNull()?.get(ExpenseParticipants.share)?.toDouble() ?: 0.0
                ExpenseInvitationDetail(
                    id            = row[ExpenseInvitations.id],
                    expenseId     = row[ExpenseInvitations.expenseId],
                    expenseTitle  = row[Expenses.title],
                    expenseAmount = row[Expenses.amount].toDouble(),
                    groupId       = row[ExpenseInvitations.groupId],
                    invitedUserId = row[ExpenseInvitations.invitedUserId],
                    invitedById   = row[ExpenseInvitations.invitedById],
                    invitedByName = inviterName,
                    share         = share,
                    status        = row[ExpenseInvitations.status],
                    createdAt     = row[ExpenseInvitations.createdAt]
                )
            }
    }

    fun accept(id: UUID): Boolean = transaction {
        ExpenseInvitations.update({ ExpenseInvitations.id eq id }) {
            it[status] = "accepted"
        } > 0
    }

    // Decline: mark declined, remove from expense_participants, redistribute share to payer
    fun decline(id: UUID, userId: UUID, expenseId: UUID): Boolean = transaction {
        ExpenseInvitations.update({ ExpenseInvitations.id eq id }) {
            it[status] = "declined"
        }

        // Get the declined share before deleting
        val declinedShare = ExpenseParticipants
            .select { (ExpenseParticipants.expenseId eq expenseId) and (ExpenseParticipants.userId eq userId) }
            .singleOrNull()?.get(ExpenseParticipants.share) ?: java.math.BigDecimal.ZERO

        // Remove declined participant
        ExpenseParticipants.deleteWhere {
            (ExpenseParticipants.expenseId eq expenseId) and
            (ExpenseParticipants.userId eq userId)
        }

        // Add their share to the payer's participant record
        val payerId = Expenses.select { Expenses.id eq expenseId }
            .singleOrNull()?.get(Expenses.paidBy)

        if (payerId != null) {
            ExpenseParticipants.update({
                (ExpenseParticipants.expenseId eq expenseId) and
                (ExpenseParticipants.userId eq payerId)
            }) {
                with(SqlExpressionBuilder) {
                    it.update(ExpenseParticipants.share, ExpenseParticipants.share + declinedShare)
                }
            }
        }
        true
    }
}
