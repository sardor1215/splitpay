package com.splitpay

import org.jetbrains.exposed.sql.Database

object Database {
    fun connect() {
        Database.connect(
            url = "jdbc:postgresql://168.231.83.18:5432/expense_app",
            driver = "org.postgresql.Driver",
            user = "app_user",
            password = "today170326"
        )
        println("Connected to PostgreSQL")
    }
}