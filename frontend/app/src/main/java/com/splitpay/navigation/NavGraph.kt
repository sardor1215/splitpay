package com.splitpay.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.splitpay.ui.auth.LoginScreen
import com.splitpay.ui.auth.RegisterScreen
import com.splitpay.ui.groups.CreateGroupScreen
import com.splitpay.ui.groups.GroupsScreen
import com.splitpay.ui.home.HomeScreen
import com.splitpay.ui.expense.AddExpenseScreen
import com.splitpay.ui.group.GroupDetailScreen
import com.splitpay.ui.profile.ProfileScreen
import com.splitpay.ui.settlement.SettlementScreen

@Composable
fun NavGraph(navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {

        // ─── Auth ─────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        // Vide la back stack : l'utilisateur ne peut pas
                        // revenir au Login en appuyant sur "retour"
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Home   ────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToGroup = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToGroups = {
                    navController.navigate(Screen.Groups.route)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route)
                },
                onNavigateToSettlement = { groupId ->
                    navController.navigate(Screen.Settlement.createRoute(groupId))
                }
            )
        }

        // ─── Groups ─────────────────────────────────────────
        composable(Screen.Groups.route) {
            GroupsScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToGroup = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route)
                }
            )
        }

        // ─── Create Group ────────────────────────────────────
        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Group Detail ─────────────────────────────────
        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupDetailScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddExpense = { id ->
                    navController.navigate(Screen.AddExpense.createRoute(id))
                },
                onNavigateToSettlement = { id ->
                    navController.navigate(Screen.Settlement.createRoute(id))
                }
            )
        }

        // ─── Settlement ────────────────────────────────────
        composable(
            route = Screen.Settlement.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            SettlementScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Add Expense ───────────────────────────────────
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            AddExpenseScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Profile ────────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}