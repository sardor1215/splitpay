package com.splitpay

import com.splitpay.routes.authRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("OK")
        }
        authRoutes() // Ici tu pourras ajouter d'autres routes
    }
}