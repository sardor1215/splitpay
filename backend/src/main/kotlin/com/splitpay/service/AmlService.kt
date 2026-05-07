package com.splitpay.service

import com.splitpay.database.tables.AmlAlerts
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

object AmlService {

    // Configurable thresholds (overridden via GdprConfig at runtime)
    var largeTransactionThreshold: BigDecimal = BigDecimal("1000.00")
    var highFrequencyCount: Int               = 10
    var highFrequencyWindowHours: Int         = 24
    var newAccountDays: Int                   = 30
    var autoSuspendAfterAlerts: Int           = 3   // pending alerts before auto-suspend

    fun checkExpense(expenseId: UUID, userId: UUID, amount: BigDecimal, groupId: UUID) {
        transaction {
            checkLargeTransaction(expenseId, userId, amount)
            checkHighFrequency(userId, groupId)
            checkNewAccountLargeTransaction(expenseId, userId, amount)
        }
    }

    private fun checkLargeTransaction(expenseId: UUID, userId: UUID, amount: BigDecimal) {
        if (amount >= largeTransactionThreshold) {
            createAlert(
                userId      = userId,
                alertType   = "large_transaction",
                description = "Expense of ${"%.2f".format(amount)} exceeds threshold of ${"%.2f".format(largeTransactionThreshold)}",
                amount      = amount,
                expenseId   = expenseId
            )
        }
    }

    private fun checkHighFrequency(userId: UUID, groupId: UUID) {
        val windowStart = OffsetDateTime.now().minusHours(highFrequencyWindowHours.toLong())
        val count = Expenses.select {
            (Expenses.paidBy eq userId) and
            (Expenses.groupId eq groupId) and
            (Expenses.createdAt greaterEq windowStart)
        }.count().toInt()

        if (count >= highFrequencyCount) {
            val alreadyFlagged = AmlAlerts.select {
                (AmlAlerts.userId eq userId) and
                (AmlAlerts.alertType eq "high_frequency") and
                (AmlAlerts.createdAt greaterEq windowStart)
            }.count() > 0
            if (!alreadyFlagged) {
                createAlert(
                    userId      = userId,
                    alertType   = "high_frequency",
                    description = "$count expenses in the last ${highFrequencyWindowHours}h (threshold: $highFrequencyCount)"
                )
            }
        }
    }

    private fun checkNewAccountLargeTransaction(expenseId: UUID, userId: UUID, amount: BigDecimal) {
        val cutoff = OffsetDateTime.now().minusDays(newAccountDays.toLong())
        val isNew = Users.select {
            (Users.id eq userId) and (Users.createdAt greaterEq cutoff)
        }.count() > 0

        if (isNew && amount >= largeTransactionThreshold.divide(BigDecimal(2))) {
            createAlert(
                userId      = userId,
                alertType   = "new_account",
                description = "New account (< $newAccountDays days) with a ${"%.2f".format(amount)} transaction",
                amount      = amount,
                expenseId   = expenseId
            )
        }
    }

    fun flagUnusualPattern(userId: UUID, description: String) {
        transaction {
            createAlert(userId = userId, alertType = "unusual_pattern", description = description)
        }
    }

    private fun createAlert(
        userId: UUID,
        alertType: String,
        description: String,
        amount: BigDecimal? = null,
        expenseId: UUID? = null
    ) {
        AmlAlerts.insert {
            it[AmlAlerts.userId]      = userId
            it[AmlAlerts.alertType]   = alertType
            it[AmlAlerts.description] = description
            it[AmlAlerts.amount]      = amount
            it[AmlAlerts.expenseId]   = expenseId
            it[AmlAlerts.status]      = "pending"
            it[AmlAlerts.createdAt]   = OffsetDateTime.now()
        }

        Users.update({ Users.id eq userId }) { it[Users.amlStatus] = "flagged" }

        // Check escalation threshold — auto-suspend if too many pending alerts
        checkEscalation(userId)
    }

    private fun checkEscalation(userId: UUID) {
        val pendingCount = AmlAlerts.select {
            (AmlAlerts.userId eq userId) and (AmlAlerts.status eq "pending")
        }.count().toInt()

        if (pendingCount >= autoSuspendAfterAlerts) {
            val alreadySuspended = Users.select { Users.id eq userId }
                .singleOrNull()?.get(Users.amlStatus) == "suspended"

            if (!alreadySuspended) {
                Users.update({ Users.id eq userId }) { it[Users.amlStatus] = "suspended" }

                val userName = Users.select { Users.id eq userId }
                    .singleOrNull()?.get(Users.name) ?: "Unknown"

                // Notify all admins (fire-and-forget outside transaction)
                val userIdCopy = userId
                val userNameCopy = userName
                val countCopy = pendingCount
                Thread {
                    runCatching {
                        FcmService.notifyAdmins(
                            title = "User Auto-Suspended",
                            body  = "$userNameCopy was automatically suspended after $countCopy unreviewed AML alerts",
                            data  = mapOf("userId" to userIdCopy.toString(), "type" to "aml_auto_suspend")
                        )
                    }
                }.start()
            }
        }
    }
}
