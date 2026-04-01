package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id                    = uuid("id").autoGenerate()
    val name                  = varchar("name", 100)
    val email                 = varchar("email", 255).uniqueIndex()
    val phone                 = varchar("phone", 20).uniqueIndex().nullable()
    val avatarUrl             = text("avatar_url").nullable()
    val createdAt             = timestampWithTimeZone("created_at")
    val passwordHash          = varchar("password_hash", 255).nullable()
    val isVerified            = bool("is_verified").default(false)
    val verificationToken     = varchar("verification_token", 255).nullable()
    val passwordResetToken    = varchar("password_reset_token", 255).nullable()
    val passwordResetExpiresAt= timestampWithTimeZone("password_reset_expires_at").nullable()
    val refreshToken          = varchar("refresh_token", 512).nullable()
    val googleId              = varchar("google_id", 255).uniqueIndex().nullable()
    val preferredCurrency     = char("preferred_currency", 3).default("USD")
    val isDeleted             = bool("is_deleted").default(false)
    val deletedAt             = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
