package com.splitpay.repository

import com.splitpay.database.tables.GroupInvitations
import com.splitpay.database.tables.ExpenseGroups
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

data class GroupInvitation(
    val id: UUID,
    val groupId: UUID,
    val groupName: String,
    val groupEmoji: String,
    val invitedUserId: UUID,
    val invitedById: UUID,
    val invitedByName: String,
    val status: String,
    val createdAt: OffsetDateTime
)

object GroupInvitationRepository {

    fun create(groupId: UUID, invitedUserId: UUID, invitedById: UUID): UUID = transaction {
        GroupInvitations.insert {
            it[GroupInvitations.groupId]       = groupId
            it[GroupInvitations.invitedUserId] = invitedUserId
            it[GroupInvitations.invitedById]   = invitedById
            it[GroupInvitations.status]        = "pending"
            it[GroupInvitations.createdAt]     = OffsetDateTime.now()
        }[GroupInvitations.id]
    }

    fun findPendingForUser(userId: UUID): List<GroupInvitation> = transaction {
        (GroupInvitations innerJoin ExpenseGroups)
            .select {
                (GroupInvitations.invitedUserId eq userId) and
                (GroupInvitations.status eq "pending")
            }
            .map { row ->
                val inviterName = Users.select { Users.id eq row[GroupInvitations.invitedById] }
                    .singleOrNull()?.get(Users.name) ?: "Unknown"
                GroupInvitation(
                    id            = row[GroupInvitations.id],
                    groupId       = row[GroupInvitations.groupId],
                    groupName     = row[ExpenseGroups.name],
                    groupEmoji    = row[ExpenseGroups.emoji],
                    invitedUserId = row[GroupInvitations.invitedUserId],
                    invitedById   = row[GroupInvitations.invitedById],
                    invitedByName = inviterName,
                    status        = row[GroupInvitations.status],
                    createdAt     = row[GroupInvitations.createdAt]
                )
            }
    }

    fun findById(id: UUID): GroupInvitation? = transaction {
        (GroupInvitations innerJoin ExpenseGroups)
            .select { GroupInvitations.id eq id }
            .singleOrNull()?.let { row ->
                val inviterName = Users.select { Users.id eq row[GroupInvitations.invitedById] }
                    .singleOrNull()?.get(Users.name) ?: "Unknown"
                GroupInvitation(
                    id            = row[GroupInvitations.id],
                    groupId       = row[GroupInvitations.groupId],
                    groupName     = row[ExpenseGroups.name],
                    groupEmoji    = row[ExpenseGroups.emoji],
                    invitedUserId = row[GroupInvitations.invitedUserId],
                    invitedById   = row[GroupInvitations.invitedById],
                    invitedByName = inviterName,
                    status        = row[GroupInvitations.status],
                    createdAt     = row[GroupInvitations.createdAt]
                )
            }
    }

    fun updateStatus(id: UUID, status: String): Boolean = transaction {
        GroupInvitations.update({ GroupInvitations.id eq id }) {
            it[GroupInvitations.status] = status
        } > 0
    }

    fun hasPending(groupId: UUID, userId: UUID): Boolean = transaction {
        GroupInvitations.select {
            (GroupInvitations.groupId eq groupId) and
            (GroupInvitations.invitedUserId eq userId) and
            (GroupInvitations.status eq "pending")
        }.count() > 0
    }
}
