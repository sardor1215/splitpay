package com.splitpay.data.model

data class Expense(
    val id: String,
    val title: String,
    val amount: Double,
    val currency: String = "€",
    val paidBy: String,
    val paidById: String = "",
    val date: String,
    val yourShare: Double,
    val category: String = "other",
    val splitMode: String = "equally",
    val participants: List<String> = emptyList()
)
