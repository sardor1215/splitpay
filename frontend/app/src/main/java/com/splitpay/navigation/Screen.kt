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

    // Notifications
    data object Notifications : Screen("notifications")

    // Profil
    data object Profile : Screen("profile")

    // Admin
    data object Admin : Screen("admin")

    // KYC
    data object Kyc : Screen("kyc")

    // Espaces
    data object SpaceList : Screen("group/{groupId}/spaces") {
        fun createRoute(groupId: String) = "group/$groupId/spaces"
    }

    data object CreateSpace : Screen("group/{groupId}/spaces/create") {
        fun createRoute(groupId: String) = "group/$groupId/spaces/create"
    }

    data object SpaceDetail : Screen("group/{groupId}/spaces/{spaceId}") {
        fun createRoute(groupId: String, spaceId: String) = "group/$groupId/spaces/$spaceId"
    }
}