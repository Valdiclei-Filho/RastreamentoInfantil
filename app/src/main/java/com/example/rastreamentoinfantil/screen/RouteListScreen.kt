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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.rastreamentoinfantil.model.Route
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.viewmodel.MainViewModel.RouteOperationStatus // Importar o sealed interface
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    navController: NavController,
    mainViewModel: MainViewModel // Assumindo que será passado ou obtido via hiltViewModel()
) {
    val dimensions = rememberResponsiveDimensions()
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }

    val routes by mainViewModel.routes.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoadingRoutes.collectAsStateWithLifecycle()
    val routeOpStatus by mainViewModel.routeOperationStatus.collectAsStateWithLifecycle()
    val isResponsible by mainViewModel.isResponsible.collectAsStateWithLifecycle()

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
            if (isResponsible) {
                FloatingActionButton(onClick = {
                    navController.navigate("routeCreate") // Navegar para a tela de criação
                }) {
                    Icon(Icons.Filled.Add, "Adicionar Rota")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (routes.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(dimensions.paddingMediumDp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isResponsible) "Nenhuma rota definida ainda. Clique no botão '+' para adicionar."
                        else "Nenhuma rota disponível para você."
                    )
                    if (!isResponsible) {
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                        Text(
                            "Apenas responsáveis podem criar e gerenciar rotas.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(dimensions.paddingMediumDp),
                    verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmallDp)
                ) {
                    items(routes, key = { it.id!! }) { route ->
                        RouteItem(
                            route = route,
                            isResponsible = isResponsible,
                            onEdit = {
                                if (isResponsible) {
                                    // Navegar para a tela de edição, passando o ID da rota
                                    navController.navigate("routeEdit/${route.id!!}")
                                }
                            },
                            onDelete = {
                                if (isResponsible) {
                                    // Adicionar diálogo de confirmação antes de deletar
                                    mainViewModel.deleteRoute(route.id!!)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteItem(
    route: Route,
    isResponsible: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dimensions = rememberResponsiveDimensions()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmallDp),
        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingMediumDp)
        ) {
            // Nome da rota
            Text(
                route.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            // Status
            Text(
                "Status:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                if (route.isActive) "Ativa" else "Inativa",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (route.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Dias ativos
            Text(
                "Dias de uso:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (route.activeDays.isNotEmpty()) {
                val diasSemana = listOf(
                    "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"
                )
                val diasOrdenados = diasSemana.filter { route.activeDays.contains(it) }
                val linhas = diasOrdenados.chunked(4)
                Column(modifier = Modifier.fillMaxWidth()) {
                    linhas.forEach { linha ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            linha.forEach { dia ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Text(
                                        text = dia,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            } else {
                Text("Não definido", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            // Origem
            Text(
                "Origem:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                route.origin?.address ?: "Não definido",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Destino
            Text(
                "Destino:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                route.destination?.address ?: "Não definido",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Pontos intermediários
            Text(
                "Pontos Intermediários:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (route.waypoints.isNullOrEmpty()) {
                Text("Não existe pontos intermediários", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    route.waypoints.forEachIndexed { idx, ponto ->
                        Text(
                            "${idx + 1}. ${ponto.address ?: "(sem endereço)"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            // Botões de ação
            if (isResponsible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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
}