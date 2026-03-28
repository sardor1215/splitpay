package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SplitMode { EQUALLY, EXACT, PERCENT }

data class Participant(
    val id: String,
    val name: String,
    val isIncluded: Boolean = true,
    val share: Double = 0.0
)

class AddExpenseViewModel : ViewModel() {

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _paidBy = MutableStateFlow("You")
    val paidBy: StateFlow<String> = _paidBy

    private val _splitMode = MutableStateFlow(SplitMode.EQUALLY)
    val splitMode: StateFlow<SplitMode> = _splitMode

    private val _participants = MutableStateFlow(
        listOf(
            Participant(id = "1", name = "You", isIncluded = true),
            Participant(id = "2", name = "Sarah Miller", isIncluded = true),
            Participant(id = "3", name = "James Wilson", isIncluded = false),
            Participant(id = "4", name = "Marc Dupont", isIncluded = true),
        )
    )
    val participants: StateFlow<List<Participant>> = _participants

    fun onAmountChange(value: String) {
        _amount.value = value
        recalculateShares()
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun onSplitModeChange(mode: SplitMode) {
        _splitMode.value = mode
        recalculateShares()
    }

    fun onToggleParticipant(participantId: String) {
        _participants.value = _participants.value.map {
            if (it.id == participantId) it.copy(isIncluded = !it.isIncluded)
            else it
        }
        recalculateShares()
    }

    fun onSelectAll() {
        _participants.value = _participants.value.map { it.copy(isIncluded = true) }
        recalculateShares()
    }

    private fun recalculateShares() {
        val total = _amount.value.toDoubleOrNull() ?: 0.0
        val included = _participants.value.count { it.isIncluded }
        if (included == 0 || total == 0.0) return

        when (_splitMode.value) {
            SplitMode.EQUALLY -> {
                val share = total / included
                _participants.value = _participants.value.map {
                    it.copy(share = if (it.isIncluded) share else 0.0)
                }
            }
            else -> { /* Exact / Percent — TODO */ }
        }
    }

    fun saveExpense(onSuccess: () -> Unit) {
        // TODO: connecter à l'API backend plus tard
        if (_amount.value.isNotBlank() && _description.value.isNotBlank()) {
            onSuccess()
        }
    }
}