package com.splitpay.data.model

data class Expense(
    val id: String,
    val title: String,
    val amount: Double,
    val currency: String = "€",
    val paidBy: String,
    val date: String,
    val yourShare: Double,      // positif = tu récupères, négatif = tu dois
    val category: String = "General"
)