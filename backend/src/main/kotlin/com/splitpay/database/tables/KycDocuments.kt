package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object KycDocuments : Table("kyc_documents") {
    val id              = uuid("id").autoGenerate()
    val userId          = uuid("user_id").references(Users.id)
    val docType         = varchar("doc_type", 30)      // id_front | id_back | passport | selfie
    val filePath        = text("file_path")
    val status          = varchar("status", 20).default("pending")  // pending | approved | rejected
    val rejectionReason = text("rejection_reason").nullable()
    val reviewedBy      = uuid("reviewed_by").nullable()
    val reviewedAt      = timestampWithTimeZone("reviewed_at").nullable()
    val createdAt       = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
