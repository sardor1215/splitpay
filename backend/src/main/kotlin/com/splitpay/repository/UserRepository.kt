package com.splitpay.repository

import com.splitpay.database.loggedTransaction
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.OffsetDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String?,
    val avatarUrl: String?,
    val passwordHash: String?,
    val isVerified: Boolean,
    val verificationToken: String?,
    val passwordResetToken: String?,
    val passwordResetExpiresAt: OffsetDateTime?,
    val refreshToken: String?,
    val googleId: String?,
    val preferredCurrency: String,
    val isDeleted: Boolean,
    val createdAt: OffsetDateTime
)

object UserRepository {

    // ── Create ────────────────────────────────────────────────────────────
    fun create(name: String, email: String, password: String, phone: String? = null): User = loggedTransaction {
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val id = Users.insert {
            it[Users.name]          = name
            it[Users.email]         = email
            it[Users.phone]         = phone
            it[Users.passwordHash]  = hash
            it[Users.isVerified]    = true   // auto-verified for now
            it[Users.createdAt]     = OffsetDateTime.now()
        }[Users.id]
        findById(id)!!
    }

    fun createWithGoogle(name: String, email: String, googleId: String, avatarUrl: String? = null): User = loggedTransaction {
        val id = Users.insert {
            it[Users.name]       = name
            it[Users.email]      = email
            it[Users.googleId]   = googleId
            it[Users.avatarUrl]  = avatarUrl
            it[Users.isVerified] = true
            it[Users.createdAt]  = OffsetDateTime.now()
        }[Users.id]
        findById(id)!!
    }

    // ── Finders ───────────────────────────────────────────────────────────
    fun findById(userId: UUID): User? = loggedTransaction {
        Users.select { (Users.id eq userId) and (Users.isDeleted eq false) }
            .singleOrNull()?.toUser()
    }

    fun findByEmail(email: String): User? = loggedTransaction {
        Users.select { (Users.email eq email) and (Users.isDeleted eq false) }
            .singleOrNull()?.toUser()
    }

    fun findByPhone(phone: String): User? = loggedTransaction {
        Users.select { (Users.phone eq phone) and (Users.isDeleted eq false) }
            .singleOrNull()?.toUser()
    }

    fun findByGoogleId(googleId: String): User? = loggedTransaction {
        Users.select { Users.googleId eq googleId }
            .singleOrNull()?.toUser()
    }

    fun findByResetToken(token: String): User? = loggedTransaction {
        Users.select { Users.passwordResetToken eq token }
            .singleOrNull()?.toUser()
    }

    fun findByRefreshToken(token: String): User? = loggedTransaction {
        Users.select { Users.refreshToken eq token }
            .singleOrNull()?.toUser()
    }

    fun findByPhones(phones: List<String>): Map<String, User> = loggedTransaction {
        if (phones.isEmpty()) return@loggedTransaction emptyMap()
        val normalizedToOriginal = phones.associateBy { normalizePhone(it) }
        Users.select { (Users.phone.isNotNull()) and (Users.isDeleted eq false) }
            .mapNotNull { row ->
                val dbPhone = row[Users.phone] ?: return@mapNotNull null
                val dbNorm  = normalizePhone(dbPhone)
                normalizedToOriginal[dbNorm]?.let { original -> original to row.toUser() }
            }.toMap()
    }

    private fun normalizePhone(phone: String) = phone.replace(Regex("[^+0-9]"), "")

    // ── Auth helpers ──────────────────────────────────────────────────────
    fun verifyPassword(user: User, password: String): Boolean =
        user.passwordHash != null && BCrypt.checkpw(password, user.passwordHash)

    fun verifyEmail(token: String): Boolean = loggedTransaction {
        Users.update({ Users.verificationToken eq token }) {
            it[isVerified]        = true
            it[verificationToken] = null
        } > 0
    }

    // ── Password reset ────────────────────────────────────────────────────
    fun setPasswordResetToken(userId: UUID, token: String, expiresAt: OffsetDateTime): Boolean = loggedTransaction {
        Users.update({ Users.id eq userId }) {
            it[passwordResetToken]     = token
            it[passwordResetExpiresAt] = expiresAt
        } > 0
    }

    fun resetPassword(token: String, newPassword: String): Boolean = loggedTransaction {
        val user = findByResetToken(token) ?: return@loggedTransaction false
        if (user.passwordResetExpiresAt != null && OffsetDateTime.now().isAfter(user.passwordResetExpiresAt)) {
            return@loggedTransaction false
        }
        Users.update({ Users.passwordResetToken eq token }) {
            it[passwordHash]           = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            it[passwordResetToken]     = null
            it[passwordResetExpiresAt] = null
        } > 0
    }

    // ── Refresh token ─────────────────────────────────────────────────────
    fun updateRefreshToken(userId: UUID, token: String?): Boolean = loggedTransaction {
        Users.update({ Users.id eq userId }) {
            it[refreshToken] = token
        } > 0
    }

    // ── Profile update ────────────────────────────────────────────────────
    fun updateProfile(
        userId: UUID,
        name: String? = null,
        phone: String? = null,
        avatarUrl: String? = null,
        preferredCurrency: String? = null
    ): User? = loggedTransaction {
        Users.update({ Users.id eq userId }) {
            if (name != null)              it[Users.name]              = name
            if (phone != null)             it[Users.phone]             = phone
            if (avatarUrl != null)         it[Users.avatarUrl]         = avatarUrl
            if (preferredCurrency != null) it[Users.preferredCurrency] = preferredCurrency
        }
        findById(userId)
    }

    // ── Soft delete ───────────────────────────────────────────────────────
    fun softDelete(userId: UUID): Boolean = loggedTransaction {
        Users.update({ Users.id eq userId }) {
            it[isDeleted] = true
            it[deletedAt] = OffsetDateTime.now()
            // Anonymize PII
            it[email]         = "deleted_${userId}@splitpay.invalid"
            it[name]          = "Deleted User"
            it[phone]         = null
            it[avatarUrl]     = null
            it[passwordHash]  = null
            it[refreshToken]  = null
            it[googleId]      = null
        } > 0
    }

    // ── Mapper ────────────────────────────────────────────────────────────
    private fun ResultRow.toUser() = User(
        id                    = this[Users.id],
        name                  = this[Users.name],
        email                 = this[Users.email],
        phone                 = this[Users.phone],
        avatarUrl             = this[Users.avatarUrl],
        passwordHash          = this[Users.passwordHash],
        isVerified            = this[Users.isVerified],
        verificationToken     = this[Users.verificationToken],
        passwordResetToken    = this[Users.passwordResetToken],
        passwordResetExpiresAt= this[Users.passwordResetExpiresAt],
        refreshToken          = this[Users.refreshToken],
        googleId              = this[Users.googleId],
        preferredCurrency     = this[Users.preferredCurrency],
        isDeleted             = this[Users.isDeleted],
        createdAt             = this[Users.createdAt]
    )
}
