package com.example.rastreamentoinfantil.screen

import com.example.rastreamentoinfantil.MainActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType // Importar NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument // Importar navArgument
import com.example.rastreamentoinfantil.repository.FirebaseRepository
// Removido GeofenceViewModel se não estiver sendo usado diretamente aqui.
// import com.example.rastreamentoinfantil.viewmodel.GeofenceViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModelFactory
import com.example.rastreamentoinfantil.viewmodel.MainViewModel

// Definições das rotas para clareza e reutilização
object AppDestinations {
    const val LOGIN_SCREEN = "login"
    const val REGISTER_SCREEN = "register"
    const val MAIN_SCREEN = "main" // Assumindo que esta é a tela que pode levar ao MapScreen ou RoutesListScreen
    const val MAP_SCREEN = "mapscreen"
    const val GEOFENCE_CONFIG_SCREEN = "geofenceconfig" // Manteve, mas sem conteúdo ainda

    // Novas rotas para gerenciamento de Rotas (trajetos)
    const val ROUTE_LIST_SCREEN = "routeList"
    const val ROUTE_CREATE_SCREEN = "routeCreate" // Para criar uma nova rota
    const val ROUTE_EDIT_SCREEN_BASE = "routeEdit"
    const val ROUTE_EDIT_SCREEN_ARG_ID = "routeId"
    const val ROUTE_EDIT_SCREEN = "$ROUTE_EDIT_SCREEN_BASE/{$ROUTE_EDIT_SCREEN_ARG_ID}" // Para editar uma rota existente
}

@Composable
fun Navigation(
    activity: MainActivity,
    mainViewModel: MainViewModel,
) {
    val navController = rememberNavController()
    // loginViewModel é instanciado aqui, mas passado para as telas de login/registro.
    // mainViewModel é passado para telas que precisam dele após o login.
    val firebaseRepository = FirebaseRepository() // Considere injeção de dependência para o repositório
    val loginViewModel = ViewModelProvider(
        activity,
        LoginViewModelFactory(firebaseRepository) // Certifique-se que LoginViewModelFactory está correta
    )[LoginViewModel::class.java]

    NavHost(navController = navController, startDestination = AppDestinations.LOGIN_SCREEN) {
        composable(AppDestinations.LOGIN_SCREEN) {
            LoginScreen(loginViewModel, navController)
        }
        composable(AppDestinations.REGISTER_SCREEN) {
            RegisterScreen(loginViewModel, navController)
        }
        composable(AppDestinations.MAIN_SCREEN) {
            // MainScreen agora precisa de navController para ir para MapScreen ou RoutesListScreen
            MainScreen(mainViewModel/*, navController*/)
        }
        composable(AppDestinations.MAP_SCREEN) {
            // MapScreen também pode precisar do navController se tiver que navegar para outro lugar,
            // como a tela de configuração de geofence ou edição de rota a partir do mapa.
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

        // --- Novas telas para Gerenciamento de Rotas ---
        composable(AppDestinations.ROUTE_LIST_SCREEN) {
            RoutesListScreen(
                navController = navController,
                mainViewModel = mainViewModel
            )
        }

        composable(AppDestinations.ROUTE_CREATE_SCREEN) {
            RouteEditScreen(
                navController = navController,
                mainViewModel = mainViewModel,
                routeId = null // Indica que estamos criando uma nova rota
            )
        }

        composable(
            route = AppDestinations.ROUTE_EDIT_SCREEN,
            arguments = listOf(navArgument(AppDestinations.ROUTE_EDIT_SCREEN_ARG_ID) {
                type = NavType.StringType
                // nullable = true // Defina como true se um ID nulo for um estado válido,
                // mas para edição, geralmente esperamos um ID.
                // Se for para criar quando nulo, então a rota ROUTE_CREATE_SCREEN é mais explícita.
            })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString(AppDestinations.ROUTE_EDIT_SCREEN_ARG_ID)
            // Se routeId for nulo aqui, pode indicar um problema de navegação ou
            // você pode querer redirecionar para a tela de criação ou mostrar um erro.
            // No entanto, a definição da rota espera um routeId.
            RouteEditScreen(
                navController = navController,
                mainViewModel = mainViewModel,
                routeId = routeId // Passa o ID da rota para edição
            )
        }
    }
}