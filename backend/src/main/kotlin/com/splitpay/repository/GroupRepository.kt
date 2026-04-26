package com.splitpay.repository

import com.splitpay.database.loggedTransaction
import com.splitpay.database.tables.ExpenseGroups
import com.splitpay.database.tables.Expenses
import com.splitpay.database.tables.GroupMembers
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

data class Group(
    val id: UUID,
    val name: String,
    val emoji: String,
    val description: String?,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val isArchived: Boolean,
    val archivedAt: OffsetDateTime?,
    val inviteToken: String?,
    val maxMembers: Int
)

data class GroupMember(
    val id: UUID,
    val groupId: UUID,
    val userId: UUID,
    val name: String,
    val role: String,
    val joinedAt: OffsetDateTime
)

object GroupRepository {

    // ── Create ─────────────────────────────────────────────────────────
    fun create(name: String, emoji: String, description: String?, createdBy: UUID): Group = loggedTransaction {
        val token = UUID.randomUUID().toString()
        val id = ExpenseGroups.insert {
            it[ExpenseGroups.name]        = name
            it[ExpenseGroups.emoji]       = emoji
            it[ExpenseGroups.description] = description
            it[ExpenseGroups.createdBy]   = createdBy
            it[ExpenseGroups.inviteToken] = token
            it[ExpenseGroups.createdAt]   = OffsetDateTime.now()
        }[ExpenseGroups.id]

        // Creator becomes admin
        GroupMembers.insert {
            it[GroupMembers.groupId]  = id
            it[GroupMembers.userId]   = createdBy
            it[GroupMembers.role]     = "admin"
            it[GroupMembers.joinedAt] = OffsetDateTime.now()
        }

        findById(id)!!
    }

    // ── Update ─────────────────────────────────────────────────────────
    fun update(groupId: UUID, name: String, emoji: String?, description: String?): Boolean = loggedTransaction {
        ExpenseGroups.update({ ExpenseGroups.id eq groupId }) {
            it[ExpenseGroups.name] = name
            if (emoji != null) it[ExpenseGroups.emoji] = emoji
            if (description != null) it[ExpenseGroups.description] = description
        } > 0
    }

    // ── Finders ────────────────────────────────────────────────────────
    fun findById(groupId: UUID): Group? = loggedTransaction {
        ExpenseGroups.select { ExpenseGroups.id eq groupId }
            .singleOrNull()?.toGroup()
    }

    fun findByInviteToken(token: String): Group? = loggedTransaction {
        ExpenseGroups.select { ExpenseGroups.inviteToken eq token }
            .singleOrNull()?.toGroup()
    }

    fun findByUser(userId: UUID): List<Group> = loggedTransaction {
        (ExpenseGroups innerJoin GroupMembers)
            .select { (GroupMembers.userId eq userId) and (ExpenseGroups.isArchived eq false) }
            .map { it.toGroup() }
    }

    // ── Members ────────────────────────────────────────────────────────
    fun getMembers(groupId: UUID): List<GroupMember> = loggedTransaction {
        (GroupMembers innerJoin Users)
            .select { GroupMembers.groupId eq groupId }
            .map { it.toMember() }
    }

    fun getMemberCount(groupId: UUID): Int = loggedTransaction {
        GroupMembers.select { GroupMembers.groupId eq groupId }.count().toInt()
    }

    fun getMemberCounts(groupIds: List<UUID>): Map<UUID, Int> = loggedTransaction {
        GroupMembers
            .slice(GroupMembers.groupId, GroupMembers.groupId.count())
            .select { GroupMembers.groupId inList groupIds }
            .groupBy(GroupMembers.groupId)
            .associate { it[GroupMembers.groupId] to it[GroupMembers.groupId.count()].toInt() }
    }

    fun getLastActivities(groupIds: List<UUID>): Map<UUID, OffsetDateTime> = loggedTransaction {
        val maxCreatedAt = Expenses.createdAt.max()
        Expenses
            .slice(Expenses.groupId, maxCreatedAt)
            .select { Expenses.groupId inList groupIds }
            .groupBy(Expenses.groupId)
            .associate { it[Expenses.groupId] to it[maxCreatedAt]!! }
    }

    fun getMember(groupId: UUID, userId: UUID): GroupMember? = loggedTransaction {
        (GroupMembers innerJoin Users).select {
            (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq userId)
        }.singleOrNull()?.toMember()
    }

