package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.network.CreateExpenseRequest
import com.splitpay.network.ExpenseParticipantRequest
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SplitMode { EQUALLY, EXACT, PERCENT }

data class Participant(
    val id: String,       // userId
    val name: String,
    val isIncluded: Boolean = true,
    val share: Double = 0.0,
    val isOnApp: Boolean = true
)

class AddExpenseViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""
    private val tokenManager = TokenManager(application)
    private val api = RetrofitClient.build(tokenManager)
    private val currentUserId = tokenManager.getUserId() ?: ""

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    // paidByUserId stored internally, displayed as name
    private val _paidByUserId = MutableStateFlow(currentUserId)
    private val _paidBy = MutableStateFlow("You")
    val paidBy: StateFlow<String> = _paidBy

    private val _splitMode = MutableStateFlow(SplitMode.EQUALLY)
    val splitMode: StateFlow<SplitMode> = _splitMode

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadParticipants()
    }

    private fun loadParticipants() {
        val members = AppCache.groupMembers[groupId] ?: emptyList()
        _participants.value = members.map { member ->
            Participant(
                id         = member.userId,
                name       = if (member.userId == currentUserId) "You" else member.name,
                isIncluded = true,
                isOnApp    = true
            )
        }
        // Set paidBy to current user
        val me = _participants.value.find { it.id == currentUserId }
        if (me != null) {
            _paidByUserId.value = me.id
            _paidBy.value = me.name
        }
        recalculateShares()
    }

    fun onAmountChange(value: String) {
        _amount.value = value
        recalculateShares()
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun onPaidByChange(name: String) {
        _paidBy.value = name
        val participant = _participants.value.find { it.name == name }
        if (participant != null) _paidByUserId.value = participant.id
    }

    fun onSplitModeChange(mode: SplitMode) {
        _splitMode.value = mode
        recalculateShares()
    }

    fun onToggleParticipant(participantId: String) {
        _participants.value = _participants.value.map {
            if (it.id == participantId) it.copy(isIncluded = !it.isIncluded) else it
        }
        recalculateShares()
    }

    fun onSelectAll() {
        _participants.value = _participants.value.map { it.copy(isIncluded = true) }
        recalculateShares()
    }

    fun clearError() { _error.value = null }

    private fun recalculateShares() {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        val included = _participants.value.count { it.isIncluded }
        if (included == 0 || total == 0.0) {
            _participants.value = _participants.value.map { it.copy(share = 0.0) }
            return
        }
        when (_splitMode.value) {
            SplitMode.EQUALLY -> {
                val share = total / included
                _participants.value = _participants.value.map {
                    it.copy(share = if (it.isIncluded) share else 0.0)
                }
            }
            else -> { /* TODO: Exact/Percent */ }
        }
    }

    fun saveExpense(onSuccess: () -> Unit) {
        val total = _amount.value.toDoubleOrNull()
        if (total == null || total <= 0) { _error.value = "Enter a valid amount"; return }
        if (_description.value.isBlank()) { _error.value = "Enter a description"; return }
        val included = _participants.value.filter { it.isIncluded }
        if (included.isEmpty()) { _error.value = "Select at least one participant"; return }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = CreateExpenseRequest(
                    title        = _description.value,
                    amount       = total,
                    paidByUserId = _paidByUserId.value,
                    splitMode    = _splitMode.value.name.lowercase(),
                    participants = included.map { ExpenseParticipantRequest(it.id, it.share) }
                )
                val response = api.createExpense(groupId, request)
                if (response.isSuccessful) {
                    // Invalidate expenses cache so GroupDetail refreshes
                    AppCache.expensesByGroup.remove(groupId)
                    onSuccess()
                } else {
                    _error.value = "Failed to save expense (${response.code()})"
                }
            } catch (_: Exception) {
                _error.value = "Cannot reach server."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
