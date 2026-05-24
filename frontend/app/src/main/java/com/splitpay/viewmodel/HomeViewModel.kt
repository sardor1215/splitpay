package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.model.Group
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.DirectPayRequest
import com.splitpay.data.network.GroupDebtorResponse
import com.splitpay.data.network.LookupRequest
import com.splitpay.data.network.LookupUserResponse

sealed class PayState {
    data object Idle    : PayState()
    data object Loading : PayState()
    data class  Success(val newBalance: Double) : PayState()
    data class  Error(val message: String) : PayState()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _accountBalance = MutableStateFlow(0.0)
    val accountBalance: StateFlow<Double> = _accountBalance

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _debtors  = MutableStateFlow<List<GroupDebtorResponse>>(emptyList())
    val debtors: StateFlow<List<GroupDebtorResponse>> = _debtors

    private val _splitPayContacts  = MutableStateFlow<List<AddableContact>>(emptyList())
    val splitPayContacts: StateFlow<List<AddableContact>> = _splitPayContacts

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts

    private val _payState = MutableStateFlow<PayState>(PayState.Idle)
    val payState: StateFlow<PayState> = _payState

    sealed class LookupState {
        data object Idle    : LookupState()
        data object Loading : LookupState()
        data class  Found(val user: LookupUserResponse) : LookupState()
        data class  NotFound(val message: String) : LookupState()
    }
    private val _lookupState = MutableStateFlow<LookupState>(LookupState.Idle)
    val lookupState: StateFlow<LookupState> = _lookupState

    val userInitial: String
        get() = tokenManager.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val userName: String
        get() = tokenManager.userName ?: ""

    init {
        AppCache.groups?.let { _groups.value = it }
        fetchGroups()
    }

    fun loadDebtors(groupId: String) {
        _debtors.value = emptyList()
        viewModelScope.launch {
            runCatching { api.getGroupDebtors(groupId) }.onSuccess { r ->
                if (r.isSuccessful) _debtors.value = r.body().orEmpty()
            }
        }
    }

    fun directPay(toUserId: String, amount: Double, note: String? = null) {
        viewModelScope.launch {
            _payState.value = PayState.Loading
            runCatching { api.directPay(DirectPayRequest(toUserId, amount, note)) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        val body = r.body()!!
                        _accountBalance.value = body.newBalance
                        _payState.value = PayState.Success(body.newBalance)
                    } else {
                        val msg = runCatching {
                            org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
                        }.getOrNull() ?: "Payment failed (${r.code()})"
                        _payState.value = PayState.Error(msg)
                    }
                }
                .onFailure { _payState.value = PayState.Error("Cannot reach the server") }
        }
    }

    fun resetPayState() { _payState.value = PayState.Idle }

    fun loadSplitPayContacts(cr: ContentResolver) {
        if (_splitPayContacts.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingContacts.value = true
            val deviceContacts = withContext(Dispatchers.IO) { readDeviceContacts(cr) }
            if (deviceContacts.isNotEmpty()) {
                runCatching { api.lookupByPhones(LookupRequest(deviceContacts.map { it.phone })) }
                    .onSuccess { r ->
                        if (r.isSuccessful) {
                            _splitPayContacts.value = r.body().orEmpty()
                                .map { AddableContact(it.userId, it.name, it.phone, it.email ?: "") }
                        }
                    }
            }
            _isLoadingContacts.value = false
        }
    }

    private fun readDeviceContacts(cr: ContentResolver): List<AddableContact> {
        val result = mutableListOf<AddableContact>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return result
        cursor.use {
            val nameCol  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameCol) ?: continue
                val phone = it.getString(phoneCol)?.replace(Regex("[\\s-]"), "") ?: continue
                result.add(AddableContact("", name, phone))
            }
        }
        return result.distinctBy { it.phone }
    }

    fun lookupUser(phone: String) {
        viewModelScope.launch {
            _lookupState.value = LookupState.Loading
            runCatching { api.lookupByPhones(LookupRequest(listOf(phone.trim()))) }
                .onSuccess { r ->
                    val user = r.body()?.firstOrNull()
                    _lookupState.value = if (r.isSuccessful && user != null)
                        LookupState.Found(user)
                    else
                        LookupState.NotFound("No user found with this phone number")
                }
                .onFailure { _lookupState.value = LookupState.NotFound("Cannot reach the server") }
        }
    }

    fun resetLookup() { _lookupState.value = LookupState.Idle }

    fun fetchGroups() {
        viewModelScope.launch {
            if (_groups.value.isEmpty()) _isLoading.value = true
            _error.value = null

            // Fetch groups and account balance in parallel
            val groupsResult  = runCatching { api.getGroups() }
            val profileResult = runCatching { api.getProfile() }

            groupsResult.onSuccess { response ->
                if (response.isSuccessful) {
                    val groups = response.body().orEmpty().map { g ->
                        Group(
                            id           = g.id,
                            name         = g.name,
                            emoji        = g.emoji,
                            members      = List(g.memberCount) { "" },
                            balance      = g.userBalance,
                            lastActivity = g.lastActivityAt,
                            isArchived   = g.isArchived,
                            inviteToken  = g.inviteToken
                        )
                    }
                    AppCache.groups = groups
                    _groups.value = groups
                }
            }.onFailure {
                if (_groups.value.isEmpty()) _error.value = "Cannot reach server"
            }

            profileResult.onSuccess { r ->
                if (r.isSuccessful) _accountBalance.value = r.body()?.accountBalance ?: 0.0
            }

            // Badge = invitations groupe/dépense + invitations/quorums espaces
            var count = 0
            runCatching { api.getPendingInvitations() }.onSuccess { r ->
                if (r.isSuccessful) count += r.body()?.size ?: 0
            }
            runCatching { api.getPendingSpaceInvitations() }.onSuccess { r ->
                if (r.isSuccessful) count += r.body()?.size ?: 0
            }
            _pendingCount.value = count

            _isLoading.value = false
        }
    }
}