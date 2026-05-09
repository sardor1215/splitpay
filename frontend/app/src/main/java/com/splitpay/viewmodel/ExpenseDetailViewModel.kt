package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.ExpenseActivityResponse
import com.splitpay.data.network.ExpenseResponse
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExpenseDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val api           = RetrofitClient.api
    private val tokenManager  = (app as SplitPayApp).tokenManager
    val currentUserId: String get() = tokenManager.userId ?: ""

    private val _expense    = MutableStateFlow<ExpenseResponse?>(null)
    val expense: StateFlow<ExpenseResponse?> = _expense

    private val _activities = MutableStateFlow<List<ExpenseActivityResponse>>(emptyList())
    val activities: StateFlow<List<ExpenseActivityResponse>> = _activities

    private val _isLoading  = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error      = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(groupId: String, expenseId: String) {
        viewModelScope.launch {
            _error.value = null

            // Show cached data immediately if available
            AppCache.expenseDetails[expenseId]?.let { _expense.value = it }
            AppCache.expenseActivities[expenseId]?.let { _activities.value = it }

            val hasCached = _expense.value != null
            _isLoading.value = !hasCached

            runCatching { api.getExpenseDetail(groupId, expenseId) }
                .onSuccess { r ->
                    if (r.isSuccessful) {
                        val body = r.body()!!
                        _expense.value    = body.expense
                        _activities.value = body.activities
                        AppCache.expenseDetails[expenseId]   = body.expense
                        AppCache.expenseActivities[expenseId] = body.activities
                    } else {
                        if (!hasCached) _error.value = "Failed to load expense (${r.code()})"
                    }
                }.onFailure {
                    if (!hasCached) _error.value = "Cannot reach server"
                }
            _isLoading.value = false
        }
    }
}
