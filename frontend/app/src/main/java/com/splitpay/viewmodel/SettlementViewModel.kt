package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.network.CreateExpenseRequest
import com.splitpay.data.network.ParticipantRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettlementPayment(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val fromName: String,
    val toName: String,
    val amount: Double,
    val owesYou: Boolean,
    val isYourDebt: Boolean,
    val isConfirmed: Boolean = false
)

class SettlementViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private var currentGroupId = ""
    val currentUserId: String get() = tokenManager.userId ?: ""

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _payments = MutableStateFlow<List<SettlementPayment>>(emptyList())
    val payments: StateFlow<List<SettlementPayment>> = _payments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val totalAmount: Double get() = _payments.value.sumOf { it.amount }
    val pendingCount: Int get() = _payments.value.count { !it.isConfirmed }
    val yourPendingCount: Int get() = _payments.value.count { !it.isConfirmed && it.isYourDebt }

    fun loadSettlement(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val userId = tokenManager.userId

            runCatching { api.getGroup(groupId) }
                .onSuccess { r -> r.body()?.let { _groupName.value = it.name } }

            runCatching { api.getSettlements(groupId) }
                .onSuccess { r ->
                    r.body()?.let { list ->
                        _payments.value = list.mapIndexed { i, s ->
                            SettlementPayment(
                                id         = i.toString(),
                                fromUserId = s.fromUserId,
                                toUserId   = s.toUserId,
                                fromName   = s.fromName,
                                toName     = s.toName,
                                amount     = s.amount,
                                owesYou    = s.toUserId == userId,
                                isYourDebt = s.fromUserId == userId
                            )
                        }
                    }
                }
                .onFailure { _error.value = "Cannot reach server" }

            _isLoading.value = false
        }
    }

    fun confirmPayment(paymentId: String) {
        val payment = _payments.value.find { it.id == paymentId } ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                api.createExpense(
                    currentGroupId,
                    CreateExpenseRequest(
                        title        = "Settlement",
                        amount       = payment.amount,
                        paidBy       = payment.fromUserId,
                        splitMode    = "exact",
                        category     = "settlement",
                        participants = listOf(ParticipantRequest(payment.toUserId, payment.amount))
                    )
                )
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    loadSettlement(currentGroupId)
                } else {
                    _error.value = "Failed to confirm (${r.code()})"
                    _isLoading.value = false
                }
            }.onFailure {
                _error.value = "Cannot reach server"
                _isLoading.value = false
            }
        }
    }

    fun confirmAll() {
        val pending = _payments.value.filter { !it.isConfirmed }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            var failed = false
            for (payment in pending) {
                runCatching {
                    api.createExpense(
                        currentGroupId,
                        CreateExpenseRequest(
                            title        = "Settlement",
                            amount       = payment.amount,
                            paidBy       = payment.fromUserId,
                            splitMode    = "exact",
                            category     = "settlement",
                            participants = listOf(ParticipantRequest(payment.toUserId, payment.amount))
                        )
                    )
                }.onFailure { failed = true }
                 .onSuccess { r -> if (!r.isSuccessful) failed = true }
            }
            if (failed) {
                _error.value = "Some payments could not be confirmed"
                _isLoading.value = false
            } else {
                loadSettlement(currentGroupId)
            }
        }
    }
}
