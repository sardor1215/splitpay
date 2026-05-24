package com.splitpay.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.splitpay.SplitPayApp
import com.splitpay.ui.admin.AdminScreen
import com.splitpay.ui.notifications.NotificationsScreen
import com.splitpay.ui.kyc.KycScreen
import com.splitpay.ui.auth.LoginScreen
import com.splitpay.ui.auth.RegisterScreen
import com.splitpay.ui.groups.CreateGroupScreen
import com.splitpay.ui.groups.GroupsScreen
import com.splitpay.ui.home.HomeScreen
import com.splitpay.ui.group.GroupDetailScreen
import com.splitpay.ui.profile.ProfileScreen
import com.splitpay.ui.space.CreateSpaceScreen
import com.splitpay.ui.space.SpaceDetailScreen
import com.splitpay.ui.space.SpaceListScreen
import com.splitpay.viewmodel.GroupDetailViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.splitpay.data.network.GroupMemberResponse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.splitpay.data.local.AuthEvents
import com.splitpay.data.local.TokenManager


@Composable
fun NavGraph(navController: NavHostController) {

    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    val startDestination = if (tokenManager.isLoggedIn()) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    // Redirect to login when token refresh fails
    LaunchedEffect(Unit) {
        AuthEvents.sessionExpired.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Show dialog + logout when account is suspended
    var suspensionMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        AuthEvents.accountSuspended.collect { msg -> suspensionMessage = msg }
    }
    suspensionMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Account Suspended") },
            text  = { Text(msg) },
            confirmButton = {
                Button(onClick = {
                    suspensionMessage = null
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                }) { Text("OK") }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
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
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                }
            )
        }

        // ─── Notifications ──────────────────────────────────────
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack  = { navController.popBackStack() },
                onNavigateToSpace = { groupId, spaceId ->
                    navController.navigate(Screen.SpaceDetail.createRoute(groupId, spaceId))
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
                },
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                }
            )
        }

        // ─── Create Group ────────────────────────────────────
        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId)) {
                        popUpTo(Screen.CreateGroup.route) { inclusive = true }
                    }
                }
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
                onNavigateToExpense = { spaceId ->
                    navController.navigate(Screen.SpaceDetail.createRoute(groupId, spaceId))
                },
                onNavigateToCreateExpense = {
                    navController.navigate(Screen.CreateSpace.createRoute(groupId))
                }
            )
        }

        // ─── Space List ───────────────────────────────────
        composable(
            route = Screen.SpaceList.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            SpaceListScreen(
                groupId        = groupId,
                onNavigateBack = { navController.popBackStack() },
                onSpaceClick   = { spaceId -> navController.navigate(Screen.SpaceDetail.createRoute(groupId, spaceId)) },
                onCreateSpace  = { navController.navigate(Screen.CreateSpace.createRoute(groupId)) }
            )
        }

        // ─── Create Space ─────────────────────────────────
        composable(
            route = Screen.CreateSpace.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            val tokenManager = remember { TokenManager(context) }
            val currentUserId = tokenManager.userId ?: ""
            val groupVm: GroupDetailViewModel = viewModel()
            val rawMembers by groupVm.members.collectAsState()
            LaunchedEffect(groupId) { groupVm.loadGroup(groupId) }
            // Convertir GroupMember (local) → GroupMemberResponse (network)
            val members = rawMembers.map { GroupMemberResponse(it.userId, it.name, it.role, "") }
            CreateSpaceScreen(
                groupId       = groupId,
                members       = members,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onSpaceCreated = { spaceId ->
                    navController.navigate(Screen.SpaceDetail.createRoute(groupId, spaceId)) {
                        popUpTo(Screen.CreateSpace.createRoute(groupId)) { inclusive = true }
                    }
                }
            )
        }

        // ─── Space Detail ─────────────────────────────────
        composable(
            route = Screen.SpaceDetail.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("spaceId") { type = NavType.StringType }
            )
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: ""
            val spaceId = back.arguments?.getString("spaceId") ?: ""
            val tokenManager = remember { TokenManager(context) }
            SpaceDetailScreen(
                groupId       = groupId,
                spaceId       = spaceId,
                currentUserId = tokenManager.userId ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Profile ────────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack    = { navController.popBackStack() },
                onNavigateToAdmin = { navController.navigate(Screen.Admin.route) },
                onNavigateToKyc   = { navController.navigate(Screen.Kyc.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ─── Admin ───────────────────────────────────────────
        composable(Screen.Admin.route) {
            AdminScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ─── KYC ─────────────────────────────────────────────
        composable(Screen.Kyc.route) {
            KycScreen(onNavigateBack = { navController.popBackStack() })
        }

    }
}