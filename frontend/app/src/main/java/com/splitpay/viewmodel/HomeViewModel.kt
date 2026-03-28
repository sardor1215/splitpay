package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import com.splitpay.data.model.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _totalOwed = MutableStateFlow(0.0)
    val totalOwed: StateFlow<Double> = _totalOwed

    private val _totalOwe = MutableStateFlow(0.0)
    val totalOwe: StateFlow<Double> = _totalOwe

    init {
        loadMockData()
    }

    // ── Mock data (remplacé par API plus tard) ────────────────────────────
    private fun loadMockData() {
        val mockGroups = listOf(
            Group(
                id = "1",
                name = "Trip to Berlin",
                members = listOf("Alex", "Sarah", "Tom", "You"),
                balance = 120.0,
                lastActivity = "Active now",
                emoji = "✈️"
            ),
            Group(
                id = "2",
                name = "Apartment Expenses",
                members = listOf("Roommate", "You"),
                balance = -300.0,
                lastActivity = "2 days ago",
                emoji = "🏠"
            ),
            Group(
                id = "3",
                name = "Weekend Hike",
                members = listOf("Alex", "Sarah", "Tom", "Emma", "Jake", "You"),
                balance = 0.0,
                lastActivity = "Last week",
                emoji = "🏕️"
            )
        )

        _groups.value = mockGroups
        _totalOwed.value = mockGroups.filter { it.balance > 0 }.sumOf { it.balance }
        _totalOwe.value = mockGroups.filter { it.balance < 0 }.sumOf { -it.balance }
    }
}