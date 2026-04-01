package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import com.splitpay.data.model.Expense
import com.splitpay.data.model.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settlement(
    val memberName: String,
    val amount: Double,
    val owesYou: Boolean
)

data class GroupMember(
    val name: String,
    val isOnApp: Boolean = true
)

class GroupDetailViewModel : ViewModel() {

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _yourBalance = MutableStateFlow(0.0)
    val yourBalance: StateFlow<Double> = _yourBalance

    private val _totalSpending = MutableStateFlow(0.0)
    val totalSpending: StateFlow<Double> = _totalSpending

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers

    fun loadGroup(groupId: String) {
        // TODO: remplacer par appel API plus tard
        when (groupId) {
            "1" -> loadBerlinTrip()
            "2" -> loadApartment()
            "3" -> loadHike()
        }
    }

    private fun loadBerlinTrip() {
        _group.value = Group(
            id = "1",
            name = "Trip to Berlin",
            members = listOf("You", "Marc", "Elena", "Alex", "Sarah"),
            balance = 142.50,
            lastActivity = "Active now",
            emoji = "✈️"
        )
        _expenses.value = listOf(
            Expense(
                id = "1",
                title = "Hotel Adlon Kempinski",
                amount = 840.0,
                paidBy = "You",
                date = "Oct 12",
                yourShare = 672.0
            ),
            Expense(
                id = "2",
                title = "Dinner at Borchardt",
                amount = 312.50,
                paidBy = "Marc",
                date = "Oct 13",
                yourShare = -62.50
            ),
            Expense(
                id = "3",
                title = "DB Train Tickets",
                amount = 125.0,
                paidBy = "You",
                date = "Oct 14",
                yourShare = 100.0
            ),
            Expense(
                id = "4",
                title = "Museum Island Entry",
                amount = 90.0,
                paidBy = "Elena",
                date = "Oct 15",
                yourShare = -18.0
            )
        )
        _settlements.value = listOf(
            Settlement("Marc", 245.0, owesYou = true),
            Settlement("Elena", 12.50, owesYou = true)
        )
        _yourBalance.value = 142.50
        _totalSpending.value = 2480.0
        _groupMembers.value = listOf(
            GroupMember("You",   isOnApp = true),
            GroupMember("Marc",  isOnApp = true),
            GroupMember("Elena", isOnApp = true),
            GroupMember("Alex",  isOnApp = false),
            GroupMember("Sarah", isOnApp = false),
        )
    }

    private fun loadApartment() {
        _group.value = Group(
            id = "2",
            name = "Apartment Expenses",
            members = listOf("You", "Roommate"),
            balance = -300.0,
            lastActivity = "2 days ago",
            emoji = "🏠"
        )
        _expenses.value = listOf(
            Expense(
                id = "5",
                title = "Monthly Rent",
                amount = 600.0,
                paidBy = "Roommate",
                date = "Mar 1",
                yourShare = -300.0
            )
        )
        _settlements.value = emptyList()
        _yourBalance.value = -300.0
        _totalSpending.value = 600.0
        _groupMembers.value = listOf(
            GroupMember("You",      isOnApp = true),
            GroupMember("Roommate", isOnApp = true),
        )
    }

    private fun loadHike() {
        _group.value = Group(
            id = "3",
            name = "Weekend Hike",
            members = listOf("You", "Alex", "Sarah", "Tom", "Emma", "Jake"),
            balance = 0.0,
            lastActivity = "Last week",
            emoji = "🏕️"
        )
        _expenses.value = listOf(
            Expense(
                id = "6",
                title = "Camping Gear Rental",
                amount = 120.0,
                paidBy = "Alex",
                date = "Mar 20",
                yourShare = 0.0
            )
        )
        _settlements.value = emptyList()
        _yourBalance.value = 0.0
        _totalSpending.value = 120.0
        _groupMembers.value = listOf(
            GroupMember("You",  isOnApp = true),
            GroupMember("Alex", isOnApp = true),
            GroupMember("Sarah",isOnApp = false),
            GroupMember("Tom",  isOnApp = true),
            GroupMember("Emma", isOnApp = false),
            GroupMember("Jake", isOnApp = true),
        )
    }
}