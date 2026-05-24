package com.splitpay.routes

import com.splitpay.repository.ExpenseInvitationRepository
import com.splitpay.repository.ExpenseRepository
import com.splitpay.repository.GroupInvitationRepository
import com.splitpay.repository.GroupRepository
import com.splitpay.repository.UserRepository
import com.splitpay.service.FcmService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

// ── Request models ─────────────────────────────────────────────────────────
@Serializable data class CreateGroupRequest(
    val name: String,
    val emoji: String = "💰",
    val description: String? = null
)

@Serializable data class AddMemberRequest(val userId: String)

@Serializable data class TransferAdminRequest(
    val toUserId: String
)

// ── Response models ────────────────────────────────────────────────────────
@Serializable data class GroupResponse(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String?,
    val createdBy: String,
    val isArchived: Boolean,
    val inviteToken: String?,
    val memberCount: Int,
    val lastActivityAt: String,
    val userBalance: Double = 0.0
)

@Serializable data class GroupMemberResponse(
    val userId: String,
    val name: String,
    val role: String,
    val joinedAt: String
)

@Serializable data class UpdateGroupRequest(
    val name: String,
    val emoji: String? = null,
    val description: String? = null
)

fun Route.groupRoutes() {

    authenticate("auth-jwt") {

        route("/groups") {

            // POST /groups — create group
            post {
                val userId = call.currentUserId()
                val body   = call.receive<CreateGroupRequest>()

                if (body.name.isBlank())
                    return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Group name is required"))

                val group = GroupRepository.create(body.name, body.emoji, body.description, userId)
                call.respond(HttpStatusCode.Created, group.toResponse())
            }

            // GET /groups — list my groups
            get {
                val userId = call.currentUserId()
                val groups = GroupRepository.findByUser(userId)
                val ids = groups.map { it.id }
                val counts       = GroupRepository.getMemberCounts(ids)
                val lastActs     = GroupRepository.getLastActivities(ids)
                val userBalances = ExpenseRepository.calculateUserBalancesForGroups(ids, userId)
                val sorted = groups.sortedByDescending { lastActs[it.id] ?: it.createdAt }
                call.respond(sorted.map {
                    it.toResponse(
                        memberCount    = counts[it.id] ?: 1,
                        lastActivityAt = lastActs[it.id] ?: it.createdAt,
                        userBalance    = userBalances[it.id] ?: 0.0
                    )
                })
            }

            route("/{groupId}") {

                // GET /groups/:id
                get {
                    val groupId = call.groupId() ?: return@get
                    val userId  = call.currentUserId()

                    val group = GroupRepository.findById(groupId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))

                    if (!GroupRepository.isMember(groupId, userId))
                        return@get call.respond(HttpStatusCode.Forbidden, MessageResponse("You are not a member of this group"))

                    call.respond(group.toResponse())
                }

                // GET /groups/:id/members
                get("/members") {
                    val groupId = call.groupId() ?: return@get
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isMember(groupId, userId))
                        return@get call.respond(HttpStatusCode.Forbidden, MessageResponse("Not a member"))

                    val members = GroupRepository.getMembers(groupId).map {
                        GroupMemberResponse(it.userId.toString(), it.name, it.role, it.joinedAt.toString())
                    }
                    call.respond(members)
                }

                // GET /groups/:id/invite — get invite link
                get("/invite") {
                    val groupId = call.groupId() ?: return@get
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@get call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can view the invite link"))

                    val group = GroupRepository.findById(groupId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))

                    val baseUrl = System.getenv("BASE_URL") ?: "http://localhost:8080"
                    call.respond(mapOf("inviteLink" to "$baseUrl/groups/join/${group.inviteToken}"))
                }

                // POST /groups/:id/invite/regenerate — new invite link
                post("/invite/regenerate") {
                    val groupId = call.groupId() ?: return@post
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can regenerate the invite link"))

                    val token   = GroupRepository.regenerateInviteToken(groupId)
                    val baseUrl = System.getenv("BASE_URL") ?: "http://localhost:8080"
                    call.respond(mapOf("inviteLink" to "$baseUrl/groups/join/$token"))
                }

                // POST /groups/:id/members — add member (or send invite if consent required)
                post("/members") {
                    val groupId  = call.groupId() ?: return@post
                    val adminId  = call.currentUserId()
                    val body     = call.receive<AddMemberRequest>()
                    val memberId = runCatching { UUID.fromString(body.userId) }.getOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

                    if (!GroupRepository.isAdmin(groupId, adminId))
                        return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can add members"))

                    if (GroupRepository.isMember(groupId, memberId))
                        return@post call.respond(HttpStatusCode.Conflict, MessageResponse("User is already a member"))

                    if (GroupInvitationRepository.hasPending(groupId, memberId))
                        return@post call.respond(HttpStatusCode.Conflict, MessageResponse("An invitation is already pending for this user"))

                    val targetUser = UserRepository.findById(memberId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("User not found"))

                    if (targetUser.requireConsent) {
                        // Send invitation instead of adding directly
                        val invitationId = GroupInvitationRepository.create(groupId, memberId, adminId)
                        val group = GroupRepository.findById(groupId)
                        val inviterName = UserRepository.findById(adminId)?.name ?: "Someone"
                        runCatching {
                            FcmService.notifyUser(memberId,
                                title = "Group Invitation",
                                body  = "$inviterName invited you to join ${group?.name ?: "a group"}",
                                data  = mapOf("type" to "group_invitation", "invitationId" to invitationId.toString())
                            )
                        }
                        call.respond(HttpStatusCode.Accepted, MessageResponse("Invitation sent — waiting for user consent"))
                    } else {
                        GroupRepository.addMember(groupId, memberId)
                        call.respond(HttpStatusCode.OK, MessageResponse("Member added"))
                        val memberIds = GroupRepository.getMembers(groupId).map { it.userId }
                        val group = GroupRepository.findById(groupId)
                        runCatching {
                            FcmService.notifyGroupMembers(groupId, adminId, memberIds,
                                "New member", "${targetUser.name} joined ${group?.name ?: "the group"}")
                        }
                    }
                }

                // DELETE /groups/:id/members/:userId — remove member
                delete("/members/{memberId}") {
                    val groupId  = call.groupId() ?: return@delete
                    val adminId  = call.currentUserId()
                    val memberId = call.parameters["memberId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid member ID"))

                    if (!GroupRepository.isAdmin(groupId, adminId))
                        return@delete call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can remove members"))

                    if (memberId == adminId)
                        return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("Use /leave to leave the group"))

                    val success = GroupRepository.removeMember(groupId, memberId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Member removed"))
                    else call.respond(HttpStatusCode.NotFound, MessageResponse("Member not found"))
                }

                // POST /groups/:id/transfer-admin
                post("/transfer-admin") {
                    val groupId = call.groupId() ?: return@post
                    val adminId = call.currentUserId()
                    val body    = call.receive<TransferAdminRequest>()
                    val toId    = runCatching { UUID.fromString(body.toUserId) }.getOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

                    if (!GroupRepository.isAdmin(groupId, adminId))
                        return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can transfer admin role"))

                    if (!GroupRepository.isMember(groupId, toId))
                        return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Target user is not a member"))

                    val success = GroupRepository.transferAdmin(groupId, adminId, toId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Admin transferred successfully"))
                    else call.respond(HttpStatusCode.InternalServerError, MessageResponse("Failed to transfer admin"))
                }

                // POST /groups/:id/leave
                post("/leave") {
                    val groupId = call.groupId() ?: return@post
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isMember(groupId, userId))
                        return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("You are not a member of this group"))

                    val memberCount = GroupRepository.getMemberCount(groupId)
                    if (memberCount == 1) {
                        // Last member — archive the group
                        GroupRepository.archive(groupId)
                        return@post call.respond(HttpStatusCode.OK, MessageResponse("You were the last member. Group has been archived."))
                    }

                    val success = GroupRepository.leaveGroup(groupId, userId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Left group successfully"))
                    else call.respond(HttpStatusCode.InternalServerError, MessageResponse("Failed to leave group"))
                }

                // PATCH /groups/:id/archive
                patch("/archive") {
                    val groupId = call.groupId() ?: return@patch
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@patch call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can archive the group"))

                    val success = GroupRepository.archive(groupId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Group archived successfully"))
                    else call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))
                }
                
                // PATCH /groups/:id — update group
                patch {
                    val groupId = call.groupId() ?: return@patch
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isMember(groupId, userId))
                        return@patch call.respond(HttpStatusCode.Forbidden, MessageResponse("Not a member"))

                    val body = call.receive<UpdateGroupRequest>()
                    val success = GroupRepository.update(groupId, body.name, body.emoji, body.description)
                    if (success) {
                        val group = GroupRepository.findById(groupId)!!
                        call.respond(group.toResponse())
                        val memberIds = GroupRepository.getMembers(groupId).map { it.userId }
                        runCatching {
                            FcmService.notifyGroupMembers(groupId, userId, memberIds,
                                "Group updated", "\"${body.name}\" has been renamed")
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))
                    }
                }
            }

            // POST /groups/join/:token — join via invite link (no groupId needed)
            post("/join/{token}") {
                val userId = call.currentUserId()
                val token  = call.parameters["token"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing invite token"))

                // Check max members before joining
                val group = GroupRepository.findByInviteToken(token)
                    ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Invalid invite link"))

                val memberCount = GroupRepository.getMemberCount(group.id)
                if (memberCount >= group.maxMembers)
                    return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Group is full (max ${group.maxMembers} members)"))

                val joined = GroupRepository.joinByToken(token, userId)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Could not join group"))

                call.respond(HttpStatusCode.OK, joined.toResponse())
            }
        }

        // ── Invitations ────────────────────────────────────────────────────

        // GET /invitations/pending — returns both group and expense invitations
        get("/invitations/pending") {
            val userId       = call.currentUserId()
            val groupInvs    = GroupInvitationRepository.findPendingForUser(userId).map {
                PendingInvitationResponse(
                    id            = it.id.toString(),
                    type          = "group",
                    title         = it.groupName,
                    subtitle      = "Invited by ${it.invitedByName}",
                    emoji         = it.groupEmoji,
                    amount        = null,
                    invitedByName = it.invitedByName,
                    createdAt     = it.createdAt.toString()
                )
            }
            val expenseInvs  = ExpenseInvitationRepository.findPendingForUser(userId).map {
                PendingInvitationResponse(
                    id            = it.id.toString(),
                    type          = "expense",
                    title         = it.expenseTitle,
                    subtitle      = "Added by ${it.invitedByName}",
                    emoji         = "💸",
                    amount        = it.share,
                    invitedByName = it.invitedByName,
                    createdAt     = it.createdAt.toString()
                )
            }
            call.respond(groupInvs + expenseInvs)
        }

        // POST /invitations/{id}/accept
        post("/invitations/{id}/accept") {
            val userId       = call.currentUserId()
            val invitationId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid invitation ID"))

            val inv = GroupInvitationRepository.findById(invitationId)
                ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Invitation not found"))

            if (inv.invitedUserId != userId)
                return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Not your invitation"))

            if (inv.status != "pending")
                return@post call.respond(HttpStatusCode.Conflict, MessageResponse("Invitation already ${inv.status}"))

            GroupRepository.addMember(inv.groupId, userId)
            GroupInvitationRepository.updateStatus(invitationId, "accepted")
            call.respond(HttpStatusCode.OK, MessageResponse("You joined ${inv.groupName}"))

            val memberIds = GroupRepository.getMembers(inv.groupId).map { it.userId }
            val userName  = UserRepository.findById(userId)?.name ?: "A new member"
            runCatching {
                FcmService.notifyGroupMembers(inv.groupId, userId, memberIds,
                    "New member", "$userName joined ${inv.groupName}")
            }
        }

        // POST /invitations/{id}/decline
        post("/invitations/{id}/decline") {
            val userId       = call.currentUserId()
            val invitationId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid invitation ID"))

            val inv = GroupInvitationRepository.findById(invitationId)
                ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Invitation not found"))

            if (inv.invitedUserId != userId)
                return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Not your invitation"))

            GroupInvitationRepository.updateStatus(invitationId, "declined")
            call.respond(HttpStatusCode.OK, MessageResponse("Invitation declined"))
        }

        // POST /invitations/expense/{id}/accept
        post("/invitations/expense/{id}/accept") {
            val userId       = call.currentUserId()
            val invitationId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid invitation ID"))

            val inv = ExpenseInvitationRepository.findById(invitationId)
                ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Invitation not found"))

            if (inv.invitedUserId != userId)
                return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Not your invitation"))

            if (inv.status != "pending")
                return@post call.respond(HttpStatusCode.Conflict, MessageResponse("Invitation already ${inv.status}"))

            ExpenseInvitationRepository.accept(invitationId)
            call.respond(HttpStatusCode.OK, MessageResponse("You accepted the expense"))
        }

        // POST /invitations/expense/{id}/decline
        post("/invitations/expense/{id}/decline") {
            val userId       = call.currentUserId()
            val invitationId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid invitation ID"))

            val inv = ExpenseInvitationRepository.findById(invitationId)
                ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Invitation not found"))

            if (inv.invitedUserId != userId)
                return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Not your invitation"))

            if (inv.status != "pending")
                return@post call.respond(HttpStatusCode.Conflict, MessageResponse("Invitation already ${inv.status}"))

            ExpenseInvitationRepository.decline(invitationId, userId, inv.expenseId)
            call.respond(HttpStatusCode.OK, MessageResponse("You declined the expense"))
        }
    }
}

@Serializable data class PendingInvitationResponse(
    val id: String,
    val type: String,          // "group" | "expense"
    val title: String,
    val subtitle: String,
    val emoji: String,
    val amount: Double?,
    val invitedByName: String,
    val createdAt: String
)


// ── Helpers ────────────────────────────────────────────────────────────────
private suspend fun ApplicationCall.groupId(): UUID? {
    val id = parameters["groupId"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))
    return id
}

private fun com.splitpay.repository.Group.toResponse(
    memberCount: Int = GroupRepository.getMemberCount(id),
    lastActivityAt: java.time.OffsetDateTime = createdAt,
    userBalance: Double = 0.0
) = GroupResponse(
    id             = id.toString(),
    name           = name,
    emoji          = emoji,
    description    = description,
    createdBy      = createdBy.toString(),
    isArchived     = isArchived,
    inviteToken    = inviteToken,
    memberCount    = memberCount,
    lastActivityAt = lastActivityAt.toString(),
    userBalance    = userBalance
)
