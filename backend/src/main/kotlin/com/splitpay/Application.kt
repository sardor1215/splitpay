package com.splitpay

import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    println(">>> Starting application...")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        println(">>> Configuring modules...")
        module()
        println(">>> Modules configured")
    }.start(wait = true)
}

fun Application.module() {
    println(">>> Configuring logging...")
    configureLogging()
    println(">>> Logging configured")
    println(">>> Configuring serialization...")
    configureSerialization()
    println(">>> Serialization done")
    configureAuth()
    println(">>> Configuring routing...")
    configureRouting()
    println(">>> Routing done")
    Database.connect()

}