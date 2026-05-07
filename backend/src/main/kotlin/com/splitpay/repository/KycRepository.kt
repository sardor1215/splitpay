package com.splitpay.repository

import com.splitpay.database.tables.KycDocuments
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

data class KycDocument(
    val id: UUID,
    val userId: UUID,
    val docType: String,
    val filePath: String,
    val status: String,
    val rejectionReason: String?,
    val reviewedBy: UUID?,
    val reviewedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)

// The 4 document types required for a complete KYC submission
val REQUIRED_DOC_TYPES = setOf("id_front", "id_back", "passport", "selfie")

object KycRepository {

    fun upsertDocument(userId: UUID, docType: String, filePath: String): KycDocument = transaction {
        // Replace existing doc of same type — reset to "uploaded" (not yet submitted)
        KycDocuments.deleteWhere { (KycDocuments.userId eq userId) and (KycDocuments.docType eq docType) }
        val id = KycDocuments.insert {
            it[KycDocuments.userId]    = userId
            it[KycDocuments.docType]   = docType
            it[KycDocuments.filePath]  = filePath
            it[KycDocuments.status]    = "uploaded"
            it[KycDocuments.createdAt] = OffsetDateTime.now()
        }[KycDocuments.id]

        // Don't flip kycStatus yet — user must submit all docs first
        findById(id)!!
    }

    // Called explicitly when user taps "Submit for Review"
    fun submitForReview(userId: UUID): Boolean = transaction {
        val uploadedTypes = KycDocuments
            .select { (KycDocuments.userId eq userId) and (KycDocuments.status eq "uploaded") }
            .map { it[KycDocuments.docType] }
            .toSet()

        if (!uploadedTypes.containsAll(REQUIRED_DOC_TYPES)) return@transaction false

        // Mark all uploaded docs as pending
        KycDocuments.update({
            (KycDocuments.userId eq userId) and (KycDocuments.status eq "uploaded")
        }) { it[KycDocuments.status] = "pending" }

        // Set user kycStatus to pending
        Users.update({ Users.id eq userId }) { it[Users.kycStatus] = "pending" }
        true
    }

    fun findById(docId: UUID): KycDocument? = transaction {
        KycDocuments.select { KycDocuments.id eq docId }.singleOrNull()?.toDoc()
    }

    fun findByUser(userId: UUID): List<KycDocument> = transaction {
        KycDocuments.select { KycDocuments.userId eq userId }
            .orderBy(KycDocuments.createdAt, SortOrder.DESC)
            .map { it.toDoc() }
    }

    // Admin: only show docs that are pending review (not just uploaded)
    fun findPending(): List<KycDocument> = transaction {
        KycDocuments.select { KycDocuments.status eq "pending" }
            .orderBy(KycDocuments.createdAt, SortOrder.ASC)
            .map { it.toDoc() }
    }

    fun approveAllForUser(userId: UUID, reviewerId: UUID): Boolean = transaction {
        val now = OffsetDateTime.now()
        val updated = KycDocuments.update({
            (KycDocuments.userId eq userId) and (KycDocuments.status eq "pending")
        }) {
            it[KycDocuments.status]     = "approved"
            it[KycDocuments.reviewedBy] = reviewerId
            it[KycDocuments.reviewedAt] = now
        }
        if (updated > 0) {
            Users.update({ Users.id eq userId }) { it[Users.kycStatus] = "approved" }
        }
        updated > 0
    }

    fun review(docId: UUID, reviewerId: UUID, newStatus: String, rejectionReason: String?): Boolean = transaction {
        val updated = KycDocuments.update({ KycDocuments.id eq docId }) {
            it[KycDocuments.status]          = newStatus
            it[KycDocuments.rejectionReason] = rejectionReason
            it[KycDocuments.reviewedBy]      = reviewerId
            it[KycDocuments.reviewedAt]      = OffsetDateTime.now()
        } > 0

        if (updated) {
            val userId = KycDocuments.select { KycDocuments.id eq docId }
                .singleOrNull()?.get(KycDocuments.userId) ?: return@transaction false

            val statuses = KycDocuments.select { KycDocuments.userId eq userId }
                .map { it[KycDocuments.status] }
            val newKycStatus = when {
                statuses.any { it == "rejected" }                             -> "rejected"
                statuses.all { it == "approved" } && statuses.isNotEmpty()   -> "approved"
                statuses.any { it == "pending" }                              -> "pending"
                else                                                          -> "none"
            }
            Users.update({ Users.id eq userId }) { it[Users.kycStatus] = newKycStatus }
        }
        updated
    }

    private fun ResultRow.toDoc() = KycDocument(
        id              = this[KycDocuments.id],
        userId          = this[KycDocuments.userId],
        docType         = this[KycDocuments.docType],
        filePath        = this[KycDocuments.filePath],
        status          = this[KycDocuments.status],
        rejectionReason = this[KycDocuments.rejectionReason],
        reviewedBy      = this[KycDocuments.reviewedBy],
        reviewedAt      = this[KycDocuments.reviewedAt],
        createdAt       = this[KycDocuments.createdAt]
    )
}
