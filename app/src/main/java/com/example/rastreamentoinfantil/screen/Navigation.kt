package com.example.rastreamentoinfantil.screen

import com.example.rastreamentoinfantil.MainActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModelFactory
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import kotlin.collections.get

@Composable
fun Navigation(
    activity: MainActivity,
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()
    val firebaseRepository = FirebaseRepository()
    val loginViewModel = ViewModelProvider(
        activity,
        LoginViewModelFactory(firebaseRepository)
    )[LoginViewModel::class.java]

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(loginViewModel, navController)
        }
        composable("register") {
            RegisterScreen(loginViewModel, navController)
        }
        composable("main") {
            MainScreen(mainViewModel)
        }
        composable("mapscreen") {
            MapScreen(Modifier, mainViewModel)
        }
    }
}