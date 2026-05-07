package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object AmlAlerts : Table("aml_alerts") {
    val id          = uuid("id").autoGenerate()
    val userId      = uuid("user_id").references(Users.id)
    val alertType   = varchar("alert_type", 50)   // large_transaction | high_frequency | new_account | unusual_pattern
    val description = text("description")
    val amount      = decimal("amount", 12, 2).nullable()
    val expenseId   = uuid("expense_id").nullable()
    val status      = varchar("status", 20).default("pending")  // pending | cleared | suspended
    val reviewedBy  = uuid("reviewed_by").nullable()
    val reviewedAt  = timestampWithTimeZone("reviewed_at").nullable()
    val createdAt   = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
