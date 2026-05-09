package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ExpenseActivities : Table("expense_activities") {
    val id        = uuid("id").autoGenerate()
    val expenseId = uuid("expense_id").references(Expenses.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val userId    = uuid("user_id").references(Users.id)
    val action    = varchar("action", 30)   // created | updated | deleted
    val details   = text("details").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
