package com.splitpay.navigation

sealed class Screen(val route: String) {

    // Auth
    data object Login : Screen("login")
    data object Register : Screen("register")

    // Principal
    data object Home : Screen("home")
    data object Groups : Screen("groups")
    data object CreateGroup : Screen("groups/create")

    data object GroupDetail : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }

    // Dépenses
    data object AddExpense : Screen("group/{groupId}/add_expense") {
        fun createRoute(groupId: String) = "group/$groupId/add_expense"
    }

    // Balances
    data object Balances : Screen("group/{groupId}/balances") {
        fun createRoute(groupId: String) = "group/$groupId/balances"
    }

    // Settlement
    data object Settlement : Screen("group/{groupId}/settlement") {
        fun createRoute(groupId: String) = "group/$groupId/settlement"
    }

    // Profil
    data object Profile : Screen("profile")

    // Admin
    data object Admin : Screen("admin")

    // KYC
    data object Kyc : Screen("kyc")

    // Expense Detail
    data object ExpenseDetail : Screen("group/{groupId}/expense/{expenseId}") {
        fun createRoute(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId"
    }

    // Edit Expense
    data object EditExpense : Screen("group/{groupId}/expense/{expenseId}/edit") {
        fun createRoute(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId/edit"
    }
}