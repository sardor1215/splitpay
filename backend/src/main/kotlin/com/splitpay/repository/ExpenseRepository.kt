package com.splitpay.repository

import com.splitpay.database.loggedTransaction
import com.splitpay.database.tables.ExpenseParticipants
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Expense(
    val id: UUID,
    val groupId: UUID,
    val title: String,
    val amount: BigDecimal,
    val paidBy: UUID,
    val paidByName: String,
    val splitMode: String,
    val createdAt: OffsetDateTime,
    val participants: List<ExpenseParticipant>
)

data class ExpenseParticipant(
    val userId: UUID,
    val name: String,
    val share: BigDecimal
)

object ExpenseRepository {

    fun create(
        groupId: UUID,
        title: String,
        amount: BigDecimal,
        paidBy: UUID,
        splitMode: String,
        participants: List<Pair<UUID, BigDecimal>>  // userId -> share
    ): Expense = loggedTransaction {
        val expenseId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        Expenses.insert {
            it[Expenses.id]       = expenseId
            it[Expenses.groupId]  = groupId
            it[Expenses.title]    = title
            it[Expenses.amount]   = amount
            it[Expenses.paidBy]   = paidBy
            it[Expenses.splitMode] = splitMode
            it[Expenses.createdAt] = now
        }

        participants.forEach { (userId, share) ->
            ExpenseParticipants.insert {
                it[ExpenseParticipants.expenseId] = expenseId
                it[ExpenseParticipants.userId]    = userId
                it[ExpenseParticipants.share]     = share
            }
        }

        findById(expenseId)!!
    }

    fun findById(expenseId: UUID): Expense? = loggedTransaction {
        val row = Expenses
            .join(Users, JoinType.LEFT, Expenses.paidBy, Users.id)
            .select { Expenses.id eq expenseId }
            .singleOrNull() ?: return@loggedTransaction null

        val participants = ExpenseParticipants
            .join(Users, JoinType.LEFT, ExpenseParticipants.userId, Users.id)
            .select { ExpenseParticipants.expenseId eq expenseId }
            .map { p ->
                ExpenseParticipant(
                    userId = p[ExpenseParticipants.userId],
                    name   = p[Users.name],
                    share  = p[ExpenseParticipants.share]
                )
            }

        Expense(
            id           = row[Expenses.id],
            groupId      = row[Expenses.groupId],
            title        = row[Expenses.title],
            amount       = row[Expenses.amount],
            paidBy       = row[Expenses.paidBy],
            paidByName   = row[Users.name],
            splitMode    = row[Expenses.splitMode],
            createdAt    = row[Expenses.createdAt],
            participants = participants
        )
    }

    fun findByGroup(groupId: UUID): List<Expense> = loggedTransaction {
        val expenseIds = Expenses
            .select { Expenses.groupId eq groupId }
            .orderBy(Expenses.createdAt, SortOrder.DESC)
            .map { it[Expenses.id] }

        expenseIds.mapNotNull { findById(it) }
    }
}
