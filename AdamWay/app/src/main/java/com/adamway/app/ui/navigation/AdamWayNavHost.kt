package com.adamway.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adamway.app.AdamWayApplication
import com.adamway.app.ui.screens.AddEditAddressScreen
import com.adamway.app.ui.screens.AddressNotesScreen
import com.adamway.app.ui.screens.HomeScreen
import com.adamway.app.ui.screens.JourneyScreen
import com.adamway.app.ui.screens.QueueScreen
import com.adamway.app.ui.screens.SettingsScreen
import com.adamway.app.viewmodel.AdamWayViewModelFactory
import com.adamway.app.viewmodel.AddressViewModel
import com.adamway.app.viewmodel.JourneyViewModel
import com.adamway.app.viewmodel.SavedAddressViewModel
import com.adamway.app.viewmodel.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val QUEUE = "queue"
    const val ADD_EDIT = "add_edit"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val ADDRESS_NOTES = "address_notes"
    const val SAVED_ADDRESS_ID_ARG = "savedAddressId"
}

@Composable
fun AdamWayNavHost(navController: NavHostController = rememberNavController()) {
    val app = LocalContext.current.applicationContext as AdamWayApplication
    val factory = remember(app) { AdamWayViewModelFactory(app) }

    val addressViewModel: AddressViewModel = viewModel(factory = factory)
    val journeyViewModel: JourneyViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val savedAddressViewModel: SavedAddressViewModel = viewModel(factory = factory)

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                addressViewModel = addressViewModel,
                onManageQueue = { navController.navigate(Routes.QUEUE) },
                onBeginJourney = { navController.navigate(Routes.JOURNEY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.QUEUE) {
            QueueScreen(
                addressViewModel = addressViewModel,
                onBack = { navController.popBackStack() },
                onAddAddress = { navController.navigate(Routes.ADD_EDIT) },
                onEditAddress = { navController.navigate(Routes.ADD_EDIT) },
                onOpenNotes = { savedAddressId ->
                    navController.navigate("${Routes.ADDRESS_NOTES}/$savedAddressId")
                },
            )
        }
        composable(Routes.ADD_EDIT) {
            AddEditAddressScreen(
                addressViewModel = addressViewModel,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.JOURNEY) {
            JourneyScreen(
                journeyViewModel = journeyViewModel,
                autoBegin = true,
                onFinished = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                addressViewModel = addressViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.ADDRESS_NOTES}/{${Routes.SAVED_ADDRESS_ID_ARG}}",
            arguments = listOf(navArgument(Routes.SAVED_ADDRESS_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val savedAddressId = backStackEntry.arguments?.getLong(Routes.SAVED_ADDRESS_ID_ARG) ?: return@composable
            AddressNotesScreen(
                savedAddressViewModel = savedAddressViewModel,
                savedAddressId = savedAddressId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
