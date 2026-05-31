package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.local.ThemeManager
import com.splitpay.data.network.DirectPayRequest
import com.splitpay.data.network.LookupRequest
import com.splitpay.data.network.PaymentHistoryItem
import com.splitpay.data.network.PendingInvitationResponse
import com.splitpay.data.network.RefreshRequest
import com.splitpay.data.network.RequireConsentRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.network.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _userName     = MutableStateFlow(tokenManager.userName ?: "")
    val userName: StateFlow<String> = _userName

    private val _userEmail    = MutableStateFlow(tokenManager.userEmail ?: "")
    val userEmail: StateFlow<String> = _userEmail

    private val _userPhone    = MutableStateFlow("")
    val userPhone: StateFlow<String> = _userPhone

    private val _totalBalance = MutableStateFlow(0.0)
    val totalBalance: StateFlow<Double> = _totalBalance

    private val _groupCount   = MutableStateFlow(AppCache.groups?.size ?: 0)
    val groupCount: StateFlow<Int> = _groupCount

    val darkMode: StateFlow<Boolean> = ThemeManager.isDark

    private val _isAdmin      = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _kycStatus      = MutableStateFlow("none")
    val kycStatus: StateFlow<String> = _kycStatus

    private val _requireConsent = MutableStateFlow(false)
    val requireConsent: StateFlow<Boolean> = _requireConsent

    private val _pendingInvitations = MutableStateFlow<List<PendingInvitationResponse>>(emptyList())
    val pendingInvitations: StateFlow<List<PendingInvitationResponse>> = _pendingInvitations

    private val _payments = MutableStateFlow<List<PaymentHistoryItem>>(emptyList())
    val payments: StateFlow<List<PaymentHistoryItem>> = _payments

    private val _accountBalance = MutableStateFlow(0.0)
    val accountBalance: StateFlow<Double> = _accountBalance

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _splitPayContacts  = MutableStateFlow<List<AddableContact>>(emptyList())
    val splitPayContacts: StateFlow<List<AddableContact>> = _splitPayContacts

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts

    init { loadProfile() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            doLoadProfile()
            _isLoading.value = false
        }
    }

    fun loadSplitPayContacts(cr: ContentResolver) {
        if (_splitPayContacts.value.isNotEmpty()) return  // already loaded
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

    private fun loadProfile() {
        viewModelScope.launch { doLoadProfile() }
    }

    private suspend fun doLoadProfile() {
        runCatching { api.getProfile() }.onSuccess { r ->
            if (r.isSuccessful) {
                val body = r.body()!!
                _userName.value       = body.name
                _userEmail.value      = body.email
                _userPhone.value      = body.phone ?: ""
                _isAdmin.value        = body.isAdmin
                _kycStatus.value      = body.kycStatus ?: "none"
                _requireConsent.value  = body.requireConsent
                _accountBalance.value  = body.accountBalance
                tokenManager.userName  = body.name
                tokenManager.userEmail = body.email
            }
        }
        val groups = AppCache.groups
        if (groups != null) {
            _totalBalance.value = groups.sumOf { it.balance }
            _groupCount.value   = groups.size
        }
        runCatching { api.getPaymentHistory() }.onSuccess { r ->
            if (r.isSuccessful) _payments.value = r.body().orEmpty()
        }
        loadInvitations()
    }

    fun loadInvitations() {
        viewModelScope.launch {
            runCatching { api.getPendingInvitations() }.onSuccess { r ->
                if (r.isSuccessful) _pendingInvitations.value = r.body().orEmpty()
            }
        }
    }

    fun updateProfile(name: String, phone: String) {
        viewModelScope.launch {
            runCatching {
                api.updateProfile(UpdateProfileRequest(
                    name  = name.trim().takeIf { it.isNotBlank() },
                    phone = phone.trim().takeIf { it.isNotBlank() }
                ))
            }.onSuccess { r ->
                if (r.isSuccessful) {
                    val body = r.body()!!
                    _userName.value       = body.name
                    _userPhone.value      = body.phone ?: ""
                    tokenManager.userName = body.name
                }
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { api.deleteAccount() }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        tokenManager.clear()
                        AppCache.clearAll()
                        onSuccess()
                    } else {
                        val msg = runCatching {
                            org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
                        }.getOrNull() ?: "Could not delete account (${r.code()})"
                        onError(msg)
                    }
                }
                .onFailure { onError("Cannot reach the server") }
        }
    }

    fun setRequireConsent(value: Boolean) {
        viewModelScope.launch {
            runCatching { api.setRequireConsent(RequireConsentRequest(value)) }.onSuccess { r ->
                if (r.isSuccessful) _requireConsent.value = value
            }
        }
    }

    fun acceptInvitation(inv: PendingInvitationResponse) {
        viewModelScope.launch {
            runCatching { api.acceptInvitation(inv.id) }.onSuccess { r ->
                if (r.isSuccessful) {
                    _pendingInvitations.value = _pendingInvitations.value.filter { it.id != inv.id }
                    AppCache.groups = null
                }
            }
        }
    }

    fun declineInvitation(inv: PendingInvitationResponse) {
        viewModelScope.launch {
            runCatching { api.declineInvitation(inv.id) }.onSuccess { r ->
                if (r.isSuccessful) _pendingInvitations.value = _pendingInvitations.value.filter { it.id != inv.id }
            }
        }
    }

    fun toggleDarkMode() {
        val prefs = getApplication<android.app.Application>()
            .getSharedPreferences("splitpay_theme", android.content.Context.MODE_PRIVATE)
        ThemeManager.toggle(prefs)
    }

    fun sendMoney(toUserId: String, amount: Double, note: String?, onSuccess: (Double) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { api.directPay(DirectPayRequest(toUserId, amount, note?.trim()?.takeIf { it.isNotBlank() })) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        val body = r.body()!!
                        _accountBalance.value = body.newBalance
                        runCatching { api.getPaymentHistory() }.onSuccess { pr ->
                            if (pr.isSuccessful) _payments.value = pr.body().orEmpty()
                        }
                        onSuccess(body.newBalance)
                    } else {
                        val msg = runCatching {
                            org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
                        }.getOrNull() ?: "Transfer failed (${r.code()})"
                        onError(msg)
                    }
                }
                .onFailure { onError("Cannot reach the server") }
        }
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            val refresh = tokenManager.refreshToken
            if (refresh != null) {
                runCatching { api.logout(RefreshRequest(refresh)) }
            }
            tokenManager.clear()
            AppCache.clearAll()
            onLogout()
        }
    }
}
