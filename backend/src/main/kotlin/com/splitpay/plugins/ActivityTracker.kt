package com.splitpay.plugins

import com.splitpay.database.tables.Users
import com.splitpay.repository.UserRepository
import com.splitpay.routes.MessageResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val EXCLUDED_PATHS = setOf("/health", "/auth/refresh", "/auth/logout", "/me", "/admin")
// Write operations that suspended users cannot perform
private val WRITE_METHODS  = setOf("POST", "PATCH", "PUT", "DELETE")

val ActivityTrackerPlugin = createApplicationPlugin("ActivityTracker") {
    on(AuthenticationChecked) { call ->
        val path   = call.request.local.uri
        val method = call.request.local.method.value

        if (EXCLUDED_PATHS.any { path.contains(it) }) return@on

        val principal = call.principal<JWTPrincipal>() ?: return@on
        val userId    = runCatching { UUID.fromString(principal.payload.subject) }.getOrNull() ?: return@on

        // Block suspended users from write operations
        if (method in WRITE_METHODS) {
            val amlStatus = transaction {
                Users.select { Users.id eq userId }.singleOrNull()?.get(Users.amlStatus)
            }
            if (amlStatus == "suspended") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    MessageResponse("Your account has been suspended due to suspicious activity. Please contact support.")
                )
                return@on
            }
        }

        application.launch(Dispatchers.IO) {
            runCatching { UserRepository.updateLastActivity(userId) }
        }
    }
}
