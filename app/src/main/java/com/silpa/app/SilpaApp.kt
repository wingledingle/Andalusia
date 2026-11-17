package com.silpa.app

import android.net.Uri
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.silpa.feature.bank.BankScreen
import com.silpa.feature.home.HomeScreen
import com.silpa.feature.studio.StudioScreen
import compose.icons.TablerIcons
import compose.icons.tablericons.Home
import compose.icons.tablericons.Photo

private enum class TopLevel(val label: String, val icon: ImageVector) {
    Home("Home", TablerIcons.Home),
    Bank("Image Bank", TablerIcons.Photo),
}

@Composable
fun SilpaApp(incoming: List<Uri>, onIncomingHandled: () -> Unit) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(TopLevel.Home) }
    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { tab = TopLevel.Bank; nav.popBackStack("main", inclusive = false) } }
    NavHost(nav, startDestination = "main") {
        composable("main") {
            // Bottom bar on phones, navigation rail on tablets/foldables (window size class driven).
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    TopLevel.entries.forEach { t ->
                        item(selected = t == tab, onClick = { tab = t }, icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) })
                    }
                },
            ) {
                when (tab) {
                    TopLevel.Home -> HomeScreen(brandMark = R.drawable.silpa_mark, onOpenProject = { id -> nav.navigate("studio/$id") })
                    TopLevel.Bank -> BankScreen(incoming = incoming, onIncomingHandled = onIncomingHandled)
                }
            }
        }
        composable("studio/{id}") { entry ->
            StudioScreen(projectId = requireNotNull(entry.arguments?.getString("id")), onBack = { nav.popBackStack() })
        }
    }
}
