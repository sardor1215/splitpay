package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.network.EarlySettleVoteRequest
import com.splitpay.data.network.PendingInvitationResponse
import com.splitpay.data.network.PendingSpaceInvitation
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.api

    private val _invitations = MutableStateFlow<List<PendingInvitationResponse>>(emptyList())
    val invitations: StateFlow<List<PendingInvitationResponse>> = _invitations

    private val _spaceInvitations = MutableStateFlow<List<PendingSpaceInvitation>>(emptyList())
    val spaceInvitations: StateFlow<List<PendingSpaceInvitation>> = _spaceInvitations

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { load() }

    fun refresh() { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val invDef   = async { runCatching { api.getPendingInvitations() } }
            val spaceDef = async { runCatching { api.getPendingSpaceInvitations() } }
            invDef.await().onSuccess   { r -> if (r.isSuccessful) _invitations.value      = r.body().orEmpty() }
            spaceDef.await().onSuccess { r -> if (r.isSuccessful) _spaceInvitations.value = r.body().orEmpty() }
            _isLoading.value = false
        }
    }

    // ── Groupe ────────────────────────────────────────────────────────────
    fun accept(inv: PendingInvitationResponse) {
        viewModelScope.launch {
            runCatching { api.acceptInvitation(inv.id) }.onSuccess { r ->
                if (r.isSuccessful) _invitations.value = _invitations.value.filter { it.id != inv.id }
            }
        }
    }

    fun decline(inv: PendingInvitationResponse) {
        viewModelScope.launch {
            runCatching { api.declineInvitation(inv.id) }.onSuccess { r ->
                if (r.isSuccessful) _invitations.value = _invitations.value.filter { it.id != inv.id }
            }
        }
    }

    // ── Espace — invitation classique (pending_acceptance) ────────────────
    fun acceptSpace(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.acceptSpace(inv.groupId, inv.id) }.onSuccess { r ->
                if (r.isSuccessful) removeSpace(inv.id)
            }
        }
    }

    fun declineSpace(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.declineSpace(inv.groupId, inv.id) }.onSuccess { r ->
                if (r.isSuccessful) removeSpace(inv.id)
            }
        }
    }

    // ── Espace — quorum (settling) ─────────────────────────────────────────
    fun confirmQuorum(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.confirmQuorum(inv.groupId, inv.id) }.onSuccess { r ->
                if (r.isSuccessful) removeSpace(inv.id)
            }
        }
    }

    fun rejectQuorum(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.rejectQuorum(inv.groupId, inv.id) }.onSuccess { r ->
                if (r.isSuccessful) removeSpace(inv.id)
            }
        }
    }

    // ── Espace — règlement anticipé PLAN ──────────────────────────────────
    fun acceptEarlySettle(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.respondEarlySettle(inv.groupId, inv.id, EarlySettleVoteRequest("accepted")) }
                .onSuccess { r -> if (r.isSuccessful) removeSpace(inv.id) }
        }
    }

    fun declineEarlySettle(inv: PendingSpaceInvitation) {
        viewModelScope.launch {
            runCatching { api.respondEarlySettle(inv.groupId, inv.id, EarlySettleVoteRequest("declined")) }
                .onSuccess { r -> if (r.isSuccessful) removeSpace(inv.id) }
        }
    }

    private fun removeSpace(id: String) {
        _spaceInvitations.value = _spaceInvitations.value.filter { it.id != id }
    }
}
