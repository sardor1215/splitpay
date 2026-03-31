package com.splitpay.repository

import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import java.time.OffsetDateTime

data class User(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String?,
    val avatarUrl: String?,
    val passwordHash: String,
    val isVerified: Boolean,
    val verificationToken: String?,
    val createdAt: OffsetDateTime
)

object UserRepository {

    fun create(name: String, email: String, password: String, phone: String? = null): User = transaction {
        val token = UUID.randomUUID().toString()
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())

        val id = Users.insert {
            it[Users.name]              = name
            it[Users.email]             = email
            it[Users.phone]             = phone
            it[Users.passwordHash]      = hash
            it[Users.isVerified]        = true
            it[Users.verificationToken] = token
        }[Users.id]

        findById(id)!!
    }

    fun findById(userId: UUID): User? = transaction {
        Users.select { Users.id eq userId }
            .singleOrNull()
            ?.toUser()
    }

    fun findByEmail(email: String): User? = transaction {
        Users.select { Users.email eq email }
            .singleOrNull()
            ?.toUser()
    }

    fun verifyEmail(token: String): Boolean = transaction {
        val updated = Users.update({ Users.verificationToken eq token }) {
            it[isVerified]        = true
            it[verificationToken] = null
        }
        updated > 0
    }

    fun verifyPassword(user: User, password: String): Boolean =
        BCrypt.checkpw(password, user.passwordHash)

    private fun ResultRow.toUser() = User(
        id                = this[Users.id],
        name              = this[Users.name],
        email             = this[Users.email],
        phone             = this[Users.phone],
        avatarUrl         = this[Users.avatarUrl],
        passwordHash      = this[Users.passwordHash] ?: "",
        isVerified        = this[Users.isVerified],
        verificationToken = this[Users.verificationToken],
        createdAt         = this[Users.createdAt]
    )
}