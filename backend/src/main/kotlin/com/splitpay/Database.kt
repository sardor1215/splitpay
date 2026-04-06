package com.splitpay

import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.ExpenseGroups
import com.splitpay.database.tables.ExpenseParticipants
import com.splitpay.database.tables.GroupMembers
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object Database {
    fun connect() {
        println(">>> Connecting to database...")
        org.jetbrains.exposed.sql.Database.connect(
            url = "jdbc:postgresql://168.231.83.18:5432/expense_app",
            driver = "org.postgresql.Driver",
            user = "app_user",
            password = "today170326"
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, ExpenseGroups, GroupMembers, Expenses, ExpenseParticipants
            )
        }
        println(">>> Database connected!")
    }
}
