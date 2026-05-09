package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.model.Expense
import com.splitpay.data.network.ParticipantRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.network.UpdateExpenseRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditExpenseViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _amount       = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    private val _description  = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _paidByUserId = MutableStateFlow("")
    val paidByUserId: StateFlow<String> = _paidByUserId

    private val _paidByName   = MutableStateFlow("")
    val paidBy: StateFlow<String> = _paidByName

    private val _splitMode    = MutableStateFlow(SplitMode.EQUALLY)
    val splitMode: StateFlow<SplitMode> = _splitMode

    private val _category     = MutableStateFlow("other")
    val category: StateFlow<String> = _category

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants

    private val _isLoading    = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error        = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(groupId: String, expenseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Load expense detail from cache or API
            val expense = AppCache.expenseDetails[expenseId]
                ?: runCatching { api.getExpenseDetail(groupId, expenseId) }.getOrNull()
                    ?.takeIf { it.isSuccessful }?.body()?.expense
                    ?.also { AppCache.expenseDetails[expenseId] = it }

            if (expense == null) { _error.value = "Failed to load expense"; _isLoading.value = false; return@launch }

            _description.value  = expense.title
            _amount.value       = expense.amount.toString()
            _paidByUserId.value = expense.paidBy
            _paidByName.value   = expense.paidByName
            _category.value     = expense.category ?: "other"
            _splitMode.value    = when (expense.splitMode) {
                "exact"      -> SplitMode.EXACT
                "percentage" -> SplitMode.PERCENT
                else         -> SplitMode.EQUALLY
            }

            // Load members from cache or API
            val members = AppCache.groupMembers[groupId]
                ?: runCatching { api.getMembers(groupId) }.getOrNull()
                    ?.takeIf { it.isSuccessful }?.body()
                    ?.map { com.splitpay.data.model.Member(it.userId, it.name, it.role) }
                    ?.also { AppCache.groupMembers[groupId] = it }

            if (members != null) {
                _participants.value = members.map { member ->
                    val ep = expense.participants.find { it.userId == member.userId }
                    Participant(id = member.userId, name = member.name, isIncluded = ep != null, share = ep?.share ?: 0.0)
                }
            }

            _isLoading.value = false
        }
    }

    fun onAmountChange(v: String)      { _amount.value = v; if (_splitMode.value == SplitMode.EQUALLY) recalculate() }
    fun onDescriptionChange(v: String) { _description.value = v }
    fun onCategoryChange(cat: String)  { _category.value = cat }

    fun onPaidByChange(userId: String, name: String) {
        _paidByUserId.value = userId
        _paidByName.value   = name
    }

    fun onSplitModeChange(mode: SplitMode) {
        _splitMode.value = mode
        if (mode == SplitMode.EQUALLY) recalculate()
        else _participants.value = _participants.value.map { it.copy(share = 0.0) }
    }

    fun onToggleParticipant(id: String) {
        _participants.value = _participants.value.map { if (it.id == id) it.copy(isIncluded = !it.isIncluded) else it }
        if (_splitMode.value == SplitMode.EQUALLY) recalculate()
    }

    fun onSelectAll() {
        _participants.value = _participants.value.map { it.copy(isIncluded = true) }
        if (_splitMode.value == SplitMode.EQUALLY) recalculate()
    }

    fun onExactShareChange(id: String, value: String) {
        _participants.value = _participants.value.map {
            if (it.id == id) it.copy(share = value.toDoubleOrNull() ?: 0.0) else it
        }
    }

    fun onPercentChange(id: String, value: String) {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        _participants.value = _participants.value.map {
            if (it.id == id) it.copy(share = total * (value.toDoubleOrNull() ?: 0.0) / 100.0) else it
        }
    }

    private fun recalculate() {
        val total    = _amount.value.toDoubleOrNull() ?: 0.0
        val included = _participants.value.count { it.isIncluded }
        if (included == 0 || total == 0.0) return
        val share = total / included
        _participants.value = _participants.value.map { it.copy(share = if (it.isIncluded) share else 0.0) }
    }

    fun saveExpense(groupId: String, expenseId: String, onSuccess: () -> Unit) {
        val total = _amount.value.toDoubleOrNull()
        if (total == null || total <= 0) { _error.value = "Invalid amount"; return }
        if (_description.value.isBlank())  { _error.value = "Title is required"; return }
        val included = _participants.value.filter { it.isIncluded }
        if (included.isEmpty()) { _error.value = "Select at least one participant"; return }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                api.updateExpense(
                    groupId, expenseId,
                    UpdateExpenseRequest(
                        title        = _description.value.trim(),
                        amount       = total,
                        paidBy       = _paidByUserId.value,
                        splitMode    = when (_splitMode.value) {
                            SplitMode.EQUALLY -> "equally"
                            SplitMode.EXACT   -> "exact"
                            SplitMode.PERCENT -> "percentage"
                        },
                        category     = _category.value,
                        participants = included.map { ParticipantRequest(it.id, it.share) }
                    )
                )
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    val updated = r.body()!!
                    val userId  = tokenManager.userId
                    val myShare = updated.participants.find { it.userId == userId }
                        ?.let { p -> if (updated.paidBy == userId) updated.amount - p.share else -p.share } ?: 0.0
                    val updatedExpense = Expense(
                        id           = updated.id,
                        title        = updated.title,
                        amount       = updated.amount,
                        paidBy       = updated.paidByName,
                        paidById     = updated.paidBy,
                        date         = updated.createdAt.take(10),
                        yourShare    = myShare,
                        category     = updated.category ?: "other",
                        splitMode    = updated.splitMode ?: "equally",
                        participants = updated.participants.map { it.userId }
                    )
                    AppCache.expensesByGroup[groupId] = AppCache.expensesByGroup[groupId]
                        ?.map { if (it.id == expenseId) updatedExpense else it }
                        ?: listOf(updatedExpense)
                    AppCache.expenseDetails[expenseId] = updated
                    onSuccess()
                } else {
                    val msg = runCatching {
                        org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
                    }.getOrNull()
                    _error.value = msg ?: "Error ${r.code()}"
                }
            }.onFailure { _error.value = "Cannot reach the server" }
            _isLoading.value = false
        }
    }
}
