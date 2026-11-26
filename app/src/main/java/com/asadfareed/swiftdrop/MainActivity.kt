package com.asadfareed.swiftdrop

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asadfareed.swiftdrop.ui.SettingsViewModel
import com.asadfareed.swiftdrop.ui.ShopViewModel
import com.asadfareed.swiftdrop.ui.TrackingViewModel
import com.asadfareed.swiftdrop.ui.cart.CartScreen
import com.asadfareed.swiftdrop.ui.home.HomeScreen
import com.asadfareed.swiftdrop.ui.orders.OrdersScreen
import com.asadfareed.swiftdrop.ui.restaurant.RestaurantScreen
import com.asadfareed.swiftdrop.ui.theme.Swift
import com.asadfareed.swiftdrop.ui.theme.SwiftdropTheme
import com.asadfareed.swiftdrop.ui.tracking.TrackingScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SwiftdropApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SwiftdropTheme { SwiftdropRoot() } }
    }
}

@Composable
private fun SwiftdropRoot() {
    val nav = rememberNavController()
    val shop: ShopViewModel = hiltViewModel()
    val settings: SettingsViewModel = hiltViewModel()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == "home" || route == "orders"

    fun tab(target: String) = nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = Swift.Background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Color.White) {
                    listOf(Triple("home", "Home", Icons.Rounded.Home), Triple("orders", "Orders", Icons.AutoMirrored.Rounded.ReceiptLong))
                        .forEach { (r, label, icon) ->
                            NavigationBarItem(
                                selected = route == r,
                                onClick = { tab(r) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Swift.Brand,
                                    selectedTextColor = Swift.Brand,
                                    indicatorColor = Swift.Brand.copy(alpha = 0.12f),
                                ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable("home") {
                HomeScreen(shop, onRestaurant = { nav.navigate("restaurant/$it") }, onCart = { nav.navigate("cart") })
            }
            composable("restaurant/{id}") { backStack ->
                RestaurantScreen(
                    shop,
                    restaurantId = backStack.arguments?.getString("id") ?: return@composable,
                    onBack = { nav.popBackStack() },
                    onCart = { nav.navigate("cart") },
                )
            }
            composable("cart") {
                CartScreen(shop, onBack = { nav.popBackStack() }, onOrderPlaced = { id ->
                    nav.navigate("track/$id") { popUpTo("home") }
                })
            }
            composable("track/{orderId}") {
                val vm: TrackingViewModel = hiltViewModel()
                TrackingScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("orders") {
                OrdersScreen(shop, settings, onTrack = { nav.navigate("track/$it") })
            }
        }
    }
}
