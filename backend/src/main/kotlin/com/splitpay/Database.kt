package com.splitpay

import com.splitpay.database.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object Database {
    fun connect() {
        println(">>> Connecting to database...")
        val config = HikariConfig().apply {
            jdbcUrl         = "jdbc:postgresql://168.231.83.18:5432/expense_app"
            driverClassName = "org.postgresql.Driver"
            username        = "app_user"
            password        = "today170326"
            maximumPoolSize = 10
            minimumIdle     = 2
            connectionTimeout = 30_000
            idleTimeout       = 600_000
            maxLifetime       = 1_800_000
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, ExpenseGroups, GroupMembers, Expenses, ExpenseParticipants
            )
        }
        println(">>> Database connected!")
    }
}
