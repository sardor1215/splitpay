package com.splitpay.data.local

import com.splitpay.data.model.Expense
import com.splitpay.data.model.Group
import com.splitpay.data.model.Member

object AppCache {
    var groups: List<Group>?         = null
    var archivedGroups: List<Group>? = null
    val groupMembers: MutableMap<String, List<Member>> = mutableMapOf()
    val expensesByGroup: MutableMap<String, List<Expense>> = mutableMapOf()

    fun clearAll() {
        groups         = null
        archivedGroups = null
        groupMembers.clear()
        expensesByGroup.clear()
    }

    fun invalidateGroup(groupId: String) {
        groupMembers.remove(groupId)
        expensesByGroup.remove(groupId)
        groups         = groups?.filter { it.id != groupId }
        archivedGroups = archivedGroups?.filter { it.id != groupId }
    }
}
