package com.splitpay.database

import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object SplitPaySqlLogger : SqlLogger {
    private val logger = LoggerFactory.getLogger("com.splitpay.database.SQL")

    override fun log(context: StatementContext, transaction: Transaction) {
        val sql = context.expandArgs(transaction)
        logger.debug("SQL → $sql")
    }
}

/**
 * Use this instead of plain transaction { } to get SQL logging.
 * Usage:
 *   loggedTransaction {
 *       Users.selectAll()
 *   }
 */
fun <T> loggedTransaction(block: org.jetbrains.exposed.sql.Transaction.() -> T): T {
    return transaction {
        addLogger(SplitPaySqlLogger)
        block()
    }
}