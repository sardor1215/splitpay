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
            idleTimeout       = 300_000   // 5 min — retire les connexions inactives avant que PG les ferme
            maxLifetime       = 600_000   // 10 min — recycle avant le timeout serveur (défaut PG = ~1h mais VPS souvent moins)
            keepaliveTime     = 60_000    // ping toutes les 60s pour garder les connexions vivantes
            connectionTestQuery = "SELECT 1"
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, ExpenseGroups, GroupMembers, Expenses, ExpenseParticipants,
                FcmTokens, AmlAlerts, GdprConfig, KycDocuments, ExpenseActivities,
                GroupInvitations, ExpenseInvitations
            )
        }
        println(">>> Database connected!")
    }
}
