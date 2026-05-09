package com.splitpay.repository

import com.splitpay.database.loggedTransaction
import com.splitpay.database.tables.ExpenseActivities
import com.splitpay.database.tables.ExpenseParticipants
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.math.RoundingMode
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
    val category: String,
    val participants: List<ExpenseShare>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime? = null
)

data class ExpenseShare(
    val userId: UUID,
    val name: String,
    val share: BigDecimal
)

data class Balance(
    val userId: UUID,
    val name: String,
    val amount: BigDecimal
)

data class Settlement(
    val fromUserId: UUID,
    val fromName: String,
    val toUserId: UUID,
    val toName: String,
    val amount: BigDecimal
)

data class ExpenseActivity(
    val id: UUID,
    val userId: UUID,
    val userName: String,
    val action: String,
    val details: String?,
    val createdAt: OffsetDateTime
)

object ExpenseRepository {

    fun create(
        groupId: UUID,
        title: String,
        amount: BigDecimal,
        paidBy: UUID,
        splitMode: String,
        category: String = "other",
        participants: List<Pair<UUID, BigDecimal>>,
        actorId: UUID? = null
    ): Expense = loggedTransaction {
        val now = OffsetDateTime.now()
        val expenseId = Expenses.insert {
            it[Expenses.groupId]   = groupId
            it[Expenses.title]     = title
            it[Expenses.amount]    = amount
            it[Expenses.paidBy]    = paidBy
            it[Expenses.splitMode] = splitMode
            it[Expenses.category]  = category
            it[Expenses.createdAt] = now
        }[Expenses.id]

        participants.forEach { (userId, share) ->
            ExpenseParticipants.insert {
                it[ExpenseParticipants.expenseId] = expenseId
                it[ExpenseParticipants.userId]    = userId
                it[ExpenseParticipants.share]     = share
            }
        }

        // Log activity
        val actor  = actorId ?: paidBy
        val paidByName = userName(paidBy)
        val nParticipants = participants.size
        logActivity(expenseId, actor, "created",
            "Added \"$title\" — $${"%.2f".format(amount)} paid by $paidByName, split $splitMode between $nParticipants participant(s)")

        findById(expenseId)!!
    }

    fun update(
        expenseId: UUID,
        title: String,
        amount: BigDecimal,
        paidBy: UUID,
        splitMode: String,
        category: String,
        participants: List<Pair<UUID, BigDecimal>>,
        actorId: UUID? = null
    ): Expense? = loggedTransaction {
        val previous = findById(expenseId)
        val updated = Expenses.update({ Expenses.id eq expenseId }) {
            it[Expenses.title]     = title
            it[Expenses.amount]    = amount
            it[Expenses.paidBy]    = paidBy
            it[Expenses.splitMode] = splitMode
            it[Expenses.category]  = category
            it[Expenses.updatedAt] = OffsetDateTime.now()
        }
        if (updated == 0) return@loggedTransaction null

        ExpenseParticipants.deleteWhere { ExpenseParticipants.expenseId eq expenseId }
        participants.forEach { (userId, share) ->
            ExpenseParticipants.insert {
                it[ExpenseParticipants.expenseId] = expenseId
                it[ExpenseParticipants.userId]    = userId
                it[ExpenseParticipants.share]     = share
            }
        }

        // Build change details
        val changes = mutableListOf<String>()
        if (previous != null) {
            if (previous.title  != title)    changes.add("title: \"${previous.title}\" → \"$title\"")
            if (previous.amount != amount)   changes.add("amount: ${"%.2f".format(previous.amount)} → ${"%.2f".format(amount)}")
            if (previous.paidBy != paidBy)   changes.add("paid by: ${previous.paidByName} → ${userName(paidBy)}")
            if (previous.splitMode != splitMode) changes.add("split: ${previous.splitMode} → $splitMode")
        }
        val details = if (changes.isEmpty()) "Updated expense" else changes.joinToString(", ")
        logActivity(expenseId, actorId ?: paidBy, "updated", details)

        findById(expenseId)
    }

    fun delete(expenseId: UUID): Boolean = loggedTransaction {
        ExpenseParticipants.deleteWhere { ExpenseParticipants.expenseId eq expenseId }
        Expenses.deleteWhere { Expenses.id eq expenseId } > 0
    }

    fun findById(id: UUID): Expense? = loggedTransaction {
        val row = Expenses.select { Expenses.id eq id }.singleOrNull() ?: return@loggedTransaction null
        row.toExpense()
    }

    fun findByGroup(groupId: UUID): List<Expense> = loggedTransaction {
        Expenses.select { Expenses.groupId eq groupId }
            .orderBy(Expenses.createdAt, SortOrder.DESC)
            .map { it.toExpense() }
    }

    fun calculateBalances(groupId: UUID): List<Balance> = loggedTransaction {
        val net   = mutableMapOf<UUID, BigDecimal>()
        val names = mutableMapOf<UUID, String>()

        findByGroup(groupId).forEach { expense ->
            expense.participants.forEach { p ->
                net[p.userId]   = (net[p.userId]   ?: BigDecimal.ZERO) - p.share
                names[p.userId] = p.name
            }
            net[expense.paidBy]   = (net[expense.paidBy] ?: BigDecimal.ZERO) + expense.amount
            names[expense.paidBy] = expense.paidByName
        }

        net.map { (userId, amount) ->
            Balance(userId, names[userId] ?: "Unknown", amount.setScale(2, RoundingMode.HALF_UP))
        }.sortedByDescending { it.amount }
    }

