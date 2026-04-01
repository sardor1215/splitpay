package com.splitpay.database

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

object DatabaseLogger {

    private val logger = LoggerFactory.getLogger("com.splitpay.database.SQL")

    /**
     * Wrap any DB call with automatic logging.
     * Usage:
     *   val user = DatabaseLogger.log("findByEmail") {
     *       UserRepository.findByEmail(email)
     *   }
     */
    fun <T> log(operation: String, block: () -> T): T {
        logger.info("⟶  START  [$operation]")
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - start
            logger.info("✓  OK     [$operation] (${duration}ms)")
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.error("✗  FAIL   [$operation] (${duration}ms) → ${e.message}")
            throw e
        }
    }
}