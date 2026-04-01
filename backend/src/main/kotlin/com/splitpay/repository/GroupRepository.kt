package com.splitpay.repository

import com.splitpay.database.loggedTransaction
import com.splitpay.database.tables.ExpenseGroups
import com.splitpay.database.tables.GroupMembers
import org.jetbrains.exposed.sql.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

data class Group(
    val id: UUID,
    val name: String,
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
    val role: String,
    val joinedAt: OffsetDateTime
)

object GroupRepository {

    // ── Create ─────────────────────────────────────────────────────────
    fun create(name: String, description: String?, createdBy: UUID): Group = loggedTransaction {
        val token = UUID.randomUUID().toString()
        val id = ExpenseGroups.insert {
            it[ExpenseGroups.name]        = name
            it[ExpenseGroups.description] = description
            it[ExpenseGroups.createdBy]   = createdBy
            it[ExpenseGroups.inviteToken] = token
        }[ExpenseGroups.id]

        // Creator becomes admin
        GroupMembers.insert {
            it[GroupMembers.groupId] = id
            it[GroupMembers.userId]  = createdBy
            it[GroupMembers.role]    = "admin"
        }

        findById(id)!!
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
        GroupMembers.select { GroupMembers.groupId eq groupId }
            .map { it.toMember() }
    }

    fun getMemberCount(groupId: UUID): Int = loggedTransaction {
        GroupMembers.select { GroupMembers.groupId eq groupId }.count().toInt()
    }

    fun getMember(groupId: UUID, userId: UUID): GroupMember? = loggedTransaction {
        GroupMembers.select {
            (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq userId)
        }.singleOrNull()?.toMember()
    }

    fun isMember(groupId: UUID, userId: UUID): Boolean =
        getMember(groupId, userId) != null

    fun isAdmin(groupId: UUID, userId: UUID): Boolean =
        getMember(groupId, userId)?.role == "admin"

    // ── Join via invite link ────────────────────────────────────────────
    fun joinByToken(token: String, userId: UUID): Group? = loggedTransaction {
        val group = findByInviteToken(token) ?: return@loggedTransaction null

        // Check max members
        val count = getMemberCount(group.id)
        if (count >= group.maxMembers) return@loggedTransaction null

        // Already a member?
        if (isMember(group.id, userId)) return@loggedTransaction group

        GroupMembers.insert {
            it[GroupMembers.groupId] = group.id
            it[GroupMembers.userId]  = userId
            it[GroupMembers.role]    = "member"
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
        role     = this[GroupMembers.role],
        joinedAt = this[GroupMembers.joinedAt]
    )
}
