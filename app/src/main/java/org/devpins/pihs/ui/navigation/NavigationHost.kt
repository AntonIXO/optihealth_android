package org.devpins.pihs.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.SupabaseClient
import org.devpins.pihs.health.HealthConnectAvailability
import org.devpins.pihs.health.SyncStatus
import org.devpins.pihs.ui.screens.HomeScreen
import org.devpins.pihs.ui.screens.ManualDataEntryScreen
import org.devpins.pihs.ui.screens.SettingsScreen
import java.time.Instant

@Composable
fun NavigationHost(
    healthConnectAvailability: HealthConnectAvailability,
    permissionsGranted: Boolean,
    syncStatus: SyncStatus,
    lastSyncTime: String?,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onSyncData: () -> Unit,
    onSyncDataInRange: (Instant, Instant) -> Unit,
    onCancelSync: () -> Unit,
    isLoggedIn: Boolean,
    supabaseClient: SupabaseClient,
    hasLocationPermissions: Boolean,
    hasBackgroundLocationPermission: Boolean,
    isLocationTrackingActive: Boolean,
    showLocationFeature: Boolean,
    showUsageFeature: Boolean,
    showNeiryFeature: Boolean,
    showTestUpload: Boolean,
    onOpenSettings: () -> Unit,
    onRequestLocationPermissions: () -> Unit,
    onStartLocationTracking: () -> Unit,
    onStopLocationTracking: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSyncUsageData: () -> Unit,
    onUploadSampleData: () -> Unit,
    onUploadEmptyData: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            // Only show bottom nav when user is logged in
            if (isLoggedIn) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    healthConnectAvailability = healthConnectAvailability,
                    permissionsGranted = permissionsGranted,
                    syncStatus = syncStatus,
                    lastSyncTime = lastSyncTime,
                    onRequestPermissions = onRequestPermissions,
                    onOpenHealthConnect = onOpenHealthConnect,
                    onSyncData = onSyncData,
                    onSyncDataInRange = onSyncDataInRange,
                    onCancelSync = onCancelSync,
                    isLoggedIn = isLoggedIn,
                    supabaseClient = supabaseClient,
                    hasLocationPermissions = hasLocationPermissions,
                    hasBackgroundLocationPermission = hasBackgroundLocationPermission,
                    isLocationTrackingActive = isLocationTrackingActive,
                    showLocationFeature = showLocationFeature,
                    showUsageFeature = showUsageFeature,
                    showNeiryFeature = showNeiryFeature,
                    showTestUpload = showTestUpload,
                    onOpenSettings = onOpenSettings,
                    onRequestLocationPermissions = onRequestLocationPermissions,
                    onStartLocationTracking = onStartLocationTracking,
                    onStopLocationTracking = onStopLocationTracking,
                    onOpenAppSettings = onOpenAppSettings,
                    onSyncUsageData = onSyncUsageData,
                    onUploadSampleData = onUploadSampleData,
                    onUploadEmptyData = onUploadEmptyData
                )
            }

            composable(Screen.ManualData.route) {
                ManualDataEntryScreen()
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
