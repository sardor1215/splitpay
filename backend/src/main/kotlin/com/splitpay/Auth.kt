package com.splitpay

import com.splitpay.auth.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtService.realm
            verifier(JwtService.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload)
                else null
            }
        }
    }
}