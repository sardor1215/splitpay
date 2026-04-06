package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.data.model.Group
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val api          = RetrofitClient.build(tokenManager)

    val userInitial: String
        get() = tokenManager.getUserName()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    private val _groups = MutableStateFlow<List<Group>>(AppCache.groups ?: emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _totalOwed = MutableStateFlow(AppCache.groups?.filter { it.balance > 0 }?.sumOf { it.balance } ?: 0.0)
    val totalOwed: StateFlow<Double> = _totalOwed

    private val _totalOwe = MutableStateFlow(AppCache.groups?.filter { it.balance < 0 }?.sumOf { -it.balance } ?: 0.0)
    val totalOwe: StateFlow<Double> = _totalOwe

    private val _archivedGroups = MutableStateFlow<List<Group>>(AppCache.archivedGroups ?: emptyList())
    val archivedGroups: StateFlow<List<Group>> = _archivedGroups

    private val _isLoading = MutableStateFlow(AppCache.groups == null)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var pollingJob: Job? = null

    init { loadGroups() }

    fun startPolling() {
        // Sync cache immediately every time the screen resumes (picks up changes from GroupDetail)
        AppCache.groups?.let { applyGroups(it) }
        AppCache.archivedGroups?.let { _archivedGroups.value = it }
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                fetchGroups()
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun loadGroups() {
        viewModelScope.launch {
            // Show cached data instantly
            AppCache.groups?.let { cached -> applyGroups(cached) }
            _isLoading.value = _groups.value.isEmpty()
            fetchGroups()
            _isLoading.value = false
        }
    }

    // Called both by loadGroups() and the polling loop
    private suspend fun fetchGroups() {
        try {
            val response = api.getGroups()
            if (response.isSuccessful) {
                val fresh = (response.body() ?: emptyList()).map { g ->
                    Group(
                        id           = g.id,
                        name         = g.name,
                        members      = emptyList(),
                        memberCount  = g.memberCount,
                        balance      = 0.0,
                        lastActivity = "",
                        emoji        = g.description?.takeIf { it.isNotBlank() } ?: "💰"
                    )
                }
                AppCache.groups = fresh
                applyGroups(fresh)
            }
            val archivedResponse = api.getArchivedGroups()
            if (archivedResponse.isSuccessful) {
                val freshArchived = (archivedResponse.body() ?: emptyList()).map { g ->
                    Group(
                        id           = g.id,
                        name         = g.name,
                        members      = emptyList(),
                        memberCount  = g.memberCount,
                        balance      = 0.0,
                        lastActivity = "",
                        emoji        = g.description?.takeIf { it.isNotBlank() } ?: "💰",
                        isArchived   = true
                    )
                }
                AppCache.archivedGroups = freshArchived
                _archivedGroups.value = freshArchived
            }
        } catch (_: Exception) {
            // Keep showing existing data on network error
        }
    }

    private fun applyGroups(list: List<Group>) {
        _groups.value    = list
        _totalOwed.value = list.filter { it.balance > 0 }.sumOf { it.balance }
        _totalOwe.value  = list.filter { it.balance < 0 }.sumOf { -it.balance }
    }
}
