package com.splitpay.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object GroupInvitations : Table("group_invitations") {
    val id            = uuid("id").autoGenerate()
    val groupId       = uuid("group_id").references(ExpenseGroups.id, onDelete = ReferenceOption.CASCADE)
    val invitedUserId = uuid("invited_user_id").references(Users.id)
    val invitedById   = uuid("invited_by_id").references(Users.id)
    val status        = varchar("status", 20).default("pending") // pending | accepted | declined
    val createdAt     = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
