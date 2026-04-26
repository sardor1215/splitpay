package com.splitpay.data.model

data class Group(
    val id: String,
    val name: String,
    val members: List<String>,
    val balance: Double,        // positif = on te doit, négatif = tu dois
    val lastActivity: String,
    val emoji: String = "💰",
    val isArchived: Boolean = false,
    val inviteToken: String? = null
)