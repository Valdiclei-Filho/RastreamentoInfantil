package com.example.rastreamentoinfantil.screen

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Estado reativo para detectar mudanças na autenticação
    val isLoggedIn by loginViewModel.isLoggedIn.observeAsState(initial = false)
    var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var currentUserId by remember { mutableStateOf(currentUser?.uid ?: "") }
    var currentUserEmail by remember { mutableStateOf(currentUser?.email ?: "") }

    // Verificar estado de autenticação quando o componente é criado
    LaunchedEffect(Unit) {
        loginViewModel.checkAuthenticationState()
    }

    // Observar mudanças no estado de login
    LaunchedEffect(isLoggedIn) {
        currentUser = FirebaseAuth.getInstance().currentUser
        currentUserId = currentUser?.uid ?: ""
        currentUserEmail = currentUser?.email ?: ""
        
        Log.d("Navigation", "Estado de login mudou: isLoggedIn=$isLoggedIn, currentUser=$currentUser")
        
        if (!isLoggedIn && currentUser == null) {
            // Usuário não está logado, navegar para login
            navController.navigate(AppDestinations.LOGIN_SCREEN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else if (isLoggedIn && currentUser != null) {
            // Usuário está logado, navegar para mapa
            navController.navigate(AppDestinations.MAP_SCREEN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
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

    NavHost(navController = navController, startDestination = AppDestinations.LOGIN_SCREEN) {

        composable(AppDestinations.LOGIN_SCREEN) {
            println(">>>>>>>>> login screen<<<< ")
            LoginScreen(loginViewModel, navController)
        }
        composable(AppDestinations.REGISTER_SCREEN) {
            RegisterScreen(loginViewModel, navController)
        }
        composable(AppDestinations.MAIN_SCREEN) {
            MainScreen(mainViewModel, navController)
        }

        composable(AppDestinations.MAP_SCREEN) {
            MapScreen(
                modifier = Modifier,
                mainViewModel = mainViewModel,
                navController = navController // Adicionado navController
            )
        }

        composable(AppDestinations.GEOFENCE_CONFIG_SCREEN) {
            // GeofenceConfigScreen(mainViewModel, navController) // Exemplo se você criar esta tela
            // Por enquanto, vazio como no seu original
        }

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

        composable(AppDestinations.FAMILY_SCREEN) {
            FamilyScreen(navController = navController)
        }
    }
}
