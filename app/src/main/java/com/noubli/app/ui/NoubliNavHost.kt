package com.noubli.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noubli.app.AppContainer
import com.noubli.app.ui.auth.AuthScreen
import com.noubli.app.ui.history.HistoryScreen
import com.noubli.app.ui.home.HomeScreen
import com.noubli.app.ui.live.LiveTrackingScreen
import com.noubli.app.ui.zone.ZoneEditScreen
import com.noubli.app.ui.zone.ZoneEditViewModel

/** Routes de navigation de l'application. */
object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val HISTORY = "history"
    const val ZONE_ID_ARG = "zoneId"
    const val ZONE_EDIT = "zone_edit/{$ZONE_ID_ARG}"
    const val LIVE = "live/{$ZONE_ID_ARG}"

    /** Route concrète vers le formulaire (NEW_ZONE_ID pour une création). */
    fun zoneEdit(zoneId: Long) = "zone_edit/$zoneId"

    /** Route vers l'écran « Suivi en direct » d'une zone. */
    fun live(zoneId: Long) = "live/$zoneId"
}

/**
 * Graphe de navigation. L'écran affiché dépend de la session : sans utilisateur
 * connecté on est renvoyé vers l'authentification, sinon vers l'accueil.
 */
@Composable
fun NoubliNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val userId by container.session.userId.collectAsStateWithLifecycle()
    val startDestination = remember {
        if (container.session.userId.value == null) Routes.AUTH else Routes.HOME
    }

    // Connexion / déconnexion : on bascule d'écran et on vide la pile de retour.
    LaunchedEffect(userId) {
        val current = navController.currentDestination?.route
        if (userId == null && current != Routes.AUTH) {
            navController.navigate(Routes.AUTH) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else if (userId != null && current == Routes.AUTH) {
            navController.navigate(Routes.HOME) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.AUTH) { AuthScreen(container) }

        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onAddZone = { navController.navigate(Routes.zoneEdit(ZoneEditViewModel.NEW_ZONE_ID)) },
                onEditZone = { zoneId -> navController.navigate(Routes.zoneEdit(zoneId)) },
                onOpenLive = { zoneId -> navController.navigate(Routes.live(zoneId)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }

        composable(
            route = Routes.ZONE_EDIT,
            arguments = listOf(navArgument(Routes.ZONE_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val zoneId = entry.arguments?.getLong(Routes.ZONE_ID_ARG) ?: ZoneEditViewModel.NEW_ZONE_ID
            ZoneEditScreen(container, zoneId, onClose = { navController.popBackStack() })
        }

        composable(
            route = Routes.LIVE,
            arguments = listOf(navArgument(Routes.ZONE_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val zoneId = entry.arguments?.getLong(Routes.ZONE_ID_ARG) ?: return@composable
            LiveTrackingScreen(container, zoneId, onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(container, onBack = { navController.popBackStack() })
        }
    }
}
