package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ExpenseGroups : Table("expense_groups") {
    val id          = uuid("id").autoGenerate()
    val name        = varchar("name", 100)
    val description = text("description").nullable()
    val createdBy   = uuid("created_by").references(Users.id)
    val createdAt   = timestampWithTimeZone("created_at")
    val isArchived  = bool("is_archived").default(false)
    val archivedAt  = timestampWithTimeZone("archived_at").nullable()
    val inviteToken = varchar("invite_token", 255).uniqueIndex().nullable()
    val maxMembers  = integer("max_members").default(50)

    override val primaryKey = PrimaryKey(id)
}

object GroupMembers : Table("group_members") {
    val id       = uuid("id").autoGenerate()
    val groupId  = uuid("group_id").references(ExpenseGroups.id)
    val userId   = uuid("user_id").references(Users.id)
    val role     = varchar("role", 20).default("member")
    val joinedAt = timestampWithTimeZone("joined_at")

    override val primaryKey = PrimaryKey(id)
}
