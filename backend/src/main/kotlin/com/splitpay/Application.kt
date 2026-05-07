package com.splitpay

import com.splitpay.service.GdprService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

fun main() {
    println(">>> Starting SplitPay backend...")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    println(">>> Initializing application modules...")
    println(">>> configure Logging...")
    configureLogging()       // HTTP request logging
    println(">>> logging configured")
    println(">>> configure Serialization...")
    configureSerialization() // JSON serialization
    println(">>> serialization configured")
    println(">>> configure Authentication...")
    configureAuth()          // JWT authentication
    println(">>> authentication configured")
    println(">>> configure Routing...")
    configureRouting()       // All routes + CORS + error handling
    println(">>> routing configured")
    Database.connect()
    println(">>> module configured")

    // Start background services after DB is connected
    val appScope = CoroutineScope(SupervisorJob())
    GdprService.start(appScope)

    // Stop background services gracefully on shutdown
    monitor.subscribe(ApplicationStopped) {
        GdprService.stop()
    }
}
