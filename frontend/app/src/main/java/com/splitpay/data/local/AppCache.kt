package com.splitpay.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.splitpay.data.model.Expense
import com.splitpay.data.model.Group
import com.splitpay.data.model.Member
import com.splitpay.data.network.ExpenseActivityResponse
import com.splitpay.data.network.ExpenseResponse

object AppCache {
    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null

    var groups: List<Group>?
        get() = _groups ?: loadPersistedGroups().also { _groups = it }
        set(value) { _groups = value; if (value != null) persistGroups(value) }

    private var _groups: List<Group>? = null
    var archivedGroups: List<Group>? = null
    val groupMembers: MutableMap<String, List<Member>> = mutableMapOf()
    val expensesByGroup: MutableMap<String, List<Expense>> = mutableMapOf()

    // Expense detail cache — keyed by expenseId
    val expenseDetails: MutableMap<String, ExpenseResponse> = mutableMapOf()
    val expenseActivities: MutableMap<String, List<ExpenseActivityResponse>> = mutableMapOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("splitpay_cache", Context.MODE_PRIVATE)
        _groups = loadPersistedGroups()
    }

    private fun persistGroups(groups: List<Group>) {
        prefs?.edit()?.putString("groups", gson.toJson(groups))?.apply()
    }

    private fun loadPersistedGroups(): List<Group>? {
        val json = prefs?.getString("groups", null) ?: return null
        return try {
            val type = object : TypeToken<List<Group>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { null }
    }

    fun clearAll() {
        _groups        = null
        archivedGroups = null
        groupMembers.clear()
        expensesByGroup.clear()
        expenseDetails.clear()
        expenseActivities.clear()
        // Keep disk cache so next login shows data instantly before refresh
    }

    fun invalidateGroup(groupId: String) {
        groupMembers.remove(groupId)
        val removedExpenseIds = expensesByGroup.remove(groupId)?.map { it.id } ?: emptyList()
        removedExpenseIds.forEach { expenseDetails.remove(it); expenseActivities.remove(it) }
        groups         = groups?.filter { it.id != groupId }
        archivedGroups = archivedGroups?.filter { it.id != groupId }
    }
}
