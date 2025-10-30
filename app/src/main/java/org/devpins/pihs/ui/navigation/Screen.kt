package org.devpins.pihs.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object ManualData : Screen("manual_data", "Add Data", Icons.Default.Add)
    object Supplements : Screen("supplements", "Supplements", Icons.Default.Star)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    
    // Supplement sub-screens (not in bottom nav)
    object Cabinet : Screen("cabinet", "Cabinet", Icons.Default.Star)
    object AddProduct : Screen("add_product", "Add Product", Icons.Default.Add)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.ManualData,
    Screen.Supplements,
    Screen.Settings
)
