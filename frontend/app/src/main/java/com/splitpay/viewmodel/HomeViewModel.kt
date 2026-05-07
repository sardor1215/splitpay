package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.model.Group
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.splitpay.data.local.AppCache

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val userInitial: String
        get() = tokenManager.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val userName: String
        get() = tokenManager.userName ?: ""

    init {
        // Show cached data immediately, then refresh in background
        AppCache.groups?.let { _groups.value = it }
        fetchGroups()
    }

    fun fetchGroups() {
        viewModelScope.launch {
            // Only show spinner if there's nothing to display yet
            if (_groups.value.isEmpty()) _isLoading.value = true
            _error.value = null
            runCatching { api.getGroups() }
                .onSuccess { response ->
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
                }
                .onFailure {
                    if (_groups.value.isEmpty()) _error.value = "Cannot reach server"
                }
            _isLoading.value = false
        }
    }
}