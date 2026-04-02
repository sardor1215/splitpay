package com.splitpay.data

import com.splitpay.data.model.Group
import com.splitpay.viewmodel.GroupMember

/**
 * Simple in-memory cache — serves stale data instantly while API refreshes silently.
 * Lives for the lifetime of the process (cleared on app kill/restart).
 */
object AppCache {
    // Groups list (Home + Groups screens)
    var groups: List<Group>? = null

    // Per-group detail + members (GroupDetailScreen)
    val groupDetails: MutableMap<String, Group> = mutableMapOf()
    val groupMembers: MutableMap<String, List<GroupMember>> = mutableMapOf()

    fun invalidateGroup(groupId: String) {
        groupDetails.remove(groupId)
        groupMembers.remove(groupId)
        groups = null          // force groups list to re-fetch too
    }

    fun clear() {
        groups = null
        groupDetails.clear()
        groupMembers.clear()
    }
}
