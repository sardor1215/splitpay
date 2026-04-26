package com.splitpay.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.AddMemberRequest
import com.splitpay.data.network.CreateGroupRequest
import com.splitpay.data.network.LookupRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppContact(
    val userId: String,
    val name: String,
    val phone: String,
    val email: String = "",
    val isSelected: Boolean = false
)

data class DeviceContact(
    val name: String,
    val phone: String
)

class CreateGroupViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.api
    private val currentUserId = (app as SplitPayApp).tokenManager.userId

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName

    private val _selectedEmoji = MutableStateFlow("🏠")
    val selectedEmoji: StateFlow<String> = _selectedEmoji

    // Contacts already on SplitPay
    private val _appContacts = MutableStateFlow<List<AppContact>>(emptyList())
    val appContacts: StateFlow<List<AppContact>> = _appContacts

    // Contacts NOT on SplitPay
    private val _nonAppContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val nonAppContacts: StateFlow<List<DeviceContact>> = _nonAppContacts

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val emojis = listOf(
        "🏠", "✈️", "🍕", "🏕️", "🎉", "🛒",
        "🏋️", "🎮", "🎵", "🚗", "⚽", "📚",
        "💼", "🌴", "🍻", "🎓", "💊", "🐾"
    )

    fun onGroupNameChange(value: String) { _groupName.value = value }
    fun onEmojiSelected(emoji: String)   { _selectedEmoji.value = emoji }

    fun onToggleContact(userId: String) {
        _appContacts.value = _appContacts.value.map {
            if (it.userId == userId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun loadContacts(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isLoadingContacts.value = true

            val deviceContacts = withContext(Dispatchers.IO) { readDeviceContacts(contentResolver) }
            if (deviceContacts.isEmpty()) { _isLoadingContacts.value = false; return@launch }

            val phones = deviceContacts.map { it.phone }

            runCatching { api.lookupByPhones(LookupRequest(phones)) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        val allAppUsers = r.body()!!
                        val allAppPhones = allAppUsers.map { it.phone }.toSet()
                        val appUsers = allAppUsers.filter { it.userId != currentUserId }

                        _appContacts.value = appUsers.map {
                            AppContact(it.userId, it.name ?: "", it.phone ?: "", it.email ?: "")
                        }

                        // Device contacts whose phone is NOT in the app
                        _nonAppContacts.value = deviceContacts
                            .filter { it.phone !in allAppPhones && it.name.isNotBlank() }
                            .distinctBy { it.phone }
                    }
                }

            _isLoadingContacts.value = false
        }
    }

    private fun readDeviceContacts(cr: ContentResolver): List<DeviceContact> {
        val result = mutableListOf<DeviceContact>()
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return result
        cursor.use {
            val nameCol  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameCol) ?: continue
                val phone = it.getString(phoneCol)?.replace("\\s|-".toRegex(), "") ?: continue
                result.add(DeviceContact(name, phone))
            }
        }
        return result.distinctBy { it.phone }
    }

    fun createGroup(onSuccess: (groupId: String) -> Unit) {
        if (_groupName.value.isBlank()) {
            _error.value = "Group name is required"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                api.createGroup(CreateGroupRequest(_groupName.value.trim(), _selectedEmoji.value))
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    val groupId = response.body()!!.id
                    AppCache.groups = null
                    _appContacts.value.filter { it.isSelected }.forEach { contact ->
                        runCatching { api.addMember(groupId, AddMemberRequest(contact.userId)) }
                    }
                    onSuccess(groupId)
                } else {
                    _error.value = "Error ${response.code()}"
                }
            }.onFailure {
                _error.value = "Cannot reach the server"
            }
            _isLoading.value = false
        }
    }
}
