package com.splitpay.navigation

sealed class Screen(val route: String) {

    // Auth
    data object Login : Screen("login")
    data object Register : Screen("register")

    // Principal
    data object Home : Screen("home")

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
}