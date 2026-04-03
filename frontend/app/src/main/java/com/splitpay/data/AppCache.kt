package com.splitpay.data

import com.splitpay.data.model.Group
import com.splitpay.viewmodel.Contact
import com.splitpay.viewmodel.ContactWithStatus
import com.splitpay.viewmodel.GroupMember

/**
 * Simple in-memory cache — serves stale data instantly while API refreshes silently.
 * Tagged with userId so reconnecting as the same user sees data immediately.
 * Different user → cache is wiped to avoid showing someone else's data.
 */
object AppCache {
    // The userId this cache belongs to
    private var ownerId: String? = null

    // Groups list (Home + Groups screens)
    var groups: List<Group>? = null
    var archivedGroups: List<Group>? = null

    // Per-group detail + members (GroupDetailScreen)
    val groupDetails: MutableMap<String, Group> = mutableMapOf()
    val groupMembers: MutableMap<String, List<GroupMember>> = mutableMapOf()

    // Device contacts for CreateGroupScreen
    var contacts: List<Contact>? = null

    // Device contacts enriched with SplitPay status for GroupDetailScreen add-member sheet
    var contactsWithStatus: List<ContactWithStatus>? = null

    /**
     * Called on login. If same user → keep cache. If different user → wipe first.
     */
    fun onLogin(userId: String) {
        if (ownerId != null && ownerId != userId) {
            clearAll()
        }
        ownerId = userId
    }

    fun invalidateGroup(groupId: String) {
        groupDetails.remove(groupId)
        groupMembers.remove(groupId)
        groups = null
        archivedGroups = null
    }

    /** Called on logout — keeps all cached data in memory so reconnecting
     *  as the same user sees everything instantly. Data is only wiped if
     *  a different user logs in (see onLogin). */
    fun onLogout() {
        // Intentionally keep groups, groupDetails, groupMembers, contacts
        // so the same user reconnecting sees data immediately
    }

    /** Full wipe (different user logged in, or explicit clear). */
    fun clearAll() {
        ownerId = null
        groups = null
        archivedGroups = null
        groupDetails.clear()
        groupMembers.clear()
        contacts = null
        contactsWithStatus = null
    }
}
