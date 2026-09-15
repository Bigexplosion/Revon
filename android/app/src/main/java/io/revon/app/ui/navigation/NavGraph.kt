package io.revon.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.revon.app.ui.screens.*
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel
import io.revon.app.ui.viewmodel.RaceViewModel

object NavRoutes {
    const val ROUTES = "routes"
    const val RACE = "race"
    const val RANKING = "ranking"
    const val CLUBS = "clubs"
    const val CLUB_DETAIL = "club_detail"
    const val PROFILE = "profile"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SESSION_DETAIL = "session_detail"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    raceViewModel: RaceViewModel,
    chatViewModel: ChatViewModel,
    startDestination: String = NavRoutes.ROUTES
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) }
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(NavRoutes.ROUTES) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(NavRoutes.REGISTER)
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(NavRoutes.ROUTES) {
                        popUpTo(NavRoutes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    if (!navController.popBackStack(NavRoutes.LOGIN, inclusive = false)) {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.REGISTER) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.ROUTES) {
            RoutesScreen(
                raceViewModel = raceViewModel,
                onSelectTrackAndRace = {
                    navController.navigate(NavRoutes.RACE)
                }
            )
        }

        composable(NavRoutes.RACE) {
            RaceScreen(
                raceViewModel = raceViewModel,
                authViewModel = authViewModel,
                onNavigateToRanking = {
                    navController.navigate(NavRoutes.RANKING)
                },
                onNavigateToSessionDetail = { sessionId ->
                    navController.navigate("${NavRoutes.SESSION_DETAIL}/$sessionId") {
                        popUpTo(NavRoutes.RACE) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.RANKING) {
            RankingScreen(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                onNavigateToProfile = { targetSessionId ->
                    if (!targetSessionId.isNullOrEmpty()) {
                        navController.navigate("${NavRoutes.SESSION_DETAIL}/$targetSessionId")
                    } else {
                        navController.navigate(NavRoutes.PROFILE)
                    }
                }
            )
        }

        composable(NavRoutes.CLUBS) {
            ClubListScreen(
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateToClubDetail = {
                    navController.navigate("${NavRoutes.CLUB_DETAIL}/default")
                }
            )
        }

        composable(
            route = "${NavRoutes.CLUB_DETAIL}/{clubId}",
            arguments = listOf(navArgument("clubId") { type = NavType.StringType })
        ) {
            ClubDetailScreen(
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.PROFILE) {
            ProfileScreen(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                onLogout = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.PROFILE) { inclusive = true }
                    }
                },
                onNavigateToSessionDetail = { sessId ->
                    navController.navigate("${NavRoutes.SESSION_DETAIL}/$sessId")
                }
            )
        }

        composable(
            route = "${NavRoutes.SESSION_DETAIL}/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SessionDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
