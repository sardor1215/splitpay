package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.data.model.Group
import com.splitpay.network.AddMemberRequest
import com.splitpay.network.CreateGroupRequest
import com.splitpay.network.PhoneLookupRequest
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import com.splitpay.util.ContactReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Contact(
    val id: String,
    val name: String,
    val isAdded: Boolean = false,
    val isOnApp: Boolean = true
)

class CreateGroupViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val api          = RetrofitClient.build(tokenManager)

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _selectedEmoji = MutableStateFlow("🏠")
    val selectedEmoji: StateFlow<String> = _selectedEmoji

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    val emojis = listOf(
        "🏠", "✈️", "🍕", "🏕️", "🎉", "🛒",
        "🏋️", "🎮", "🎵", "🚗", "⚽", "📚",
        "💼", "🌴", "🍻", "🎓", "💊", "🐾"
    )

    fun onGroupNameChange(value: String) {
        _groupName.value = value
    }

    fun onEmojiSelected(emoji: String) {
        _selectedEmoji.value = emoji
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun onToggleContact(contactId: String) {
        _contacts.value = _contacts.value.map {
            if (it.id == contactId) it.copy(isAdded = !it.isAdded) else it
        }
    }

    fun syncContacts() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val deviceContacts = withContext(Dispatchers.IO) {
                    ContactReader.read(getApplication())
                }
                if (deviceContacts.isNotEmpty()) {
                    val phones   = deviceContacts.map { it.phone }
                    val response = api.lookupUsers(PhoneLookupRequest(phones))
                    // Map phone → (userId, name) for contacts on SplitPay
                    val lookupMap = if (response.isSuccessful)
                        response.body()?.associate { it.phone to it.userId } ?: emptyMap()
                    else emptyMap()

                    _contacts.value = deviceContacts.map { dc ->
                        val userId = lookupMap[dc.phone]
                        Contact(
                            id      = userId ?: dc.phone, // real userId when on app
                            name    = dc.name,
                            isOnApp = userId != null
                        )
                    }
                }
            } catch (_: Exception) { /* keep existing list */ }
            finally { _isSyncing.value = false }
        }
    }

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun clearError() { _createError.value = null }

    fun createGroup(onSuccess: (groupId: String) -> Unit) {
        if (_groupName.value.isBlank()) {
            _createError.value = "Group name is required"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _createError.value = null
            try {
                val response = api.createGroup(
                    CreateGroupRequest(
                        name        = _groupName.value,
                        description = _selectedEmoji.value
                    )
                )
                if (response.isSuccessful) {
                    val groupId  = response.body()!!.id
                    val selected = _contacts.value.filter { it.isAdded && it.isOnApp }

                    // Add all selected contacts as members
                    selected.forEach { contact ->
                        try { api.addMemberToGroup(groupId, AddMemberRequest(contact.id)) }
                        catch (_: Exception) { }
                    }

                    // Optimistic cache — populate immediately so GroupDetail shows
                    // data instantly without waiting for API on first open
                    val myName = tokenManager.getUserName() ?: "You"
                    val optimisticMembers = buildList {
                        add(GroupMember(name = myName, isOnApp = true))
                        selected.forEach { add(GroupMember(name = it.name, isOnApp = true)) }
                    }
                    val optimisticGroup = Group(
                        id           = groupId,
                        name         = _groupName.value,
                        members      = emptyList(),
                        memberCount  = optimisticMembers.size,
                        balance      = 0.0,
                        lastActivity = "",
                        emoji        = _selectedEmoji.value
                    )
                    AppCache.groupDetails[groupId] = optimisticGroup
                    AppCache.groupMembers[groupId] = optimisticMembers
                    AppCache.groups = null   // force groups list to re-fetch fresh

                    onSuccess(groupId)
                } else {
                    _createError.value = "Failed to create group (${response.code()})"
                }
            } catch (_: Exception) {
                _createError.value = "Cannot reach server. Check your connection."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
