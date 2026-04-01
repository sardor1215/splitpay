package com.splitpay

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val httpLogger = LoggerFactory.getLogger("splitpay.http")

fun Application.configureLogging() {
    install(CallLogging) {
        level  = Level.INFO
        logger = httpLogger        // 👈 this was missing

        format { call ->
            val method   = call.request.httpMethod.value.padEnd(6)
            val path     = call.request.path().padEnd(30)
            val status   = call.response.status()
            val duration = call.processingTimeMillis()

            val ip = call.request.headers["X-Forwarded-For"]
                ?: call.request.headers["X-Real-IP"]
                ?: call.request.local.remoteAddress

            val userId = try {
                call.principal<JWTPrincipal>()?.payload?.subject ?: "anonymous"
            } catch (e: Exception) {
                "anonymous"
            }

            val statusIcon = when (status?.value) {
                in 200..299 -> "✓"
                in 300..399 -> "→"
                in 400..499 -> "✗"
                in 500..599 -> "!!"
                else        -> "?"
            }

            "$statusIcon  [$method] $path | $status | ${duration}ms | ip=$ip | user=$userId"
        }
    }
}