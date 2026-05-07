package com.splitpay.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object GdprConfig : Table("gdpr_config") {
    val key       = varchar("key", 100)
    val value     = varchar("value", 255)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(key)
}
