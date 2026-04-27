package com.celox.segway.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.celox.segway.R
import com.celox.segway.feature.about.AboutScreen
import com.celox.segway.feature.airlock.AirLockScreen
import com.celox.segway.feature.battery.BatteryDetailScreen
import com.celox.segway.feature.diagnostics.DiagnosticsScreen
import com.celox.segway.feature.manual.ManualScreen
import com.celox.segway.feature.firmware.FirmwareScreen
import com.celox.segway.feature.garage.GarageScreen
import com.celox.segway.feature.home.VehicleScreen
import com.celox.segway.feature.mine.MineScreen
import com.celox.segway.feature.pair.PairScreen
import com.celox.segway.feature.profiles.ProfilesScreen
import com.celox.segway.feature.scooter_settings.ScooterSettingsScreen
import com.celox.segway.feature.settings.SettingsScreen
import com.celox.segway.feature.track.TrackScreen

@Composable
fun SegwayApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Route.Vehicle.route, Route.Track.route, Route.Mine.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) BottomNav(currentRoute) { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Vehicle.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.Vehicle.route) {
                VehicleScreen(
                    onPairClick = { navController.navigate(Route.Pair.route) },
                    onBatteryDetailClick = { navController.navigate(Route.BatteryDetail.route) },
                )
            }
            composable(Route.Track.route) { TrackScreen() }
            composable(Route.Mine.route) {
                MineScreen(
                    onSettingsClick = { navController.navigate(Route.Settings.route) },
                    onAboutClick = { navController.navigate(Route.About.route) },
                    onGarageClick = { navController.navigate(Route.Garage.route) },
                    onFirmwareClick = { navController.navigate(Route.Firmware.route) },
                    onDiagnosticsClick = { navController.navigate(Route.Diagnostics.route) },
                    onAirLockClick = { navController.navigate(Route.AirLock.route) },
                    onProfilesClick = { navController.navigate(Route.Profiles.route) },
                    onScooterSettingsClick = { navController.navigate(Route.ScooterSettings.route) },
                    onManualClick = { navController.navigate(Route.Manual.route) },
                )
            }
            composable(Route.Pair.route) {
                PairScreen(onClose = { navController.popBackStack() })
            }
            composable(Route.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Garage.route) {
                GarageScreen(
                    onBack = { navController.popBackStack() },
                    onPairClick = { navController.navigate(Route.Pair.route) }
                )
            }
            composable(Route.Firmware.route) {
                FirmwareScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Diagnostics.route) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.AirLock.route) {
                AirLockScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Profiles.route) {
                ProfilesScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.BatteryDetail.route) {
                BatteryDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.ScooterSettings.route) {
                ScooterSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Manual.route) {
                ManualScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomNav(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        listOf(
            BottomItem(Route.Vehicle, R.string.nav_vehicle, Icons.Outlined.ElectricScooter),
            BottomItem(Route.Track, R.string.nav_track, Icons.Outlined.Timeline),
            BottomItem(Route.Mine, R.string.nav_mine, Icons.Outlined.Person),
        ).forEach { item ->
            NavigationBarItem(
                selected = currentRoute?.hierarchyContains(item.route.route) == true,
                onClick = { onSelect(item.route.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) }
            )
        }
    }
}

private fun String.hierarchyContains(other: String): Boolean = this == other

private data class BottomItem(
    val route: Route,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

sealed class Route(val route: String) {
    data object Vehicle : Route("vehicle")
    data object Track : Route("track")
    data object Mine : Route("mine")
    data object Pair : Route("pair")
    data object Settings : Route("settings")
    data object About : Route("about")
    data object Garage : Route("garage")
    data object Firmware : Route("firmware")
    data object Diagnostics : Route("diagnostics")
    data object AirLock : Route("airlock")
    data object Profiles : Route("profiles")
    data object BatteryDetail : Route("battery_detail")
    data object ScooterSettings : Route("scooter_settings")
    data object Manual : Route("manual")
}
