package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.data.model.Expense
import com.splitpay.data.model.Group
import com.splitpay.network.AddMemberRequest
import com.splitpay.network.PhoneLookupRequest
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import com.splitpay.util.ContactReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Settlement(
    val memberName: String,
    val amount: Double,
    val owesYou: Boolean
)

data class GroupMember(
    val name: String,
    val isOnApp: Boolean = true
)

// Contact from device directory, enriched with SplitPay status
data class ContactWithStatus(
    val name: String,
    val phone: String,
    val userId: String?,       // null = not on SplitPay
    val isAdded: Boolean = false
) {
    val isOnApp get() = userId != null
}

class GroupDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // groupId available immediately from nav args — used to pre-load cache in init
    private val cachedGroupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val tokenManager = TokenManager(application)
    private val api          = RetrofitClient.build(tokenManager)

    // StateFlows initialized from cache so the very first frame has data — no spinner
    private val _isLoading = MutableStateFlow(AppCache.groupDetails[cachedGroupId] == null)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _group = MutableStateFlow<Group?>(AppCache.groupDetails[cachedGroupId])
    val group: StateFlow<Group?> = _group

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _yourBalance = MutableStateFlow(0.0)
    val yourBalance: StateFlow<Double> = _yourBalance

    private val _totalSpending = MutableStateFlow(0.0)
    val totalSpending: StateFlow<Double> = _totalSpending

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(
        AppCache.groupMembers[cachedGroupId] ?: emptyList()
    )
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers

    // ── Contacts / Add Member ─────────────────────────────────────────────
    private val _contacts = MutableStateFlow<List<ContactWithStatus>>(
        AppCache.contactsWithStatus ?: emptyList()  // serve cache immediately on VM creation
    )
    val contacts: StateFlow<List<ContactWithStatus>> = _contacts

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts

    private val _contactsError = MutableStateFlow<String?>(null)
    val contactsError: StateFlow<String?> = _contactsError

    private val _addMemberError = MutableStateFlow<String?>(null)
    val addMemberError: StateFlow<String?> = _addMemberError

    // ─────────────────────────────────────────────────────────────────────

    private var pollingJob: Job? = null

    fun startPolling(groupId: String) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                fetchGroup(groupId)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            // Serve cached data instantly — no spinner if we already have it
            AppCache.groupDetails[groupId]?.let { _group.value = it }
            AppCache.groupMembers[groupId]?.let { _groupMembers.value = it }
            _isLoading.value = _group.value == null
            fetchGroup(groupId)
            _isLoading.value = false
        }
    }

    // Called both by loadGroup() and the polling loop
    private suspend fun fetchGroup(groupId: String) {
        try {
            val groupResp = api.getGroup(groupId)
            if (groupResp.isSuccessful) {
                val g = groupResp.body()!!
                val fresh = Group(
                    id           = g.id,
                    name         = g.name,
                    members      = emptyList(),
                    memberCount  = g.memberCount,
                    balance      = 0.0,
                    lastActivity = "",
                    emoji        = g.description?.takeIf { it.isNotBlank() } ?: "💰"
                )
                AppCache.groupDetails[groupId] = fresh
                _group.value = fresh
            }
            val membersResp = api.getGroupMembers(groupId)
            if (membersResp.isSuccessful) {
                val freshMembers = membersResp.body()?.map { m ->
                    GroupMember(name = m.name, isOnApp = true)
                } ?: emptyList()
                AppCache.groupMembers[groupId] = freshMembers
                _groupMembers.value = freshMembers
            }
        } catch (_: Exception) {
            // Keep showing cached data on network error
        }
    }

    /**
     * 1. Read device contacts (IO thread)
     * 2. POST /users/lookup with all phone numbers
     * 3. Merge results → ContactWithStatus list
     */
    fun loadContacts() {
        viewModelScope.launch {
            _isLoadingContacts.value = true
            _contactsError.value = null
            try {
                val deviceContacts = withContext(Dispatchers.IO) {
                    ContactReader.read(getApplication())
                }
                if (deviceContacts.isEmpty()) {
                    _contacts.value = emptyList()
                    return@launch
                }

                val phones  = deviceContacts.map { it.phone }
                val response = api.lookupUsers(PhoneLookupRequest(phones))

                val lookupMap: Map<String, Pair<String, String>> = if (response.isSuccessful) {
                    response.body()?.associate { it.phone to (it.userId to it.name) } ?: emptyMap()
                } else emptyMap()

                val fresh = deviceContacts.map { contact ->
                    val match = lookupMap[contact.phone]
                    ContactWithStatus(
                        name   = contact.name,
                        phone  = contact.phone,
                        userId = match?.first
                    )
                }
                // Preserve already-added state during refresh
                val alreadyAdded = _contacts.value.filter { it.isAdded }.map { it.userId }.toSet()
                _contacts.value = fresh.map { if (it.userId in alreadyAdded) it.copy(isAdded = true) else it }
                AppCache.contactsWithStatus = fresh
            } catch (e: Exception) {
                _contactsError.value = "Cannot reach server. Check your connection."
            } finally {
                _isLoadingContacts.value = false
            }
        }
    }

    fun clearErrors() {
        _contactsError.value = null
        _addMemberError.value = null
    }

    fun addMember(groupId: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _addMemberError.value = null
            try {
                val response = api.addMemberToGroup(groupId, AddMemberRequest(userId))
                if (response.isSuccessful) {
                    // Mark as added in the local list
                    _contacts.value = _contacts.value.map {
                        if (it.userId == userId) it.copy(isAdded = true) else it
                    }
                    onSuccess()
                } else {
                    _addMemberError.value = when (response.code()) {
                        403  -> "Only admins can add members"
                        409  -> "Already a member"
                        else -> "Failed to add member (${response.code()})"
                    }
                }
            } catch (e: Exception) {
                _addMemberError.value = "Cannot reach server."
            }
        }
    }

    // ── Mock data (until expenses API is ready) ───────────────────────────

    private fun loadBerlinTrip() {
        _group.value = Group(
            id = "1", name = "Trip to Berlin",
            members = listOf("You", "Marc", "Elena", "Alex", "Sarah"),
            balance = 142.50, lastActivity = "Active now", emoji = "✈️"
        )
        _expenses.value = listOf(
            Expense(id = "1", title = "Hotel Adlon Kempinski", amount = 840.0,  paidBy = "You",   date = "Oct 12", yourShare = 672.0),
            Expense(id = "2", title = "Dinner at Borchardt",   amount = 312.50, paidBy = "Marc",  date = "Oct 13", yourShare = -62.50),
            Expense(id = "3", title = "DB Train Tickets",       amount = 125.0,  paidBy = "You",   date = "Oct 14", yourShare = 100.0),
            Expense(id = "4", title = "Museum Island Entry",    amount = 90.0,   paidBy = "Elena", date = "Oct 15", yourShare = -18.0)
        )
        _settlements.value = listOf(
            Settlement("Marc", 245.0, owesYou = true),
            Settlement("Elena", 12.50, owesYou = true)
        )
        _yourBalance.value   = 142.50
        _totalSpending.value = 2480.0
        _groupMembers.value  = listOf(
            GroupMember("You",   isOnApp = true),
            GroupMember("Marc",  isOnApp = true),
            GroupMember("Elena", isOnApp = true),
            GroupMember("Alex",  isOnApp = false),
            GroupMember("Sarah", isOnApp = false),
        )
    }

    private fun loadApartment() {
        _group.value = Group(
            id = "2", name = "Apartment Expenses",
            members = listOf("You", "Roommate"),
            balance = -300.0, lastActivity = "2 days ago", emoji = "🏠"
        )
        _expenses.value = listOf(
            Expense(id = "5", title = "Monthly Rent", amount = 600.0, paidBy = "Roommate", date = "Mar 1", yourShare = -300.0)
        )
        _settlements.value  = emptyList()
        _yourBalance.value  = -300.0
        _totalSpending.value = 600.0
        _groupMembers.value = listOf(
            GroupMember("You",      isOnApp = true),
            GroupMember("Roommate", isOnApp = true),
        )
    }

    private fun loadHike() {
        _group.value = Group(
            id = "3", name = "Weekend Hike",
            members = listOf("You", "Alex", "Sarah", "Tom", "Emma", "Jake"),
            balance = 0.0, lastActivity = "Last week", emoji = "🏕️"
        )
        _expenses.value = listOf(
            Expense(id = "6", title = "Camping Gear Rental", amount = 120.0, paidBy = "Alex", date = "Mar 20", yourShare = 0.0)
        )
        _settlements.value  = emptyList()
        _yourBalance.value  = 0.0
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
