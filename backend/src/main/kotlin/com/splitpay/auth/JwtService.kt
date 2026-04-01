package com.splitpay.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.util.Date
import java.util.UUID

object JwtService {
    private val secret   = System.getenv("JWT_SECRET")         ?: "splitpay-dev-secret-change-in-prod"
    private val refresh  = System.getenv("JWT_REFRESH_SECRET") ?: "splitpay-refresh-secret-change-in-prod"
    private val issuer   = "splitpay"
    private val audience = "splitpay-users"
    val realm            = "splitpay"

    private val accessExpiry  = 15 * 60 * 1000L          // 15 minutes
    private val refreshExpiry = 7 * 24 * 60 * 60 * 1000L // 7 days

    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    val refreshVerifier: JWTVerifier = JWT.require(Algorithm.HMAC256(refresh))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateAccessToken(userId: UUID, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + accessExpiry))
            .sign(Algorithm.HMAC256(secret))

    fun generateRefreshToken(userId: UUID): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshExpiry))
            .sign(Algorithm.HMAC256(refresh))

    fun validateRefreshToken(token: String): UUID? = try {
        val decoded = refreshVerifier.verify(token)
        UUID.fromString(decoded.subject)
    } catch (e: Exception) {
        null
    }
}
