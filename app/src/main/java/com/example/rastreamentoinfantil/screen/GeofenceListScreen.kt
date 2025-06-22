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
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceListScreen(
    navController: NavController,
    mainViewModel: MainViewModel
) {
    val dimensions = rememberResponsiveDimensions()
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }

    val geofences by mainViewModel.geofences.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoadingGeofences.collectAsStateWithLifecycle()
    val geofenceOpStatus by mainViewModel.geofenceOperationStatus.collectAsStateWithLifecycle()
    val isResponsible by mainViewModel.isResponsible.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(geofenceOpStatus) {
        when (val status = geofenceOpStatus) {
            is MainViewModel.GeofenceOperationStatus.Success -> {
                snackbarHostState.showSnackbar(
                    message = status.message,
                    duration = SnackbarDuration.Short
                )
                mainViewModel.clearGeofenceOperationStatus()
            }
            is MainViewModel.GeofenceOperationStatus.Error -> {
                snackbarHostState.showSnackbar(
                    message = status.errorMessage,
                    duration = SnackbarDuration.Long,
                    withDismissAction = true
                )
                mainViewModel.clearGeofenceOperationStatus()
            }
            else -> { /* Não faz nada para Loading ou Idle aqui */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Minhas Áreas Seguras") })
        },
        floatingActionButton = {
            if (isResponsible) {
                FloatingActionButton(onClick = {
                    navController.navigate("geofenceCreate")
                }) {
                    Icon(Icons.Filled.Add, "Adicionar Área Segura")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (geofences.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(dimensions.paddingMediumDp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isResponsible) "Nenhuma área segura definida ainda. Clique no botão '+' para adicionar."
                        else "Nenhuma área segura disponível para você."
                    )
                    if (!isResponsible) {
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                        Text(
                            "Apenas responsáveis podem criar e gerenciar áreas seguras.",
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
                    items(geofences, key = { it.id!! }) { geofence ->
                        GeofenceItem(
                            geofence = geofence,
                            isResponsible = isResponsible,
                            onEdit = {
                                if (isResponsible) {
                                    navController.navigate("geofenceEdit/${geofence.id!!}")
                                }
                            },
                            onDelete = {
                                if (isResponsible) {
                                    mainViewModel.deleteGeofence(geofence.id!!)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GeofenceItem(
    geofence: Geofence,
    isResponsible: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dimensions = rememberResponsiveDimensions()
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingMediumDp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(geofence.name, style = MaterialTheme.typography.titleMedium)
                Text("Raio: ${geofence.radius}m", style = MaterialTheme.typography.bodySmall)
                Text("Lat: ${String.format("%.4f", geofence.coordinates.latitude)}", style = MaterialTheme.typography.bodySmall)
                Text("Lng: ${String.format("%.4f", geofence.coordinates.longitude)}", style = MaterialTheme.typography.bodySmall)
                Text(if(geofence.isActive) "Ativa" else "Inativa", style = MaterialTheme.typography.bodySmall)
            }
            if (isResponsible) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar Área Segura")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Deletar Área Segura")
                    }
                }
            }
        }
    }
} 