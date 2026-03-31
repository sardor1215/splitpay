package com.splitpay.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

object JwtService {
    private val secret = System.getenv("JWT_SECRET") ?: "your-secret-key"
    private val issuer = "splitpay"
    private val audience = "splitpay-users"
    private val expirationMs = 24 * 60 * 60 * 1000L // 24 hours
    val realm = "splitpay"

    val verifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: UUID, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(Algorithm.HMAC256(secret))
}