package com.splitpay.service

import com.google.auth.oauth2.GoogleCredentials
import com.splitpay.database.tables.FcmTokens
import com.splitpay.database.tables.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.UUID

object FcmService {

    private val projectId: String = System.getenv("FCM_PROJECT_ID") ?: "splitpay-a6e4a"
    private val credentialsPath: String? = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")

    private fun accessToken(): String? {
        val path = credentialsPath ?: return null
        return try {
            val credentials = GoogleCredentials
                .fromStream(FileInputStream(path))
                .createScoped("https://www.googleapis.com/auth/firebase.messaging")
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        } catch (e: Exception) {
            null
        }
    }

    // ── Token management ───────────────────────────────────────────────────────

    fun upsertToken(userId: UUID, token: String) = transaction {
        FcmTokens.deleteWhere { FcmTokens.token eq token }
        FcmTokens.insert {
            it[FcmTokens.userId]    = userId
            it[FcmTokens.token]     = token
            it[FcmTokens.updatedAt] = OffsetDateTime.now()
        }
    }

    private fun tokensForUsers(userIds: List<UUID>): List<String> {
        if (userIds.isEmpty()) return emptyList()
        return transaction {
            FcmTokens.select { FcmTokens.userId inList userIds }.map { it[FcmTokens.token] }
        }
    }

    // ── Notification sending ───────────────────────────────────────────────────

    fun notifyUser(userId: UUID, title: String, body: String, data: Map<String, String> = emptyMap()) {
        val token = accessToken() ?: return
        tokensForUsers(listOf(userId)).forEach { fcmToken -> send(fcmToken, title, body, token, data) }
    }

    fun notifyAdmins(title: String, body: String, data: Map<String, String> = emptyMap()) {
        val token = accessToken() ?: return
        val adminIds = transaction {
            Users.select { (Users.isAdmin eq true) and (Users.isDeleted eq false) }
                .map { it[Users.id] }
        }
        tokensForUsers(adminIds).forEach { fcmToken -> send(fcmToken, title, body, token, data) }
    }

    fun notifyGroupMembers(
        groupId: UUID,
        excludeUserId: UUID?,
        memberIds: List<UUID>,
        title: String,
        body: String
    ) {
        val token = accessToken() ?: return
        val targets = if (excludeUserId != null) memberIds.filter { it != excludeUserId } else memberIds
        val fcmTokens = tokensForUsers(targets)
        fcmTokens.forEach { fcmToken -> send(fcmToken, title, body, token, mapOf("groupId" to groupId.toString())) }
    }

    private fun send(fcmToken: String, title: String, body: String, bearerToken: String, data: Map<String, String> = emptyMap()) {
        try {
            val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $bearerToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
                connectTimeout = 5_000
                readTimeout    = 5_000
            }

            val dataJson = data.entries.joinToString(",") { (k, v) -> """"$k":"$v"""" }
            val payload = """{"message":{"token":"$fcmToken","notification":{"title":"${title.esc}","body":"${body.esc}"},"data":{$dataJson}}}"""

            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // Non-fatal — never crash the API because FCM failed
        }
    }

    private val String.esc get() = replace("\\", "\\\\").replace("\"", "\\\"")
}
