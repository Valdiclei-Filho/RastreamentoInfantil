package com.example.rastreamentoinfantil.screen

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rastreamentoinfantil.MainActivity
import com.example.rastreamentoinfantil.RastreamentoInfantilApp
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
    const val GEOFENCE_LIST_SCREEN = "geofenceList"
    const val GEOFENCE_CREATE_SCREEN = "geofenceCreate"
    const val GEOFENCE_EDIT_SCREEN_BASE = "geofenceEdit"
    const val GEOFENCE_EDIT_SCREEN_ARG_ID = "geofenceId"
    const val GEOFENCE_EDIT_SCREEN = "$GEOFENCE_EDIT_SCREEN_BASE/{$GEOFENCE_EDIT_SCREEN_ARG_ID}"
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

    // Usar estado global da aplicação diretamente
    var isLoggedIn by remember { 
        val initialState = RastreamentoInfantilApp.isUserLoggedIn
        Log.d("Navigation", "Estado inicial - isLoggedIn: $initialState")
        mutableStateOf(initialState) 
    }
    var isCheckingAuth by remember { 
        val initialState = !RastreamentoInfantilApp.isAuthChecked
        Log.d("Navigation", "Estado inicial - isCheckingAuth: $initialState")
        mutableStateOf(initialState) 
    }
    var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var currentUserId by remember { mutableStateOf(currentUser?.uid ?: "") }
    var currentUserEmail by remember { mutableStateOf(currentUser?.email ?: "") }

    // Determinar a tela inicial baseada no estado de autenticação
    val startDestination = remember(isLoggedIn, isCheckingAuth) {
        when {
            isCheckingAuth -> AppDestinations.LOGIN_SCREEN // Mostrar loading na tela de login
            isLoggedIn && currentUser != null -> AppDestinations.MAP_SCREEN
            else -> AppDestinations.LOGIN_SCREEN
        }
    }

    // Observar mudanças no estado global da aplicação
    LaunchedEffect(Unit) {
        Log.d("Navigation", "=== NAVIGATION INICIADA ===")
        Log.d("Navigation", "Estado atual da aplicação:")
        Log.d("Navigation", "- isAuthChecked: ${RastreamentoInfantilApp.isAuthChecked}")
        Log.d("Navigation", "- isUserLoggedIn: ${RastreamentoInfantilApp.isUserLoggedIn}")
        Log.d("Navigation", "- currentUserId: ${RastreamentoInfantilApp.currentUserId}")
        
        // Atualizar estado inicial
        isLoggedIn = RastreamentoInfantilApp.isUserLoggedIn
        isCheckingAuth = !RastreamentoInfantilApp.isAuthChecked
        currentUser = FirebaseAuth.getInstance().currentUser
        currentUserId = currentUser?.uid ?: ""
        currentUserEmail = currentUser?.email ?: ""
        
        Log.d("Navigation", "Estado inicial atualizado: isLoggedIn=$isLoggedIn, isCheckingAuth=$isCheckingAuth")
        
        RastreamentoInfantilApp.addAuthStateCallback {
            val newIsLoggedIn = RastreamentoInfantilApp.isUserLoggedIn
            val newIsCheckingAuth = !RastreamentoInfantilApp.isAuthChecked
            
            Log.d("Navigation", "=== CALLBACK EXECUTADO ===")
            Log.d("Navigation", "Novo estado: isLoggedIn=$newIsLoggedIn, isCheckingAuth=$newIsCheckingAuth")
            
            isLoggedIn = newIsLoggedIn
            isCheckingAuth = newIsCheckingAuth
            currentUser = FirebaseAuth.getInstance().currentUser
            currentUserId = currentUser?.uid ?: ""
            currentUserEmail = currentUser?.email ?: ""
            
            Log.d("Navigation", "Estado atualizado: isLoggedIn=$isLoggedIn, isCheckingAuth=$isCheckingAuth")
        }
    }

    // Observar mudanças no estado de login
    LaunchedEffect(isLoggedIn, isCheckingAuth) {
        Log.d("Navigation", "=== LAUNCHEDEFFECT EXECUTADO ===")
        Log.d("Navigation", "Estado de login mudou: isLoggedIn=$isLoggedIn, isCheckingAuth=$isCheckingAuth, currentUser=$currentUser")
        
        // Só navegar após a verificação inicial de autenticação estar completa
        if (!isCheckingAuth) {
            Log.d("Navigation", "Verificação de autenticação concluída, decidindo navegação...")
            
            if (!isLoggedIn && currentUser == null) {
                Log.d("Navigation", "Usuário não está logado, navegando para LOGIN_SCREEN")
                // Usuário não está logado, navegar para login
                navController.navigate(AppDestinations.LOGIN_SCREEN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            } else if (isLoggedIn && currentUser != null) {
                Log.d("Navigation", "Usuário está logado, navegando para MAP_SCREEN")
                // Usuário está logado, navegar para mapa
                navController.navigate(AppDestinations.MAP_SCREEN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                Log.d("Navigation", "Estado inconsistente: isLoggedIn=$isLoggedIn, currentUser=$currentUser")
            }
    } else {
            Log.d("Navigation", "Ainda verificando autenticação...")
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

    Log.d("Navigation", "Iniciando NavHost com startDestination: $startDestination")

    NavHost(navController = navController, startDestination = startDestination) {

        composable(AppDestinations.LOGIN_SCREEN) {
            Log.d("Navigation", "Renderizando LOGIN_SCREEN - isCheckingAuth: $isCheckingAuth")
            if (isCheckingAuth) {
                // Mostrar loading enquanto verifica autenticação
                Log.d("Navigation", "Mostrando loading...")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(50.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Log.d("Navigation", "Mostrando tela de login...")
            println(">>>>>>>>> login screen<<<< ")
            LoginScreen(loginViewModel, navController)
            }
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
                navController = navController
            )
        }

        composable(AppDestinations.GEOFENCE_CONFIG_SCREEN) {
            // GeofenceConfigScreen(mainViewModel, navController)
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

        // Novas rotas para geofence
        composable(AppDestinations.GEOFENCE_LIST_SCREEN) {
            GeofenceListScreen(navController = navController, mainViewModel = mainViewModel)
        }

        composable(AppDestinations.GEOFENCE_CREATE_SCREEN) {
            GeofenceEditScreen(navController = navController, mainViewModel = mainViewModel, geofenceId = null)
        }

        composable(
            route = AppDestinations.GEOFENCE_EDIT_SCREEN,
            arguments = listOf(navArgument(AppDestinations.GEOFENCE_EDIT_SCREEN_ARG_ID) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val geofenceId = backStackEntry.arguments?.getString(AppDestinations.GEOFENCE_EDIT_SCREEN_ARG_ID)
            GeofenceEditScreen(navController = navController, mainViewModel = mainViewModel, geofenceId = geofenceId)
        }
    }
}
