package com.splitpay.routes

import com.splitpay.repository.KycDocument
import com.splitpay.repository.KycRepository
import com.splitpay.repository.UserRepository
import com.splitpay.service.FcmService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

private val UPLOAD_DIR = File("./uploads/kyc").also { it.mkdirs() }
private val ALLOWED_TYPES = setOf("id_front", "id_back", "passport", "selfie")

@Serializable data class KycDocumentResponse(
    val id: String,
    val docType: String,
    val status: String,
    val rejectionReason: String?,
    val createdAt: String
)

@Serializable data class KycStatusResponse(
    val kycStatus: String,
    val documents: List<KycDocumentResponse>
)

@Serializable data class KycReviewRequest(
    val status: String,           // approved | rejected
    val rejectionReason: String? = null
)

fun Route.kycRoutes() {
    authenticate("auth-jwt") {

        // POST /kyc/documents — upload a document
        post("/kyc/documents") {
            val userId = call.currentUserId()
            var docType: String? = null
            var savedFile: File? = null

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when {
                    part is PartData.FormItem && part.name == "docType" -> {
                        docType = part.value
                    }
                    part is PartData.FileItem -> {
                        val ext       = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                        val filename  = "${userId}_${docType}_${System.currentTimeMillis()}.$ext"
                        val userDir   = File(UPLOAD_DIR, userId.toString()).also { it.mkdirs() }
                        savedFile     = File(userDir, filename)
                        part.streamProvider().use { input ->
                            savedFile!!.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                part.dispose()
            }

            if (docType == null || docType !in ALLOWED_TYPES)
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("docType must be one of: id_front, id_back, passport, selfie"))
            if (savedFile == null)
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("No file uploaded"))

            val doc = KycRepository.upsertDocument(userId, docType!!, savedFile!!.path)
            call.respond(HttpStatusCode.Created, doc.toResponse())
        }

        // POST /kyc/submit — submit all uploaded docs for review
        post("/kyc/submit") {
            val userId = call.currentUserId()
            val ok = KycRepository.submitForReview(userId)
            if (ok) call.respond(MessageResponse("Documents submitted for review"))
            else call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("All 4 documents are required: id_front, id_back, passport, selfie")
            )
        }

        // GET /kyc/status — current user's KYC status
        get("/kyc/status") {
            val userId = call.currentUserId()
            val user   = UserRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("User not found"))
            val docs   = KycRepository.findByUser(userId)
            call.respond(KycStatusResponse(user.kycStatus, docs.map { it.toResponse() }))
        }

        // ── Admin KYC endpoints ────────────────────────────────────────────

        // GET /admin/kyc/pending
        get("/admin/kyc/pending") {
            if (!call.requireAdmin()) return@get
            val docs = KycRepository.findPending()
            val result = docs.map { doc ->
                val user = UserRepository.findById(doc.userId)
                AdminKycDocResponse(
                    id          = doc.id.toString(),
                    userId      = doc.userId.toString(),
                    userName    = user?.name ?: "Unknown",
                    userEmail   = user?.email ?: "",
                    docType     = doc.docType,
                    status      = doc.status,
                    fileUrl     = "/admin/kyc/documents/${doc.id}/file",
                    createdAt   = doc.createdAt.toString()
                )
            }
            call.respond(result)
        }

        // POST /admin/kyc/users/{userId}/approve-all — approve all pending docs for a user
        post("/admin/kyc/users/{userId}/approve-all") {
            if (!call.requireAdmin()) return@post
            val targetId = call.parameters["userId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid user ID"))

            val ok = KycRepository.approveAllForUser(targetId, call.currentUserId())
            if (ok) {
                // Notify user
                runCatching {
                    FcmService.notifyUser(
                        userId = targetId,
                        title  = "Identity Verified",
                        body   = "Your KYC has been approved. All features are now unlocked."
                    )
                }
                call.respond(MessageResponse("All documents approved — user is now verified"))
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("No pending documents found for this user"))
            }
        }

        // PATCH /admin/kyc/documents/{docId} — approve or reject single doc
        patch("/admin/kyc/documents/{docId}") {
            if (!call.requireAdmin()) return@patch
            val docId = call.parameters["docId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid document ID"))

            val body = call.receive<KycReviewRequest>()
            if (body.status !in listOf("approved", "rejected"))
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Status must be 'approved' or 'rejected'"))
            if (body.status == "rejected" && body.rejectionReason.isNullOrBlank())
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Rejection reason is required"))

            val doc = KycRepository.findById(docId)
                ?: return@patch call.respond(HttpStatusCode.NotFound, MessageResponse("Document not found"))

            val ok = KycRepository.review(docId, call.currentUserId(), body.status, body.rejectionReason)
            if (ok) {
                // Notify user after review
                val msg = if (body.status == "approved")
                    "Your KYC has been approved. All features are now unlocked."
                else
                    "One of your KYC documents was rejected: ${body.rejectionReason}. Please re-upload."
                runCatching {
                    FcmService.notifyUser(userId = doc.userId, title = "KYC Update", body = msg)
                }
                call.respond(MessageResponse("Document ${body.status}"))
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("Document not found"))
            }
        }

        // GET /admin/kyc/documents/{docId}/file — serve the document file
        get("/admin/kyc/documents/{docId}/file") {
            if (!call.requireAdmin()) return@get
            val docId = call.parameters["docId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid document ID"))

            val doc  = KycRepository.findById(docId)
                ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("Document not found"))
            val file = File(doc.filePath)
            if (!file.exists())
                return@get call.respond(HttpStatusCode.NotFound, MessageResponse("File not found on server"))

            val contentType = when (file.extension.lowercase()) {
                "png"  -> ContentType.Image.PNG
                "pdf"  -> ContentType.Application.Pdf
                else   -> ContentType.Image.JPEG
            }
            call.respondFile(file)
        }
    }
}

@Serializable data class AdminKycDocResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val docType: String,
    val status: String,
    val fileUrl: String,
    val createdAt: String
)

private fun KycDocument.toResponse() = KycDocumentResponse(
    id              = id.toString(),
    docType         = docType,
    status          = status,
    rejectionReason = rejectionReason,
    createdAt       = createdAt.toString()
)

// requireAdmin is defined in AdminRouting.kt but is private — re-declare here
private suspend fun ApplicationCall.requireAdmin(): Boolean {
    if (!UserRepository.isAdmin(currentUserId())) {
        respond(HttpStatusCode.Forbidden, MessageResponse("Admin access required"))
        return false
    }
    return true
}
