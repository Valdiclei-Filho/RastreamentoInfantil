package com.example.rastreamentoinfantil.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rastreamentoinfantil.MainActivity
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.viewmodel.FamilyViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModelFactory
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth

// ✅ Definições das rotas
object AppDestinations {
    const val LOGIN_SCREEN = "login"
    const val REGISTER_SCREEN = "register"
    const val MAIN_SCREEN = "main"
    const val MAP_SCREEN = "mapscreen"
    const val GEOFENCE_CONFIG_SCREEN = "geofenceconfig"
    const val ROUTE_LIST_SCREEN = "routeList"
    const val ROUTE_CREATE_SCREEN = "routeCreate"
    const val ROUTE_EDIT_SCREEN_BASE = "routeEdit"
    const val ROUTE_EDIT_SCREEN_ARG_ID = "routeId"
    const val ROUTE_EDIT_SCREEN = "$ROUTE_EDIT_SCREEN_BASE/{$ROUTE_EDIT_SCREEN_ARG_ID}"
    const val FAMILY_SCREEN = "family"
}

@Composable
fun Navigation(
    activity: MainActivity,
    mainViewModel: MainViewModel,
) {
    val navController = rememberNavController()
    val firebaseRepository = FirebaseRepository()

    val loginViewModel = ViewModelProvider(
        activity,
        LoginViewModelFactory(firebaseRepository)
    )[LoginViewModel::class.java]

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: ""
    val currentUserEmail = currentUser?.email ?: ""

    println("startDestination")

    println(currentUser)

    val startDestination = if (currentUser != null) {
        AppDestinations.MAP_SCREEN
    } else {
        AppDestinations.LOGIN_SCREEN
    }

    val familyViewModel = ViewModelProvider(
        activity,
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(FamilyViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return FamilyViewModel(
                        firebaseRepository,
                        currentUserId,
                        currentUserEmail
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )[FamilyViewModel::class.java]

    NavHost(navController = navController, startDestination = startDestination) {

        composable(AppDestinations.LOGIN_SCREEN) {
            println(">>>>>>>>> login screen<<<< ")
            LoginScreen(loginViewModel, navController)
        }
        composable(AppDestinations.REGISTER_SCREEN) {
            RegisterScreen(loginViewModel, navController)
        }

        composable(AppDestinations.MAP_SCREEN) {
            MapScreen(
                modifier = Modifier,
                mainViewModel = mainViewModel,
                navController = navController
            )
        }

        // ⚙️ Tela de configuração de geofence (placeholder se não tiver conteúdo ainda)
        composable(AppDestinations.GEOFENCE_CONFIG_SCREEN) {
            // GeofenceConfigScreen(navController) // se desejar implementar
        }

        // 🗺️ Telas de gerenciamento de rotas
        composable(AppDestinations.ROUTE_LIST_SCREEN) {
            RoutesListScreen(navController = navController, mainViewModel = mainViewModel)
        }

        composable(AppDestinations.ROUTE_CREATE_SCREEN) {
            RouteEditScreen(navController = navController, mainViewModel = mainViewModel, routeId = null)
        }

        composable(
            route = AppDestinations.ROUTE_EDIT_SCREEN,
            arguments = listOf(navArgument(AppDestinations.ROUTE_EDIT_SCREEN_ARG_ID) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString(AppDestinations.ROUTE_EDIT_SCREEN_ARG_ID)
            RouteEditScreen(navController = navController, mainViewModel = mainViewModel, routeId = routeId)
        }

        // 👪 Tela de família
        composable(AppDestinations.FAMILY_SCREEN) {
            FamilyScreen(navController = navController)
        }
    }
}
