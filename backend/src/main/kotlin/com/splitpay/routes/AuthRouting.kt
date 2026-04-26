package com.splitpay.routes

import com.splitpay.auth.EmailService
import com.splitpay.auth.JwtService
import com.splitpay.repository.UserRepository
import com.splitpay.services.ExportService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.UUID

// ── Request models ─────────────────────────────────────────────────────────
@Serializable data class LookupRequest(val phones: List<String>)
@Serializable data class LookupUserResponse(val userId: String, val name: String, val phone: String, val email: String)

@Serializable data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

@Serializable data class LoginRequest(
    val identifier: String, // email or phone number
    val password: String
)

@Serializable data class ForgotPasswordRequest(
    val email: String
)

@Serializable data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

@Serializable data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable data class GoogleAuthRequest(
    val idToken: String
)

@Serializable data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val preferredCurrency: String? = null
)

// ── Response models ────────────────────────────────────────────────────────
@Serializable data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String
)

@Serializable data class UserProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val preferredCurrency: String,
    val isVerified: Boolean
)

@Serializable data class MessageResponse(val message: String)

@Serializable data class PhoneLookupRequest(val phones: List<String>)
@Serializable data class UserLookupResult(val phone: String, val userId: String, val name: String)

// ── Helper ─────────────────────────────────────────────────────────────────
fun ApplicationCall.currentUserId(): UUID =
    UUID.fromString(principal<JWTPrincipal>()!!.payload.subject)

