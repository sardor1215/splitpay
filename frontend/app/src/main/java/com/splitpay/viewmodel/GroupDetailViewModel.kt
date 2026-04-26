package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.model.Expense
import com.splitpay.data.model.Group
import com.splitpay.data.network.AddMemberRequest
import com.splitpay.data.network.LookupRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.network.UpdateGroupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Settlement(val memberName: String, val amount: Double, val owesYou: Boolean)
data class GroupMember(val userId: String, val name: String, val role: String)

// Reuse AppContact/DeviceContact from CreateGroupViewModel
data class AddableContact(
    val userId: String,
    val name: String,
    val phone: String,
    val email: String = "",
    val isSelected: Boolean = false
)
data class InvitableContact(val name: String, val phone: String)

class GroupDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group

    private val _members = MutableStateFlow<List<GroupMember>>(emptyList())
    val members: StateFlow<List<GroupMember>> = _members

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements

    private val _yourBalance = MutableStateFlow(0.0)
    val yourBalance: StateFlow<Double> = _yourBalance

    private val _totalSpending = MutableStateFlow(0.0)
    val totalSpending: StateFlow<Double> = _totalSpending

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _groupDeleted = MutableStateFlow(false)
    val groupDeleted: StateFlow<Boolean> = _groupDeleted

    // ── Add member contact lists ───────────────────────────────────────────────
    private val _addableContacts = MutableStateFlow<List<AddableContact>>(emptyList())
    val addableContacts: StateFlow<List<AddableContact>> = _addableContacts

    private val _invitableContacts = MutableStateFlow<List<InvitableContact>>(emptyList())
    val invitableContacts: StateFlow<List<InvitableContact>> = _invitableContacts

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts

    val emojis = listOf(
        "🏠", "✈️", "🍕", "🏕️", "🎉", "🛒",
        "🏋️", "🎮", "🎵", "🚗", "⚽", "📚",
        "💼", "🌴", "🍻", "🎓", "💊", "🐾"
    )

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val userId = tokenManager.userId

            val groupDef       = async { runCatching { api.getGroup(groupId) } }
            val membersDef     = async { runCatching { api.getMembers(groupId) } }
            val expensesDef    = async { runCatching { api.getExpenses(groupId) } }
            val balancesDef    = async { runCatching { api.getBalances(groupId) } }
            val settlementsDef = async { runCatching { api.getSettlements(groupId) } }

            groupDef.await().onSuccess { r ->
                r.body()?.let { g ->
                    _group.value = Group(
                        id           = g.id,
                        name         = g.name,
                        emoji        = g.emoji,
                        members      = emptyList(),
                        balance      = g.userBalance,
                        lastActivity = g.lastActivityAt,
                        isArchived   = g.isArchived,
                        inviteToken  = g.inviteToken
                    )
                }
            }

            membersDef.await().onSuccess { r ->
                r.body()?.let { list ->
                    _group.value = _group.value?.copy(members = list.map { it.name })
                    _members.value = list.map { GroupMember(it.userId, it.name, it.role) }
                    _isAdmin.value = list.find { it.userId == userId }?.role == "admin"
                }
            }

            expensesDef.await().onSuccess { r ->
                r.body()?.let { list ->
                    val expenses = list.map { e ->
                        val myShare = e.participants
                            .find { it.userId == userId }
                            ?.let { p -> if (e.paidBy == userId) e.amount - p.share else -p.share }
                            ?: 0.0
                        Expense(
                            id           = e.id,
                            title        = e.title,
                            amount       = e.amount,
                            paidBy       = e.paidByName,
                            paidById     = e.paidBy,
                            date         = e.createdAt.take(10),
                            yourShare    = myShare,
                            category     = e.category ?: "other",
                            splitMode    = e.splitMode ?: "equally",
                            participants = e.participants.map { it.userId }
                        )
                    }
                    _expenses.value = expenses
                    _totalSpending.value = expenses.sumOf { it.amount }
                }
            }

            balancesDef.await().onSuccess { r ->
                r.body()?.let { list ->
                    _yourBalance.value = list.find { it.userId == userId }?.amount ?: 0.0
                }
            }

            settlementsDef.await().onSuccess { r ->
                r.body()?.let { list ->
                    _settlements.value = list.map { s ->
                        Settlement(
                            memberName = if (s.fromUserId == userId) s.toName else s.fromName,
                            amount     = s.amount,
                            owesYou    = s.toUserId == userId
                        )
                    }
                }
            }

            _isLoading.value = false
        }
    }

    // ── Load contacts for add member sheet ────────────────────────────────────
    fun loadContactsForAdding(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isLoadingContacts.value = true

            val deviceContacts = withContext(Dispatchers.IO) { readDeviceContacts(contentResolver) }
            if (deviceContacts.isEmpty()) { _isLoadingContacts.value = false; return@launch }

            val currentUserId = tokenManager.userId
            val currentMemberIds = _members.value.map { it.userId }.toSet()

            runCatching { api.lookupByPhones(LookupRequest(deviceContacts.map { it.phone })) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        val appUsers = r.body().orEmpty()
                        val appPhones = appUsers.map { it.phone }.toSet()

                        // On SplitPay but not already in the group and not current user
                        _addableContacts.value = appUsers
                            .filter { it.userId != currentUserId && it.userId !in currentMemberIds }
                            .map { AddableContact(it.userId, it.name, it.phone, it.email ?: "") }

                        // Not on SplitPay
                        _invitableContacts.value = deviceContacts
                            .filter { it.phone !in appPhones }
                            .distinctBy { it.phone }
                            .map { InvitableContact(it.name, it.phone) }
                    }
                }

            _isLoadingContacts.value = false
        }
    }

    private fun readDeviceContacts(cr: ContentResolver): List<InvitableContact> {
        val result = mutableListOf<InvitableContact>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return result
        cursor.use {
            val nameCol  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameCol) ?: continue
                val phone = it.getString(phoneCol)?.replace("\\s|-".toRegex(), "") ?: continue
                result.add(InvitableContact(name, phone))
            }
        }
        return result.distinctBy { it.phone }
    }

    fun addMemberById(groupId: String, userId: String, userName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                api.addMember(groupId, AddMemberRequest(userId))
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    _members.value = _members.value + GroupMember(userId, userName, "member")
                    _group.value = _group.value?.copy(members = _members.value.map { it.name })
                    // Remove from addable list
                    _addableContacts.value = _addableContacts.value.filter { it.userId != userId }
                    onSuccess()
                } else {
                    onError("Failed to add member (${r.code()})")
                }
            }.onFailure { onError("Cannot reach server") }
        }
    }

    fun updateGroup(groupId: String, newName: String, newEmoji: String, onSuccess: () -> Unit) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching {
                api.updateGroup(groupId, UpdateGroupRequest(newName.trim(), newEmoji))
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    _group.value = _group.value?.copy(name = newName.trim(), emoji = newEmoji)
                    AppCache.groups = null
                    onSuccess()
                } else {
                    _error.value = "Failed to update group"
                }
            }.onFailure { _error.value = "Cannot reach server" }
        }
    }

    fun removeMember(groupId: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                api.removeMember(groupId, userId)
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    _members.value = _members.value.filter { it.userId != userId }
                    _group.value = _group.value?.copy(members = _members.value.map { it.name })
                    onSuccess()
                } else {
                    _error.value = "Failed to remove member"
                }
            }.onFailure { _error.value = "Cannot reach server" }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            runCatching {
                api.archiveGroup(groupId)
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    AppCache.invalidateGroup(groupId)
                    _groupDeleted.value = true
                } else {
                    _error.value = "Failed to delete group"
                }
            }.onFailure { _error.value = "Cannot reach server" }
        }
    }

    fun deleteExpense(groupId: String, expenseId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { api.deleteExpense(groupId, expenseId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _expenses.value = _expenses.value.filter { it.id != expenseId }
                        _totalSpending.value = _expenses.value.sumOf { it.amount }
                        AppCache.expensesByGroup.remove(groupId)
                        onSuccess()
                    } else {
                        _error.value = "Failed to delete expense"
                    }
                }.onFailure { _error.value = "Cannot reach server" }
        }
    }

    fun refresh(groupId: String) = loadGroup(groupId)
}