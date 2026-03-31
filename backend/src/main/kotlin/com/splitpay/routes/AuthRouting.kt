package com.splitpay.routes

import com.splitpay.auth.EmailService
import com.splitpay.auth.JwtService
import com.splitpay.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val name: String,
    val email: String
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val avatarUrl: String?,
    val isVerified: Boolean
)


fun Route.authRoutes() {
    route("/auth") {

        // POST /auth/register
        post("/register") {

            val body = call.receive<RegisterRequest>()

            if (body.name.isBlank() || body.email.isBlank() || body.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Name, email and password are required")
                return@post
            }

            if (body.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, "Password must be at least 8 characters")
                return@post
            }

            if (UserRepository.findByEmail(body.email) != null) {
                call.respond(HttpStatusCode.Conflict, "Email already in use")
                return@post
            }

            val user = UserRepository.create(body.name, body.email, body.password, body.phone)
            //EmailService.sendVerificationEmail(body.email, user.verificationToken!!)//

            call.respond(HttpStatusCode.Created, mapOf(
                "message" to "Welcome ${user.name}! Please check your email to verify your account."
            ))
        }

        // POST /auth/login
        post("/login") {
            val body = call.receive<LoginRequest>()
            val user = UserRepository.findByEmail(body.email)

            if (user == null || !UserRepository.verifyPassword(user, body.password)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            if (!user.isVerified) {
                call.respond(HttpStatusCode.Forbidden, "Please verify your email first")
                return@post
            }

            val token = JwtService.generateToken(user.id, user.email)
            call.respond(AuthResponse(token, user.id.toString(), user.name, user.email))
        }

        // GET /auth/verify?token=...
        get("/verify") {
            val token = call.request.queryParameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing token")

            val success = UserRepository.verifyEmail(token)
            if (success) {
                call.respond(HttpStatusCode.OK, "Email verified! You can now log in.")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Invalid or expired token")
            }
        }

        // POST /auth/logout
        post("/logout") {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
        }
    }

    // GET /me — protected
    authenticate("auth-jwt") {
        get("/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = UUID.fromString(principal!!.payload.subject)
            val user = UserRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")

            call.respond(UserProfileResponse(
                id        = user.id.toString(),
                name      = user.name,
                email     = user.email,
                phone     = user.phone,
                avatarUrl = user.avatarUrl,
                isVerified = user.isVerified
            ))
        }
    }
}