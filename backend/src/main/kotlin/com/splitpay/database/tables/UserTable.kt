package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone  // needs exposed-java-time

object Users : Table("users") {
    val id                = uuid("id").autoGenerate()
    val name              = varchar("name", 100)
    val email             = varchar("email", 255).uniqueIndex()
    val phone             = varchar("phone", 20).uniqueIndex().nullable()
    val avatarUrl         = text("avatar_url").nullable()
    val createdAt         = timestampWithTimeZone("created_at")
    val passwordHash      = varchar("password_hash", 255).nullable()
    val isVerified        = bool("is_verified").default(false)
    val verificationToken = varchar("verification_token", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}