    fun isMember(groupId: UUID, userId: UUID): Boolean =
        getMember(groupId, userId) != null

    fun isAdmin(groupId: UUID, userId: UUID): Boolean =
        getMember(groupId, userId)?.role == "admin"

    // ── Add member directly ────────────────────────────────────────────
    fun addMember(groupId: UUID, userId: UUID) = loggedTransaction {
        GroupMembers.insert {
            it[GroupMembers.groupId]  = groupId
            it[GroupMembers.userId]   = userId
            it[GroupMembers.role]     = "member"
            it[GroupMembers.joinedAt] = OffsetDateTime.now()
        }
    }

    // ── Join via invite link ────────────────────────────────────────────
    fun joinByToken(token: String, userId: UUID): Group? = loggedTransaction {
        val group = findByInviteToken(token) ?: return@loggedTransaction null

        // Check max members
        val count = getMemberCount(group.id)
        if (count >= group.maxMembers) return@loggedTransaction null

        // Already a member?
        if (isMember(group.id, userId)) return@loggedTransaction group

        GroupMembers.insert {
            it[GroupMembers.groupId]  = group.id
            it[GroupMembers.userId]   = userId
            it[GroupMembers.role]     = "member"
            it[GroupMembers.joinedAt] = OffsetDateTime.now()
        }
        group
    }

    // ── Remove member ──────────────────────────────────────────────────
    fun removeMember(groupId: UUID, userId: UUID): Boolean = loggedTransaction {
        GroupMembers.deleteWhere {
            (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq userId)
        } > 0
    }

    // ── Transfer admin ─────────────────────────────────────────────────
    fun transferAdmin(groupId: UUID, fromUserId: UUID, toUserId: UUID): Boolean = loggedTransaction {
        // Demote old admin
        GroupMembers.update({
            (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq fromUserId)
        }) { it[role] = "member" }

        // Promote new admin
        GroupMembers.update({
            (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq toUserId)
        }) { it[role] = "admin" } > 0
    }

    // ── Leave group ────────────────────────────────────────────────────
    fun leaveGroup(groupId: UUID, userId: UUID): Boolean = loggedTransaction {
        // If last admin, auto-promote next member
        if (isAdmin(groupId, userId)) {
            val nextMember = GroupMembers.select {
                (GroupMembers.groupId eq groupId) and (GroupMembers.userId neq userId)
            }.firstOrNull()

            if (nextMember != null) {
                GroupMembers.update({
                    (GroupMembers.groupId eq groupId) and
                    (GroupMembers.userId eq nextMember[GroupMembers.userId])
                }) { it[role] = "admin" }
            }
        }
        removeMember(groupId, userId)
    }

    // ── Archive ────────────────────────────────────────────────────────
    fun archive(groupId: UUID): Boolean = loggedTransaction {
        ExpenseGroups.update({ ExpenseGroups.id eq groupId }) {
            it[isArchived] = true
            it[archivedAt] = OffsetDateTime.now()
        } > 0
    }

    // ── Regenerate invite link ─────────────────────────────────────────
    fun regenerateInviteToken(groupId: UUID): String = loggedTransaction {
        val token = UUID.randomUUID().toString()
        ExpenseGroups.update({ ExpenseGroups.id eq groupId }) {
            it[inviteToken] = token
        }
        token
    }

    // ── Mappers ────────────────────────────────────────────────────────
    private fun ResultRow.toGroup() = Group(
        id          = this[ExpenseGroups.id],
        name        = this[ExpenseGroups.name],
        emoji       = this[ExpenseGroups.emoji],
        description = this[ExpenseGroups.description],
        createdBy   = this[ExpenseGroups.createdBy],
        createdAt   = this[ExpenseGroups.createdAt],
        isArchived  = this[ExpenseGroups.isArchived],
        archivedAt  = this[ExpenseGroups.archivedAt],
        inviteToken = this[ExpenseGroups.inviteToken],
        maxMembers  = this[ExpenseGroups.maxMembers]
    )

    private fun ResultRow.toMember() = GroupMember(
        id       = this[GroupMembers.id],
        groupId  = this[GroupMembers.groupId],
        userId   = this[GroupMembers.userId],
        name     = this[Users.name],
        role     = this[GroupMembers.role],
        joinedAt = this[GroupMembers.joinedAt]
    )
}
