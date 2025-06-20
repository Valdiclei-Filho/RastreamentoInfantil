package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.rastreamentoinfantil.model.Route
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.viewmodel.MainViewModel.RouteOperationStatus // Importar o sealed interface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    navController: NavController,
    mainViewModel: MainViewModel // Assumindo que será passado ou obtido via hiltViewModel()
) {
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }

    val routes by mainViewModel.routes.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoadingRoutes.collectAsStateWithLifecycle()
    val routeOpStatus by mainViewModel.routeOperationStatus.collectAsStateWithLifecycle()

    val scaffoldState = rememberBottomSheetScaffoldState() // Para Snackbar
    val snackbarHostState = remember { SnackbarHostState() }


    LaunchedEffect(routeOpStatus) {
        when (val status = routeOpStatus) {
            is RouteOperationStatus.Success -> {
                snackbarHostState.showSnackbar(
                    message = status.message,
                    duration = SnackbarDuration.Short
                )
                mainViewModel.clearRouteOperationStatus() // Limpar o status
            }
            is RouteOperationStatus.Error -> {
                snackbarHostState.showSnackbar(
                    message = status.errorMessage,
                    duration = SnackbarDuration.Long,
                    withDismissAction = true
                )
                mainViewModel.clearRouteOperationStatus() // Limpar o status
            }
            else -> { /* Não faz nada para Loading ou Idle aqui */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Minhas Rotas") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("routeCreate") // Navegar para a tela de criação
            }) {
                Icon(Icons.Filled.Add, "Adicionar Rota")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (routes.isEmpty()) {
                Text(
                    "Nenhuma rota definida ainda. Clique no botão '+' para adicionar.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(routes, key = { it.id!! }) { route ->
                        RouteItem(
                            route = route,
                            onEdit = {
                                // Navegar para a tela de edição, passando o ID da rota
                                navController.navigate("routeEdit/${route.id!!}")
                            },
                            onDelete = {
                                // Adicionar diálogo de confirmação antes de deletar
                                mainViewModel.deleteRoute(route.id!!)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RouteItem(
    route: Route,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium)
                // Você pode adicionar mais detalhes aqui, como origem/destino resumidos
                Text("Origem: ${route.origin?.address ?: "Não definido"}", style = MaterialTheme.typography.bodySmall)
                Text("Destino: ${route.destination?.address ?: "Não definido"}", style = MaterialTheme.typography.bodySmall)
                Text("Pontos: ${route.waypoints?.size}", style = MaterialTheme.typography.bodySmall)
                Text(if(route.isActive) "Ativa" else "Inativa", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar Rota")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Deletar Rota")
                }
            }
        }
    }
}