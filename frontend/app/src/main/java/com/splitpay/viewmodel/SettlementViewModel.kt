package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettlementPayment(
    val id: String,
    val memberName: String,
    val amount: Double,
    val isConfirmed: Boolean = false
)

class SettlementViewModel : ViewModel() {

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _payments = MutableStateFlow<List<SettlementPayment>>(emptyList())
    val payments: StateFlow<List<SettlementPayment>> = _payments

    val totalAmount: Double get() = _payments.value.sumOf { it.amount }
    val remainingAmount: Double get() = _payments.value.filter { !it.isConfirmed }.sumOf { it.amount }
    val pendingCount: Int get() = _payments.value.count { !it.isConfirmed }

    fun loadSettlement(groupId: String) {
        when (groupId) {
            "1" -> {
                _groupName.value = "Trip to Berlin"
                _payments.value = listOf(
                    SettlementPayment(id = "1", memberName = "Marc", amount = 45.00),
                    SettlementPayment(id = "2", memberName = "Elena", amount = 12.50),
                )
            }
            "2" -> {
                _groupName.value = "Apartment Expenses"
                _payments.value = listOf(
                    SettlementPayment(id = "3", memberName = "Roommate", amount = 300.00),
                )
            }
            else -> {
                _groupName.value = "Group"
                _payments.value = emptyList()
            }
        }
    }

    fun confirmPayment(paymentId: String) {
        _payments.value = _payments.value.map {
            if (it.id == paymentId) it.copy(isConfirmed = true) else it
        }
    }

    fun confirmAll() {
        _payments.value = _payments.value.map { it.copy(isConfirmed = true) }
    }
}
