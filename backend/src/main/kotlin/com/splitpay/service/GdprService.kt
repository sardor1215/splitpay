package com.splitpay.service

import com.splitpay.database.tables.GdprConfig
import com.splitpay.database.tables.Users
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

object GdprService {

    // Defaults — overridden by values stored in gdpr_config table
    private var archiveAfterMonths: Long = 12
    private var deleteAfterMonths: Long  = 24

    private var schedulerJob: Job? = null

    fun start(scope: CoroutineScope) {
        loadConfig()
        schedulerJob = scope.launch {
            while (isActive) {
                runCatching { runDailyCleanup() }
                    .onFailure { println("GDPR cleanup error: ${it.message}") }
                delay(24 * 60 * 60 * 1000L) // 24 hours
            }
        }
        println(">>> GDPR scheduler started (archive=${archiveAfterMonths}m, delete=${deleteAfterMonths}m)")
    }

    fun stop() {
        schedulerJob?.cancel()
    }

    private fun loadConfig() {
        runCatching {
            transaction {
                GdprConfig.selectAll().forEach { row ->
                    when (row[GdprConfig.key]) {
                        "archive_after_months"       -> archiveAfterMonths = row[GdprConfig.value].toLongOrNull() ?: 12
                        "delete_after_months"        -> deleteAfterMonths  = row[GdprConfig.value].toLongOrNull() ?: 24
                        "auto_suspend_after_alerts"  -> AmlService.autoSuspendAfterAlerts = row[GdprConfig.value].toIntOrNull() ?: 3
                        "large_transaction_threshold"-> AmlService.largeTransactionThreshold = row[GdprConfig.value].toBigDecimalOrNull() ?: java.math.BigDecimal("1000.00")
                        "high_frequency_count"       -> AmlService.highFrequencyCount = row[GdprConfig.value].toIntOrNull() ?: 10
                        "high_frequency_window_hours"-> AmlService.highFrequencyWindowHours = row[GdprConfig.value].toIntOrNull() ?: 24
                        "new_account_days"           -> AmlService.newAccountDays = row[GdprConfig.value].toIntOrNull() ?: 30
                    }
                }
            }
        }
    }

    fun reloadConfig() = loadConfig()

    fun runDailyCleanup() {
        loadConfig()
        val now = OffsetDateTime.now()

        transaction {
            // Auto-delete: accounts soft-deleted more than deleteAfterMonths ago
            val deleteCutoff = now.minusMonths(deleteAfterMonths)
            val toDelete = Users.select {
                (Users.isDeleted eq true) and
                (Users.deletedAt lessEq deleteCutoff)
            }.map { it[Users.id] }

            toDelete.forEach { userId ->
                Users.update({ Users.id eq userId }) {
                    it[Users.name]         = "Purged"
                    it[Users.email]        = "purged_${userId}@splitpay.invalid"
                    it[Users.phone]        = null
                    it[Users.avatarUrl]    = null
                    it[Users.passwordHash] = null
                    it[Users.refreshToken] = null
                    it[Users.googleId]     = null
                }
            }
            if (toDelete.isNotEmpty()) println("GDPR: purged ${toDelete.size} accounts")

            // Auto-archive: inactive accounts (no activity for archiveAfterMonths)
            val archiveCutoff = now.minusMonths(archiveAfterMonths)
            val toArchive = Users.select {
                (Users.isDeleted eq false) and
                (Users.lastActivityAt.isNotNull()) and
                (Users.lastActivityAt lessEq archiveCutoff)
            }.map { it[Users.id] }

            toArchive.forEach { userId ->
                Users.update({ Users.id eq userId }) {
                    it[Users.isDeleted] = true
                    it[Users.deletedAt] = now
                    it[Users.name]      = "Archived"
                    it[Users.email]     = "archived_${userId}@splitpay.invalid"
                    it[Users.phone]     = null
                    it[Users.avatarUrl] = null
                    it[Users.passwordHash] = null
                    it[Users.refreshToken] = null
                    it[Users.googleId]  = null
                }
            }
            if (toArchive.isNotEmpty()) println("GDPR: archived ${toArchive.size} inactive accounts")
        }
    }

    fun getConfig(): Map<String, String> = transaction {
        GdprConfig.selectAll().associate { it[GdprConfig.key] to it[GdprConfig.value] }
    }

    fun setConfig(key: String, value: String) {
        transaction {
            GdprConfig.upsert(GdprConfig.key) {
                it[GdprConfig.key]       = key
                it[GdprConfig.value]     = value
                it[GdprConfig.updatedAt] = OffsetDateTime.now()
            }
        }
        loadConfig()
    }
}