// ── Routes ─────────────────────────────────────────────────────────────────
fun Route.authRoutes() {

    route("/auth") {

        // POST /auth/register
        post("/register") {
            val body = call.receive<RegisterRequest>()

            if (body.name.isBlank() || body.email.isBlank() || body.password.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Name, email and password are required"))

            if (!isValidPassword(body.password))
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Password must be at least 8 characters, include 1 uppercase letter and 1 digit"))

            if (UserRepository.findByEmail(body.email) != null)
                return@post call.respond(HttpStatusCode.Conflict, MessageResponse("Email already in use"))

            val user = UserRepository.create(body.name, body.email, body.password, body.phone)

            // EmailService.sendVerificationEmail(body.email, user.verificationToken!!) // re-enable when SMTP ready

            call.respond(HttpStatusCode.Created, MessageResponse("Registered successfully! You can now log in."))
        }

        // POST /auth/login
        post("/login") {
            val body = call.receive<LoginRequest>()

            // Accept email or phone number
            val user = if (body.identifier.contains("@"))
                UserRepository.findByEmail(body.identifier)
            else
                UserRepository.findByPhone(body.identifier)

            if (user == null || !UserRepository.verifyPassword(user, body.password))
                return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid credentials"))

            if (!user.isVerified)
                return@post call.respond(HttpStatusCode.Forbidden, MessageResponse("Please verify your email first"))

            val accessToken  = JwtService.generateAccessToken(user.id, user.email)
            val refreshToken = JwtService.generateRefreshToken(user.id)
            UserRepository.updateRefreshToken(user.id, refreshToken)

            call.respond(AuthResponse(accessToken, refreshToken, user.id.toString(), user.name, user.email))
        }

        // POST /auth/refresh
        post("/refresh") {
            val body  = call.receive<RefreshTokenRequest>()
            val userId = JwtService.validateRefreshToken(body.refreshToken)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid or expired refresh token"))

            val user = UserRepository.findById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("User not found"))

            // Validate stored token matches (rotation check)
            if (user.refreshToken != body.refreshToken)
                return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Refresh token has been revoked"))

            val newAccess  = JwtService.generateAccessToken(user.id, user.email)
            val newRefresh = JwtService.generateRefreshToken(user.id)
            UserRepository.updateRefreshToken(user.id, newRefresh)

            call.respond(AuthResponse(newAccess, newRefresh, user.id.toString(), user.name, user.email))
        }

        // GET /auth/verify?token=
        get("/verify") {
            val token = call.request.queryParameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing token"))

            val success = UserRepository.verifyEmail(token)
            if (success) call.respond(HttpStatusCode.OK, MessageResponse("Email verified! You can now log in."))
            else call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired token"))
        }

        // POST /auth/forgot-password
        post("/forgot-password") {
            val body = call.receive<ForgotPasswordRequest>()
            val user = UserRepository.findByEmail(body.email)

            // Always respond 200 to avoid email enumeration
            if (user != null) {
                val token     = UUID.randomUUID().toString()
                val expiresAt = OffsetDateTime.now().plusHours(1)
                UserRepository.setPasswordResetToken(user.id, token, expiresAt)

                // Print token to console for dev (replace with EmailService in prod)
                println("================================================")
                println("  PASSWORD RESET LINK FOR: ${body.email}")
                println("  http://localhost:8080/auth/reset-password?token=$token")
                println("  Expires at: $expiresAt")
                println("================================================")

                // EmailService.sendPasswordResetEmail(body.email, token) // enable in prod
            }

            call.respond(HttpStatusCode.OK, MessageResponse("If that email exists, a reset link has been sent."))
        }

        // POST /auth/reset-password
        post("/reset-password") {
            val body = call.receive<ResetPasswordRequest>()

            if (!isValidPassword(body.newPassword))
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Password must be at least 8 characters, include 1 uppercase letter and 1 digit"))

            val success = UserRepository.resetPassword(body.token, body.newPassword)
            if (success) call.respond(HttpStatusCode.OK, MessageResponse("Password reset successfully. You can now log in."))
            else call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired reset token"))
        }

        // POST /auth/google
        post("/google") {
            val body = call.receive<GoogleAuthRequest>()

            // Verify Google ID token
            // In production: use Google's tokeninfo endpoint or google-api-client lib
            // For now: call Google's tokeninfo API
            val googleUser = verifyGoogleToken(body.idToken)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid Google token"))

            // Find or create user
            var user = UserRepository.findByGoogleId(googleUser.first)
                ?: UserRepository.findByEmail(googleUser.second)

            if (user == null) {
                user = UserRepository.createWithGoogle(
                    name      = googleUser.third,
                    email     = googleUser.second,
                    googleId  = googleUser.first,
                    avatarUrl = googleUser.fourth
                )
            }

            val accessToken  = JwtService.generateAccessToken(user.id, user.email)
            val refreshToken = JwtService.generateRefreshToken(user.id)
            UserRepository.updateRefreshToken(user.id, refreshToken)

            call.respond(AuthResponse(accessToken, refreshToken, user.id.toString(), user.name, user.email))
        }

        // POST /auth/logout
        post("/logout") {
            // Optionally revoke refresh token if present in body
            try {
                val body = call.receive<RefreshTokenRequest>()
                val userId = JwtService.validateRefreshToken(body.refreshToken)
                if (userId != null) UserRepository.updateRefreshToken(userId, null)
            } catch (_: Exception) {}

            call.respond(HttpStatusCode.OK, MessageResponse("Logged out successfully"))
        }
    }

    // ── Protected user routes ──────────────────────────────────────────
    authenticate("auth-jwt") {

        // POST /users/lookup — find SplitPay users by phone numbers (batch)
        post("/users/lookup") {
            val body    = call.receive<PhoneLookupRequest>()
            val matched = UserRepository.findByPhones(body.phones)
            val results = matched.map { (phone, user) ->
                UserLookupResult(phone = phone, userId = user.id.toString(), name = user.name)
            }
            call.respond(results)
        }

        // GET /me
        get("/me") {
            val user = UserRepository.findById(call.currentUserId())
                ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("User not found"))

            call.respond(user.toProfileResponse())
        }

        // PATCH /me
        patch("/me") {
            val body = call.receive<UpdateProfileRequest>()
            val user = UserRepository.updateProfile(
                userId            = call.currentUserId(),
                name              = body.name,
                phone             = body.phone,
                avatarUrl         = body.avatarUrl,
                preferredCurrency = body.preferredCurrency
            ) ?: return@patch call.respond(HttpStatusCode.NotFound, MessageResponse("User not found"))

            call.respond(user.toProfileResponse())
        }

        // GET /me/export/json
        get("/me/export/json") {
            val jsonData = ExportService.exportUserDataJson(call.currentUserId())
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"splitpay-export.json\""
            )
            call.respondText(jsonData, ContentType.Application.Json)
        }

        // GET /me/export/pdf
        get("/me/export/pdf") {
            val pdfBytes = ExportService.exportUserDataPdf(call.currentUserId())
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"splitpay-export.txt\""
            )
            call.respondBytes(pdfBytes, ContentType.Text.Plain)
        }

        // DELETE /me
        delete("/me") {
            val success = UserRepository.softDelete(call.currentUserId())
            if (success) call.respond(HttpStatusCode.OK, MessageResponse("Account deleted. Your data has been anonymized."))
            else call.respond(HttpStatusCode.InternalServerError, MessageResponse("Failed to delete account"))
        }

        // POST /users/lookup — match phone numbers to app users
        post("/users/lookup") {
            val body = call.receive<LookupRequest>()
            val users = UserRepository.findByPhones(body.phones)
            call.respond(users.map { LookupUserResponse(it.id.toString(), it.name, it.phone ?: "", it.email) })
        }
    }
}

// ── Google token verification ──────────────────────────────────────────────
// Returns Triple(googleId, email, name, avatarUrl) or null
private suspend fun verifyGoogleToken(idToken: String): Quad? {
    return try {
        val url = "https://oauth2.googleapis.com/tokeninfo?id_token=$idToken"
        val client = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        client.requestMethod = "GET"
        if (client.responseCode != 200) return null

        val response = client.inputStream.bufferedReader().readText()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(response)
            .let { it as kotlinx.serialization.json.JsonObject }

        Quad(
            json["sub"]?.toString()?.trim('"')         ?: return null,
            json["email"]?.toString()?.trim('"')       ?: return null,
            json["name"]?.toString()?.trim('"')        ?: "User",
            json["picture"]?.toString()?.trim('"')
        )
    } catch (e: Exception) {
        null
    }
}

private fun isValidPassword(password: String): Boolean =
    password.length >= 8 && password.any { it.isUpperCase() } && password.any { it.isDigit() }

private data class Quad(val first: String, val second: String, val third: String, val fourth: String?)

private fun com.splitpay.repository.User.toProfileResponse() = UserProfileResponse(
    id                = id.toString(),
    name              = name,
    email             = email,
    phone             = phone,
    avatarUrl         = avatarUrl,
    preferredCurrency = preferredCurrency,
    isVerified        = isVerified
)
