package app.routemate.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.routemate.R
import app.routemate.ui.find.FindScreen
import app.routemate.ui.offer.OfferScreen
import app.routemate.ui.profile.ProfileScreen
import app.routemate.ui.ride.RideDetailScreen
import app.routemate.ui.signin.SignInScreen
import app.routemate.ui.trips.TripsScreen
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow

private sealed class Tab(val route: String, val labelRes: Int) {
    data object Find : Tab("find", R.string.tab_find)
    data object Offer : Tab("offer", R.string.tab_offer)
    data object Trips : Tab("trips", R.string.tab_trips)
    data object Profile : Tab("profile", R.string.tab_profile)
}

private val TABS = listOf(Tab.Find, Tab.Offer, Tab.Trips, Tab.Profile)

@Composable
fun RootNav(
    pendingRideIdFlow: MutableStateFlow<String?> = MutableStateFlow(null),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Tab.Find.route
    val showBar = TABS.any { it.route == currentRoute }

    // Notification deeplinks (routemate://ride/{id}) land here. Wait until
    // we're off the sign-in screen so a signed-out user actually authenticates
    // first; clear the flow after navigating to make this idempotent.
    val pendingRideId by pendingRideIdFlow.collectAsState()
    LaunchedEffect(pendingRideId, currentRoute) {
        val id = pendingRideId ?: return@LaunchedEffect
        if (currentRoute != "signin") {
            nav.navigate("ride/$id")
            pendingRideIdFlow.value = null
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Find -> Icons.Outlined.Search
                                        Tab.Offer -> Icons.Outlined.AddCircle
                                        Tab.Trips -> Icons.Outlined.DirectionsCar
                                        Tab.Profile -> Icons.Outlined.Person
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = nav,
            startDestination = "signin",
            modifier = Modifier.padding(padding),
        ) {
            composable("signin") { SignInScreen(onSignedIn = { nav.navigate(Tab.Find.route) { popUpTo("signin") { inclusive = true } } }) }
            composable(Tab.Find.route) { FindScreen() }
            composable(Tab.Offer.route) { OfferScreen() }
            composable(Tab.Trips.route) {
                TripsScreen(
                    onOpenRide = { id -> nav.navigate("ride/$id") },
                    onOpenRideRating = { id, target ->
                        nav.navigate("ride/$id?rateTarget=$target")
                    },
                )
            }
            composable(Tab.Profile.route) {
                ProfileScreen(onSignedOut = { nav.navigate("signin") { popUpTo(0) } })
            }
            composable(
                route = "ride/{id}?rateTarget={rateTarget}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("rateTarget") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                RideDetailScreen(
                    rideId = entry.arguments?.getString("id") ?: return@composable,
                    rateTargetUserId = entry.arguments?.getString("rateTarget"),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
