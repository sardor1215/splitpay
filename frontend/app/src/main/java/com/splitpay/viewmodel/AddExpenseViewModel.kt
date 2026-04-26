package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.CreateExpenseRequest
import com.splitpay.data.network.ParticipantRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SplitMode { EQUALLY, EXACT, PERCENT }

data class Participant(
    val id: String,
    val name: String,
    val isIncluded: Boolean = true,
    val share: Double = 0.0
)

class AddExpenseViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _amount      = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _paidByUserId = MutableStateFlow(tokenManager.userId ?: "")
    val paidByUserId: StateFlow<String> = _paidByUserId

    private val _paidByName  = MutableStateFlow(tokenManager.userName ?: "Vous")
    val paidBy: StateFlow<String> = _paidByName

    private val _splitMode   = MutableStateFlow(SplitMode.EQUALLY)
    val splitMode: StateFlow<SplitMode> = _splitMode

    private val _category    = MutableStateFlow("other")
    val category: StateFlow<String> = _category

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error       = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadParticipants(groupId: String) {
        val cached = AppCache.groupMembers[groupId]
        if (cached != null) {
            _participants.value = cached.map { Participant(it.userId, it.name, isIncluded = true) }
            recalculateShares()
        } else {
            viewModelScope.launch {
                runCatching { api.getMembers(groupId) }.onSuccess { r ->
                    if (r.isSuccessful) {
                        val members = r.body()!!
                        _participants.value = members.map {
                            Participant(it.userId, it.name, isIncluded = true)
                        }
                        recalculateShares()
                    }
                }
            }
        }
    }

    fun onAmountChange(value: String)      { _amount.value = value; recalculateShares() }
    fun onDescriptionChange(value: String) { _description.value = value }

    fun onPaidByChange(userId: String, name: String) {
        _paidByUserId.value = userId
        _paidByName.value   = name
    }

    fun onSplitModeChange(mode: SplitMode) { _splitMode.value = mode; recalculateShares() }
    fun onCategoryChange(cat: String) { _category.value = cat }

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

    private fun recalculateShares() {
        val total    = _amount.value.toDoubleOrNull() ?: 0.0
        val included = _participants.value.count { it.isIncluded }
        if (included == 0 || total == 0.0) return

        if (_splitMode.value == SplitMode.EQUALLY) {
            val share = total / included
            _participants.value = _participants.value.map {
                it.copy(share = if (it.isIncluded) share else 0.0)
            }
        }
    }

    fun saveExpense(groupId: String, onSuccess: () -> Unit) {
        val total = _amount.value.toDoubleOrNull()
        if (total == null || total <= 0) { _error.value = "Invalid amount"; return }
        if (_description.value.isBlank()) { _error.value = "Title is required"; return }

        val included = _participants.value.filter { it.isIncluded }
        if (included.isEmpty()) { _error.value = "Select at least one participant"; return }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                api.createExpense(
                    groupId,
                    CreateExpenseRequest(
                        title        = _description.value.trim(),
                        amount       = total,
                        paidBy       = _paidByUserId.value,
                        splitMode    = _splitMode.value.name.lowercase(),
                        category     = _category.value,
                        participants = included.map { ParticipantRequest(it.id, it.share) }
                    )
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    AppCache.expensesByGroup.remove(groupId)
                    onSuccess()
                } else {
                    _error.value = "Error ${response.code()}"
                }
            }.onFailure {
                _error.value = "Cannot reach the server"
            }
            _isLoading.value = false
        }
    }
}
