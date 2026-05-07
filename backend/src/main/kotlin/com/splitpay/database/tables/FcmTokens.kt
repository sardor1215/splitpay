package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object FcmTokens : Table("fcm_tokens") {
    val userId    = uuid("user_id").references(Users.id)
    val token     = varchar("token", 512)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(token)
}
