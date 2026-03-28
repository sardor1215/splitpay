package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Contact(
    val id: String,
    val name: String,
    val isAdded: Boolean = false
)

class CreateGroupViewModel : ViewModel() {

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _selectedEmoji = MutableStateFlow("🏠")
    val selectedEmoji: StateFlow<String> = _selectedEmoji

    private val _contacts = MutableStateFlow(
        listOf(
            Contact(id = "1", name = "Sarah Miller"),
            Contact(id = "2", name = "James Wilson"),
            Contact(id = "3", name = "Marc Dupont"),
            Contact(id = "4", name = "Emma Larson"),
            Contact(id = "5", name = "Tom Nguyen"),
            Contact(id = "6", name = "Jake Foster"),
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

    fun onToggleContact(contactId: String) {
        _contacts.value = _contacts.value.map {
            if (it.id == contactId) it.copy(isAdded = !it.isAdded) else it
        }
    }

    fun createGroup(onSuccess: () -> Unit) {
        if (_groupName.value.isNotBlank()) {
            // TODO: connecter à l'API backend
            onSuccess()
        }
    }
}