    fun calculateUserBalancesForGroups(groupIds: List<UUID>, userId: UUID): Map<UUID, Double> = loggedTransaction {
        if (groupIds.isEmpty()) return@loggedTransaction emptyMap()
        val balances = mutableMapOf<UUID, BigDecimal>()

        // What the user paid across all groups (positive)
        Expenses
            .slice(Expenses.groupId, Expenses.amount.sum())
            .select { (Expenses.groupId inList groupIds) and (Expenses.paidBy eq userId) }
            .groupBy(Expenses.groupId)
            .forEach { row ->
                val gid = row[Expenses.groupId]
                balances[gid] = (balances[gid] ?: BigDecimal.ZERO) + (row[Expenses.amount.sum()] ?: BigDecimal.ZERO)
            }

        // What the user owes across all groups (negative)
        (ExpenseParticipants innerJoin Expenses)
            .slice(Expenses.groupId, ExpenseParticipants.share.sum())
            .select { (Expenses.groupId inList groupIds) and (ExpenseParticipants.userId eq userId) }
            .groupBy(Expenses.groupId)
            .forEach { row ->
                val gid = row[Expenses.groupId]
                balances[gid] = (balances[gid] ?: BigDecimal.ZERO) - (row[ExpenseParticipants.share.sum()] ?: BigDecimal.ZERO)
            }

        balances.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toDouble() }
    }

    fun calculateSettlements(groupId: UUID): List<Settlement> {
        val balances  = calculateBalances(groupId)
        val creditors = balances.filter { it.amount > BigDecimal.ZERO }
            .sortedByDescending { it.amount }.map { it to it.amount.toBigDecimal() }.toMutableList()
        val debtors   = balances.filter { it.amount < BigDecimal.ZERO }
            .sortedBy { it.amount }.map { it to it.amount.negate().toBigDecimal() }.toMutableList()

        val cAmounts  = creditors.map { it.second }.toMutableList()
        val dAmounts  = debtors.map { it.second }.toMutableList()

        val settlements = mutableListOf<Settlement>()
        var ci = 0; var di = 0

        while (ci < creditors.size && di < debtors.size) {
            val settle = cAmounts[ci].min(dAmounts[di])
            settlements.add(
                Settlement(
                    fromUserId = debtors[di].first.userId,
                    fromName   = debtors[di].first.name,
                    toUserId   = creditors[ci].first.userId,
                    toName     = creditors[ci].first.name,
                    amount     = settle.setScale(2, RoundingMode.HALF_UP)
                )
            )
            cAmounts[ci] = cAmounts[ci] - settle
            dAmounts[di] = dAmounts[di] - settle
            if (cAmounts[ci].compareTo(BigDecimal.ZERO) == 0) ci++
            if (dAmounts[di].compareTo(BigDecimal.ZERO) == 0) di++
        }

        return settlements
    }

    fun getActivities(expenseId: UUID): List<ExpenseActivity> = loggedTransaction {
        (ExpenseActivities innerJoin Users)
            .select { ExpenseActivities.expenseId eq expenseId }
            .orderBy(ExpenseActivities.createdAt, SortOrder.ASC)
            .map {
                ExpenseActivity(
                    id        = it[ExpenseActivities.id],
                    userId    = it[ExpenseActivities.userId],
                    userName  = it[Users.name],
                    action    = it[ExpenseActivities.action],
                    details   = it[ExpenseActivities.details],
                    createdAt = it[ExpenseActivities.createdAt]
                )
            }
    }

    private fun logActivity(expenseId: UUID, userId: UUID, action: String, details: String?) {
        ExpenseActivities.insert {
            it[ExpenseActivities.expenseId] = expenseId
            it[ExpenseActivities.userId]    = userId
            it[ExpenseActivities.action]    = action
            it[ExpenseActivities.details]   = details
            it[ExpenseActivities.createdAt] = OffsetDateTime.now()
        }
    }

    private fun loadParticipants(expenseId: UUID): List<ExpenseShare> =
        (ExpenseParticipants innerJoin Users)
            .select { ExpenseParticipants.expenseId eq expenseId }
            .map { ExpenseShare(it[ExpenseParticipants.userId], it[Users.name], it[ExpenseParticipants.share]) }

    private fun userName(userId: UUID): String =
        Users.select { Users.id eq userId }.singleOrNull()?.get(Users.name) ?: "Unknown"

    private fun ResultRow.toExpense() = Expense(
        id           = this[Expenses.id],
        groupId      = this[Expenses.groupId],
        title        = this[Expenses.title],
        amount       = this[Expenses.amount],
        paidBy       = this[Expenses.paidBy],
        paidByName   = userName(this[Expenses.paidBy]),
        splitMode    = this[Expenses.splitMode],
        category     = this[Expenses.category],
        participants = loadParticipants(this[Expenses.id]),
        createdAt    = this[Expenses.createdAt],
        updatedAt    = this[Expenses.updatedAt]
    )
}

private fun BigDecimal.toBigDecimal() = this
