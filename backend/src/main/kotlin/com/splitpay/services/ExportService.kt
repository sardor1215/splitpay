package com.splitpay.services

import com.splitpay.repository.UserRepository
import java.util.UUID

object ExportService {

    fun exportUserDataJson(userId: UUID): String {
        val user = UserRepository.findById(userId)
            ?: throw IllegalArgumentException("User not found")

        return """
        {
            "exportedAt": "${java.time.OffsetDateTime.now()}",
            "version": "1.0",
            "profile": {
                "id":                "${user.id}",
                "name":              "${user.name}",
                "email":             "${user.email}",
                "phone":             ${if (user.phone != null) "\"${user.phone}\"" else "null"},
                "avatarUrl":         ${if (user.avatarUrl != null) "\"${user.avatarUrl}\"" else "null"},
                "preferredCurrency": "${user.preferredCurrency}",
                "isVerified":        ${user.isVerified},
                "createdAt":         "${user.createdAt}"
            },
            "note": "This export contains all personal data stored for your account."
        }
        """.trimIndent()
    }

    fun exportUserDataPdf(userId: UUID): ByteArray {
        val user = UserRepository.findById(userId)
            ?: throw IllegalArgumentException("User not found")

        val content = """
            SplitPay — User Data Export
            ===========================
            Exported: ${java.time.OffsetDateTime.now()}

            PROFILE
            -------
            ID:                 ${user.id}
            Name:               ${user.name}
            Email:              ${user.email}
            Phone:              ${user.phone ?: "N/A"}
            Preferred Currency: ${user.preferredCurrency}
            Verified:           ${user.isVerified}
            Account Created:    ${user.createdAt}

            This document contains all personal data stored for your SplitPay account.
        """.trimIndent()

        return content.toByteArray(Charsets.UTF_8)
    }
}