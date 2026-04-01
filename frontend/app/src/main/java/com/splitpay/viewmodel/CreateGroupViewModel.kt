package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Contact(
    val id: String,
    val name: String,
    val isAdded: Boolean = false,
    val isOnApp: Boolean = true
)

class CreateGroupViewModel : ViewModel() {

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _selectedEmoji = MutableStateFlow("🏠")
    val selectedEmoji: StateFlow<String> = _selectedEmoji

    private val _contacts = MutableStateFlow(
        listOf(
            Contact(id = "1", name = "Sarah Miller", isOnApp = true),
            Contact(id = "2", name = "James Wilson", isOnApp = false),
            Contact(id = "3", name = "Marc Dupont",  isOnApp = true),
            Contact(id = "4", name = "Emma Larson",  isOnApp = false),
            Contact(id = "5", name = "Tom Nguyen",   isOnApp = true),
            Contact(id = "6", name = "Jake Foster",  isOnApp = false),
        )
    )
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
            delay(1500) // TODO: appel API réel
            _isSyncing.value = false
        }
    }

    fun createGroup(onSuccess: () -> Unit) {
        if (_groupName.value.isNotBlank()) {
            // TODO: connecter à l'API backend
            onSuccess()
        }
    }
}
