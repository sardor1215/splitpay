package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.network.*
import com.splitpay.SplitPayApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SpaceUiState {
    data object Idle    : SpaceUiState()
    data object Loading : SpaceUiState()
    data object Success : SpaceUiState()
    data class Error(val message: String) : SpaceUiState()
}

class SpaceViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.api
    private val userId get() = (getApplication<SplitPayApp>()).tokenManager.userId ?: ""

    private val _spaces    = MutableStateFlow<List<SpaceResponse>>(emptyList())
    val spaces: StateFlow<List<SpaceResponse>> = _spaces

    private val _currentSpace = MutableStateFlow<SpaceResponse?>(null)
    val currentSpace: StateFlow<SpaceResponse?> = _currentSpace

    private val _audit = MutableStateFlow<List<SpaceAuditEntry>>(emptyList())
    val audit: StateFlow<List<SpaceAuditEntry>> = _audit

    private val _uiState = MutableStateFlow<SpaceUiState>(SpaceUiState.Idle)
    val uiState: StateFlow<SpaceUiState> = _uiState

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun refresh(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.getSpaces(groupId) }
                .onSuccess { r ->
                    if (r.isSuccessful) _spaces.value = r.body() ?: emptyList()
                }
            _isLoading.value = false
        }
    }

    // ── Charger la liste des espaces d'un groupe ──────────────────────────
    fun loadSpaces(groupId: String) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { api.getSpaces(groupId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _spaces.value = r.body() ?: emptyList()
                        _uiState.value = SpaceUiState.Idle
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    // ── Charger le détail d'un espace ────────────────────────────────────
    fun loadSpace(groupId: String, spaceId: String) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { api.getSpace(groupId, spaceId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _currentSpace.value = r.body()
                        _uiState.value = SpaceUiState.Idle
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    // ── Créer un espace (FR-047/048) ─────────────────────────────────────
    fun createSpace(groupId: String, request: CreateSpaceRequest, onSuccess: (SpaceResponse) -> Unit) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { api.createSpace(groupId, request) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        r.body()?.let { space ->
                            _spaces.value = listOf(space) + _spaces.value
                            onSuccess(space)
                        }
                        _uiState.value = SpaceUiState.Success
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    // ── Accepter sa participation (FR-053) ───────────────────────────────
    fun acceptSpace(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.acceptSpace(groupId, spaceId)
    }

    // ── Refuser sa participation (FR-053) ────────────────────────────────
    fun declineSpace(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.declineSpace(groupId, spaceId)
    }

    // ── Forcer le lancement avec membres acceptants (FR-054) ─────────────
    fun forceLaunch(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.forceLaunchSpace(groupId, spaceId)
    }

    // ── Déclencher le règlement (FR-050) ─────────────────────────────────
    fun settle(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.settleSpace(groupId, spaceId)
    }

    // ── Demander règlement anticipé PLAN (FR-051) ─────────────────────────
    fun requestEarlySettle(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.requestEarlySettle(groupId, spaceId)
    }

    // ── Voter sur le règlement anticipé (FR-051/052) ──────────────────────
    fun respondEarlySettle(groupId: String, spaceId: String, accepted: Boolean) =
        simpleAction(groupId, spaceId) {
            api.respondEarlySettle(groupId, spaceId, EarlySettleVoteRequest(if (accepted) "accepted" else "declined"))
        }

    // ── Confirmer le quorum (FR-058) ──────────────────────────────────────
    fun confirmQuorum(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.confirmQuorum(groupId, spaceId)
    }

    // ── Rejeter le quorum (FR-059) ────────────────────────────────────────
    fun rejectQuorum(groupId: String, spaceId: String) = simpleAction(groupId, spaceId) {
        api.rejectQuorum(groupId, spaceId)
    }

    // ── Assister un membre défaillant (FR-056/057) ────────────────────────
    fun assist(groupId: String, spaceId: String, memberId: String) =
        simpleAction(groupId, spaceId) {
            api.assistMember(groupId, spaceId, AssistRequest(memberId))
        }

    // ── Transférer le rôle lanceur ────────────────────────────────────────
    fun transferLauncher(groupId: String, spaceId: String, toUserId: String) =
        simpleAction(groupId, spaceId) {
            api.transferLauncher(groupId, spaceId, TransferLauncherRequest(toUserId))
        }

    // ── Journal d'audit (FR-062) ──────────────────────────────────────────
    fun loadAudit(groupId: String, spaceId: String) {
        viewModelScope.launch {
            runCatching { api.getSpaceAudit(groupId, spaceId) }
                .onSuccess { r -> if (r.isSuccessful) _audit.value = r.body() ?: emptyList() }
        }
    }

    fun editSpace(groupId: String, spaceId: String, request: EditSpaceRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { api.editSpace(groupId, spaceId, request) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        r.body()?.let { _currentSpace.value = it }
                        _uiState.value = SpaceUiState.Idle
                        onSuccess()
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    fun deleteSpace(groupId: String, spaceId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { api.deleteSpace(groupId, spaceId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _spaces.value = _spaces.value.filter { it.id != spaceId }
                        onSuccess()
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    fun resetState() { _uiState.value = SpaceUiState.Idle }
    fun clearSnackbar() { _snackbar.value = null }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun simpleAction(groupId: String, spaceId: String, call: suspend () -> retrofit2.Response<MessageResponse>) {
        viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading
            runCatching { call() }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        _snackbar.value = r.body()?.message ?: "Done"
                        loadSpace(groupId, spaceId)
                    } else {
                        _uiState.value = SpaceUiState.Error(extractError(r))
                    }
                }
                .onFailure { _uiState.value = SpaceUiState.Error("Cannot reach server") }
        }
    }

    private fun <T> extractError(r: retrofit2.Response<T>): String = runCatching {
        org.json.JSONObject(r.errorBody()?.string() ?: "").getString("message")
    }.getOrDefault("Error ${r.code()}")

    // ── Statut localisé ───────────────────────────────────────────────────
    companion object {
        fun statusLabel(status: String) = when (status) {
            "pending_acceptance" -> "Awaiting responses"
            "active"             -> "Active"
            "settling"           -> "Settlement in progress"
            "settled"            -> "Settled"
            "cancelled"          -> "Cancelled"
            "suspended"          -> "Suspended"
            else                 -> status
        }
        fun categoryLabel(category: String) = when (category) {
            "voyage"      -> "✈️ Trip"
            "colocation"  -> "🏠 Housing"
            "événement"   -> "🎉 Event"
            else          -> "📦 Other"
        }
        val categories = listOf("voyage", "colocation", "événement", "autre")
        val splitModes = listOf("equally", "exact", "percentage")
        val splitModeLabel = mapOf("equally" to "Equal split", "exact" to "Exact amounts", "percentage" to "By percentage")
    }
}
