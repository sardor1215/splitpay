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
import com.splitpay.network.UpdateGroupRequest
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
    val userId: String = "",
    val name: String,
    val role: String = "member",
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

    private val _groupError = MutableStateFlow<String?>(null)
    val groupError: StateFlow<String?> = _groupError

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
                    emoji        = g.description?.takeIf { it.isNotBlank() } ?: "💰",
                    isArchived   = g.isArchived
                )
                AppCache.groupDetails[groupId] = fresh
                _group.value = fresh
            }
            val membersResp = api.getGroupMembers(groupId)
            if (membersResp.isSuccessful) {
                val freshMembers = membersResp.body()?.map { m ->
                    GroupMember(userId = m.userId, name = m.name, role = m.role, isOnApp = true)
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
        _groupError.value = null
    }

    fun updateGroup(groupId: String, name: String, emoji: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Optimistic update — apply immediately, revert on failure
            val previous = _group.value
            val optimistic = previous?.copy(name = name, emoji = emoji)
            if (optimistic != null) {
                _group.value = optimistic
                AppCache.groupDetails[groupId] = optimistic
                AppCache.groups = AppCache.groups?.map { if (it.id == groupId) optimistic else it }
            }
            try {
                val response = api.updateGroup(groupId, UpdateGroupRequest(name = name, description = emoji))
                if (response.isSuccessful) {
                    val fresh = response.body()!!
                    val confirmed = _group.value?.copy(
                        name  = fresh.name,
                        emoji = fresh.description?.takeIf { it.isNotBlank() } ?: "💰"
                    )
                    if (confirmed != null) {
                        _group.value = confirmed
                        AppCache.groupDetails[groupId] = confirmed
                        AppCache.groups = AppCache.groups?.map { if (it.id == groupId) confirmed else it }
                    }
                    onSuccess()
                } else {
                    // Revert on error
                    _group.value = previous
                    if (previous != null) {
                        AppCache.groupDetails[groupId] = previous
                        AppCache.groups = AppCache.groups?.map { if (it.id == groupId) previous else it }
                    }
                    _groupError.value = "Failed to update group (${response.code()})"
                }
            } catch (_: Exception) {
                // Revert on network error
                _group.value = previous
                if (previous != null) {
                    AppCache.groupDetails[groupId] = previous
                    AppCache.groups = AppCache.groups?.map { if (it.id == groupId) previous else it }
                }
                _groupError.value = "Cannot reach server."
            }
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit) {
        // Optimistic: remove from cache immediately and navigate back
        AppCache.invalidateGroup(groupId)
        AppCache.groups = AppCache.groups?.filter { it.id != groupId }
        onSuccess()
        viewModelScope.launch {
            try {
                val response = api.deleteGroup(groupId)
                if (!response.isSuccessful) {
                    _groupError.value = "Failed to delete group (${response.code()})"
                }
            } catch (_: Exception) {
                // Already navigated away — silently ignore
            }
        }
    }

    fun unarchiveGroup(groupId: String, onSuccess: () -> Unit) {
        // Optimistic: move back to active list
        val updated = _group.value?.copy(isArchived = false)
        if (updated != null) {
            _group.value = updated
            AppCache.groupDetails[groupId] = updated
            AppCache.archivedGroups = AppCache.archivedGroups?.filter { it.id != groupId }
            AppCache.groups = listOf(updated) + (AppCache.groups ?: emptyList())
        }
        onSuccess()
        viewModelScope.launch {
            try {
                api.unarchiveGroup(groupId)
            } catch (_: Exception) { }
        }
    }

    fun archiveGroup(groupId: String, onSuccess: () -> Unit) {
        // Optimistic: remove from cache immediately and navigate back
        AppCache.invalidateGroup(groupId)
        AppCache.groups = AppCache.groups?.filter { it.id != groupId }
        onSuccess()
        viewModelScope.launch {
            try {
                val response = api.archiveGroup(groupId)
                if (!response.isSuccessful) {
                    _groupError.value = "Failed to archive group (${response.code()})"
                }
            } catch (_: Exception) {
                // Already navigated away — silently ignore
            }
        }
    }

    fun removeMember(groupId: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Optimistic update — remove immediately, restore on failure
            val previousMembers = _groupMembers.value
            _groupMembers.value = previousMembers.filter { it.userId != userId }
            AppCache.groupMembers[groupId] = _groupMembers.value
            try {
                val response = api.removeMember(groupId, userId)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    // Revert
                    _groupMembers.value = previousMembers
                    AppCache.groupMembers[groupId] = previousMembers
                    _groupError.value = when (response.code()) {
                        403 -> "Only admins can remove members"
                        else -> "Failed to remove member (${response.code()})"
                    }
                }
            } catch (_: Exception) {
                // Revert
                _groupMembers.value = previousMembers
                AppCache.groupMembers[groupId] = previousMembers
                _groupError.value = "Cannot reach server."
            }
        }
    }

    fun addMembers(groupId: String, userIds: List<String>, onSuccess: () -> Unit) {
        if (userIds.isEmpty()) return
        _addMemberError.value = null

        // Optimistic: add placeholders to members list immediately
        val optimisticNew = userIds.mapNotNull { uid ->
            _contacts.value.find { it.userId == uid }
                ?.let { GroupMember(userId = uid, name = it.name, role = "member", isOnApp = true) }
        }
        val previous = _groupMembers.value
        _groupMembers.value = previous + optimisticNew
        AppCache.groupMembers[groupId] = _groupMembers.value

        onSuccess()

        viewModelScope.launch {
            userIds.forEach { uid ->
                try {
                    val response = api.addMemberToGroup(groupId, AddMemberRequest(uid))
                    if (!response.isSuccessful && response.code() != 409) {
                        _addMemberError.value = when (response.code()) {
                            403  -> "Only admins can add members"
                            else -> "Failed to add member (${response.code()})"
                        }
                    }
                } catch (_: Exception) {
                    _addMemberError.value = "Cannot reach server."
                }
            }
            // Refresh members from server to get accurate state
            try {
                val membersResp = api.getGroupMembers(groupId)
                if (membersResp.isSuccessful) {
                    val fresh = membersResp.body()?.map { m ->
                        GroupMember(userId = m.userId, name = m.name, role = m.role, isOnApp = true)
                    } ?: _groupMembers.value
                    AppCache.groupMembers[groupId] = fresh
                    _groupMembers.value = fresh
                }
            } catch (_: Exception) { }
        }
    }

    // ── Mock data (until expenses API is ready) ───────────────────────────

    // Legacy mock data — kept for reference only, not called
    @Suppress("unused")
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
            GroupMember(name = "You",   isOnApp = true),
            GroupMember(name = "Marc",  isOnApp = true),
            GroupMember(name = "Elena", isOnApp = true),
            GroupMember(name = "Alex",  isOnApp = false),
            GroupMember(name = "Sarah", isOnApp = false),
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
            GroupMember(name = "You",      isOnApp = true),
            GroupMember(name = "Roommate", isOnApp = true),
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
            GroupMember(name = "You",  isOnApp = true),
            GroupMember(name = "Alex", isOnApp = true),
            GroupMember(name = "Sarah",isOnApp = false),
            GroupMember(name = "Tom",  isOnApp = true),
            GroupMember(name = "Emma", isOnApp = false),
            GroupMember(name = "Jake", isOnApp = true),
        )
    }
}
