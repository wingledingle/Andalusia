package com.silpa.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.silpa.feature.bank.BankScreen
import com.silpa.feature.home.HomeScreen
import com.silpa.feature.studio.StudioScreen

private enum class TopLevel(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Bank("Image Bank", Icons.Outlined.Collections, Icons.Filled.Collections),
}

@Composable
fun SilpaApp() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "main") {
        composable("main") {
            var tab by rememberSaveable { mutableStateOf(TopLevel.Home) }
            // Bottom bar on phones, navigation rail on tablets/foldables, chosen by window size class.
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    TopLevel.entries.forEach { t ->
                        item(
                            selected = t == tab,
                            onClick = { tab = t },
                            icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                },
            ) {
                when (tab) {
                    TopLevel.Home -> HomeScreen(
                        brandMark = R.drawable.silpa_mark,
                        onNewCanvas = { w, h -> nav.navigate("studio/$w/$h") },
                    )
                    TopLevel.Bank -> BankScreen()
                }
            }
        }
        composable(
            "studio/{w}/{h}",
            arguments = listOf(navArgument("w") { type = NavType.IntType }, navArgument("h") { type = NavType.IntType }),
        ) { entry ->
            val args = requireNotNull(entry.arguments)
            StudioScreen(
                canvasWidth = args.getInt("w"),
                canvasHeight = args.getInt("h"),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
