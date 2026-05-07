package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.network.AdminGroupResponse
import com.splitpay.data.network.AdminKycDocResponse
import com.splitpay.data.network.AdminStatsResponse
import com.splitpay.data.network.AdminUserResponse
import com.splitpay.data.network.AmlAlertResponse
import com.splitpay.data.network.GdprConfigUpdateRequest
import com.splitpay.data.network.KycReviewRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.network.ReviewAlertRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.api

    private val _stats = MutableStateFlow<AdminStatsResponse?>(null)
    val stats: StateFlow<AdminStatsResponse?> = _stats

    private val _users = MutableStateFlow<List<AdminUserResponse>>(emptyList())
    val users: StateFlow<List<AdminUserResponse>> = _users

    private val _groups = MutableStateFlow<List<AdminGroupResponse>>(emptyList())
    val groups: StateFlow<List<AdminGroupResponse>> = _groups

    private val _amlAlerts = MutableStateFlow<List<AmlAlertResponse>>(emptyList())
    val amlAlerts: StateFlow<List<AmlAlertResponse>> = _amlAlerts

    private val _gdprConfig = MutableStateFlow<Map<String, String>>(emptyMap())
    val gdprConfig: StateFlow<Map<String, String>> = _gdprConfig

    private val _kycPending = MutableStateFlow<List<AdminKycDocResponse>>(emptyList())
    val kycPending: StateFlow<List<AdminKycDocResponse>> = _kycPending

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                val statsRes  = api.getAdminStats()
                val usersRes  = api.getAdminUsers()
                val groupsRes = api.getAdminGroups()
                val alertsRes = api.getAmlAlerts()
                val gdprRes   = api.getGdprConfig()
                val kycRes    = api.getKycPending()

                when {
                    statsRes.code() == 403 -> { _error.value = "Admin access required"; return@runCatching }
                    statsRes.isSuccessful  -> _stats.value = statsRes.body()
                }
                if (usersRes.isSuccessful)  _users.value      = usersRes.body() ?: emptyList()
                if (groupsRes.isSuccessful) _groups.value     = groupsRes.body() ?: emptyList()
                if (alertsRes.isSuccessful) _amlAlerts.value  = alertsRes.body() ?: emptyList()
                if (gdprRes.isSuccessful)   _gdprConfig.value = gdprRes.body()?.config ?: emptyMap()
                if (kycRes.isSuccessful)    _kycPending.value = kycRes.body() ?: emptyList()
            }.onFailure { e -> _error.value = e.message ?: e.javaClass.simpleName }
            _isLoading.value = false
        }
    }

    fun loadAmlAlerts(statusFilter: String? = null) {
        viewModelScope.launch {
            runCatching { api.getAmlAlerts(statusFilter) }.onSuccess { r ->
                if (r.isSuccessful) _amlAlerts.value = r.body() ?: emptyList()
            }
        }
    }

    fun reviewAlert(alertId: String, newStatus: String) {
        viewModelScope.launch {
            runCatching { api.reviewAmlAlert(alertId, ReviewAlertRequest(newStatus)) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _amlAlerts.value = _amlAlerts.value.map {
                            if (it.id == alertId) it.copy(status = newStatus) else it
                        }
                        _actionResult.value = "Alert marked as $newStatus"
                    }
                }
        }
    }

    fun updateGdprConfig(req: GdprConfigUpdateRequest) {
        viewModelScope.launch {
            runCatching { api.updateGdprConfig(req) }.onSuccess { r ->
                if (r.isSuccessful) {
                    _actionResult.value = "Configuration saved"
                    // Reload config
                    runCatching { api.getGdprConfig() }.onSuccess { cr ->
                        if (cr.isSuccessful) _gdprConfig.value = cr.body()?.config ?: emptyMap()
                    }
                }
            }
        }
    }

    fun runGdprCleanup() {
        viewModelScope.launch {
            _actionResult.value = null
            runCatching { api.runGdprCleanup() }.onSuccess { r ->
                _actionResult.value = if (r.isSuccessful) "Cleanup completed" else "Cleanup failed (${r.code()})"
            }.onFailure { _actionResult.value = "Cannot reach server" }
        }
    }

    fun reviewKycDocument(docId: String, status: String, rejectionReason: String? = null) {
        viewModelScope.launch {
            runCatching { api.reviewKycDocument(docId, KycReviewRequest(status, rejectionReason)) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _kycPending.value = _kycPending.value.filter { it.id != docId }
                        _actionResult.value = "Document $status"
                    }
                }
        }
    }

    fun liftSuspension(userId: String, userName: String) {
        viewModelScope.launch {
            runCatching { api.liftSuspension(userId) }.onSuccess { r ->
                if (r.isSuccessful) {
                    _users.value = _users.value.map {
                        if (it.id == userId) it.copy(amlStatus = "clear") else it
                    }
                    _amlAlerts.value = _amlAlerts.value.filter { it.userId != userId }
                    _actionResult.value = "$userName — suspension lifted"
                }
            }.onFailure { _actionResult.value = "Cannot reach server" }
        }
    }

    fun approveAllKycForUser(userId: String, userName: String) {
        viewModelScope.launch {
            runCatching { api.approveAllKycForUser(userId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _kycPending.value = _kycPending.value.filter { it.userId != userId }
                        _actionResult.value = "$userName verified"
                    } else {
                        _actionResult.value = "Failed (${r.code()})"
                    }
                }.onFailure { _actionResult.value = "Cannot reach server" }
        }
    }

    fun clearActionResult() { _actionResult.value = null }
}
