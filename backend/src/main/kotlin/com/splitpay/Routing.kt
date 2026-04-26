package com.splitpay

import com.splitpay.routes.authRoutes
import com.splitpay.routes.expenseRoutes
import com.splitpay.routes.groupRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {

    // CORS — allow frontend to call backend
    install(CORS) {
        anyHost() // restrict to your domain in production
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    // Global error handler
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("message" to "Internal server error: ${cause.message}")
            )
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized — missing or invalid token"))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "Route not found"))
        }
    }

    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "OK", "version" to "1.0"))
        }

        // Auth + User routes
        authRoutes()

        // Group routes
        groupRoutes()

        // Expense routes (expenses, balances, settlements)
        expenseRoutes()
    }
}
