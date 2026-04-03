package com.splitpay.routes

import com.splitpay.repository.GroupRepository
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
    val description: String? = null
)

@Serializable data class AddMemberRequest(val userId: String)

@Serializable data class TransferAdminRequest(
    val toUserId: String
)

@Serializable data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null
)

// ── Response models ────────────────────────────────────────────────────────
@Serializable data class GroupResponse(
    val id: String,
    val name: String,
    val description: String?,
    val createdBy: String,
    val isArchived: Boolean,
    val inviteToken: String?,
    val memberCount: Int
)

@Serializable data class GroupMemberResponse(
    val userId: String,
    val name: String,
    val role: String,
    val joinedAt: String
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

                val group = GroupRepository.create(body.name, body.description, userId)
                call.respond(HttpStatusCode.Created, group.toResponse())
            }

            // GET /groups — list my active groups
            get {
                val userId = call.currentUserId()
                val groups = GroupRepository.findByUser(userId).map { it.toResponse() }
                call.respond(groups)
            }

            // GET /groups/archived — list my archived groups
            get("/archived") {
                val userId = call.currentUserId()
                val groups = GroupRepository.findArchivedByUser(userId).map { it.toResponse() }
                call.respond(groups)
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

                // PATCH /groups/:id — update name/description (admin only)
                patch {
                    val groupId = call.groupId() ?: return@patch
                    val userId  = call.currentUserId()
                    val body    = call.receive<UpdateGroupRequest>()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@patch call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can edit the group"))

                    val success = GroupRepository.update(groupId, body.name, body.description)
                    if (success) {
                        val group = GroupRepository.findById(groupId)!!
                        call.respond(HttpStatusCode.OK, group.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))
                    }
                }

                // DELETE /groups/:id — delete group permanently (admin only)
                delete {
                    val groupId = call.groupId() ?: return@delete
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@delete call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can delete the group"))

                    val success = GroupRepository.delete(groupId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Group deleted"))
                    else call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))
                }

                // POST /groups/:id/members — add a user directly (admin only)
                post("/members") {
                    val groupId = call.groupId() ?: return@post
                    val adminId = call.currentUserId()
                    val body    = call.receive<AddMemberRequest>()
                    val userId  = runCatching { UUID.fromString(body.userId) }.getOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

                    if (!GroupRepository.isAdmin(groupId, adminId))
                        return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can add members"))

                    if (GroupRepository.isMember(groupId, userId))
                        return@post call.respond(HttpStatusCode.Conflict, MessageResponse("User is already a member"))

                    val group = GroupRepository.findById(groupId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))

                    val memberCount = GroupRepository.getMemberCount(groupId)
                    if (memberCount >= group.maxMembers)
                        return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Group is full (max ${group.maxMembers} members)"))

                    GroupRepository.addMember(groupId, userId)
                    call.respond(HttpStatusCode.OK, MessageResponse("Member added"))
                }

                // GET /groups/:id/members
                get("/members") {
                    val groupId = call.groupId() ?: return@get
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isMember(groupId, userId))
                        return@get call.respond(HttpStatusCode.Forbidden, MessageResponse("Not a member"))

                    val members = GroupRepository.getMembers(groupId).map { m ->
                        val user = com.splitpay.repository.UserRepository.findById(m.userId)
                        GroupMemberResponse(
                            userId   = m.userId.toString(),
                            name     = user?.name ?: "Unknown",
                            role     = m.role,
                            joinedAt = m.joinedAt.toString()
                        )
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

                // PATCH /groups/:id/unarchive
                patch("/unarchive") {
                    val groupId = call.groupId() ?: return@patch
                    val userId  = call.currentUserId()

                    if (!GroupRepository.isAdmin(groupId, userId))
                        return@patch call.respond(HttpStatusCode.Forbidden, MessageResponse("Only admins can unarchive the group"))

                    val success = GroupRepository.unarchive(groupId)
                    if (success) call.respond(HttpStatusCode.OK, MessageResponse("Group unarchived successfully"))
                    else call.respond(HttpStatusCode.NotFound, MessageResponse("Group not found"))
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
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────
private suspend fun ApplicationCall.groupId(): UUID? {
    val id = parameters["groupId"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, MessageResponse("Invalid group ID"))
    return id
}

private fun com.splitpay.repository.Group.toResponse() = GroupResponse(
    id          = id.toString(),
    name        = name,
    description = description,
    createdBy   = createdBy.toString(),
    isArchived  = isArchived,
    inviteToken = inviteToken,
    memberCount = GroupRepository.getMemberCount(id)
)
