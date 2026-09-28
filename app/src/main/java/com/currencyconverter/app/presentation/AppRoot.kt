package com.currencyconverter.app.presentation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.currencyconverter.app.R
import com.currencyconverter.app.presentation.favorites.FavoritesScreen
import com.currencyconverter.app.presentation.home.HomeScreen
import com.currencyconverter.app.presentation.navigation.FavoritesRoute
import com.currencyconverter.app.presentation.navigation.HomeRoute
import com.currencyconverter.app.presentation.navigation.SettingsRoute
import com.currencyconverter.app.presentation.settings.SettingsScreen

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
)

private val destinations = listOf(
    TopLevelDestination(HomeRoute, R.string.nav_convert, Icons.Rounded.CurrencyExchange, "nav_home"),
    TopLevelDestination(FavoritesRoute, R.string.nav_favorites, Icons.Rounded.Star, "nav_favorites"),
    TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings, "nav_settings"),
)

@Composable
fun AppRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true,
                        onClick = { navController.navigateToTopLevel(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        modifier = Modifier.testTag(destination.tag),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<FavoritesRoute> {
                FavoritesScreen(onOpenConverter = { navController.navigateToTopLevel(HomeRoute) })
            }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
