package com.example.bajeti.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bajeti.auth.AppAuthState
import com.example.bajeti.auth.AuthViewModel
import com.example.bajeti.ui.screens.LoginScreen
import com.example.bajeti.ui.screens.OnboardingScreen
import com.example.bajeti.ui.screens.OverviewScreen
import com.example.bajeti.ui.screens.SenderDetailScreen
import com.example.bajeti.ui.screens.SettingsScreen
import com.example.bajeti.ui.screens.SmsImportScreen
import com.example.bajeti.ui.screens.TrendsScreen
import com.example.bajeti.ui.theme.TealPrimary

private sealed class NavRoute(val route: String) {
    object Login : NavRoute("login")
    object Main : NavRoute("main")
}

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Overview : BottomTab("overview", "Overview", Icons.Filled.Dashboard)
    object Transactions : BottomTab("trends", "Transactions", Icons.Filled.ReceiptLong)
    object SmsImport : BottomTab("sms_import", "SMS Import", Icons.Filled.Sms)
    object Settings : BottomTab("settings", "Settings", Icons.Filled.Settings)
}

private val bottomTabs = listOf(
    BottomTab.Overview,
    BottomTab.Transactions,
    BottomTab.SmsImport,
    BottomTab.Settings,
)

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val authState by authViewModel.appAuthState.collectAsStateWithLifecycle()

    when (authState) {
        AppAuthState.Loading -> {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary)
            }
        }
        AppAuthState.SignedOut -> {
            LoginScreen(viewModel = authViewModel)
        }
        AppAuthState.SignedIn -> {
            val context = LocalContext.current
            var onboardingDone by remember {
                mutableStateOf(
                    context.getSharedPreferences("bajeti_settings", Context.MODE_PRIVATE)
                        .getBoolean("onboarding_done", false)
                )
            }
            if (onboardingDone) {
                MainScreen()
            } else {
                OnboardingScreen(onComplete = { onboardingDone = true })
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 4.dp,
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                unselectedIconColor = Color(0xFF9CA3AF),
                                unselectedTextColor = Color(0xFF9CA3AF),
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomTab.Overview.route) {
                OverviewScreen(onSeeAll = {
                    navController.navigate(BottomTab.Transactions.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(BottomTab.SmsImport.route) {
                SmsImportScreen(
                    onSenderClick = { sender ->
                        navController.navigate("sms_sender/${Uri.encode(sender)}")
                    }
                )
            }
            composable(BottomTab.Transactions.route) { TrendsScreen() }
            composable(BottomTab.Settings.route) { SettingsScreen() }
            composable("sms_sender/{sender}") { backStackEntry ->
                val sender = Uri.decode(backStackEntry.arguments?.getString("sender") ?: "")
                SenderDetailScreen(
                    sender = sender,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
