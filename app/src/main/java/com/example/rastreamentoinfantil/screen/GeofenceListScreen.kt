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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val geofencesFiltradas = mainViewModel.getActiveGeofencesForUser()
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
            } else if (geofencesFiltradas.isEmpty()) {
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
                    items(geofencesFiltradas, key = { it.id!! }) { geofence ->
                        GeofenceItem(
                            geofence = geofence,
                            isResponsible = isResponsible,
                            diaAtual = "Segunda", // Placeholder, as filtering is removed
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
    diaAtual: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dimensions = rememberResponsiveDimensions()
    val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
    val diasOrdenados = diasSemana.filter { geofence.activeDays.contains(it) }
    val isAtivaHoje = geofence.activeDays.contains(diaAtual) && geofence.isActive
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
            // Nome da geofence
            Text(
                geofence.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (isAtivaHoje) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            // Status
            Text(
                "Status:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )
            Text(
                if (geofence.isActive) "Ativa" else "Inativa",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (geofence.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Dias ativos
            Text(
                "Dias de uso:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )
            if (diasOrdenados.isNotEmpty()) {
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
                                    color = if (dia == diaAtual && isAtivaHoje) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Text(
                                        text = dia,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
            // Raio
            Text(
                "Raio:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )
            Text("${geofence.radius.toInt()} metros", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            // Localização
            Text(
                "Localização:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )
            Text(
                "Lat: ${String.format("%.4f", geofence.coordinates.latitude)}\nLng: ${String.format("%.4f", geofence.coordinates.longitude)}",
                style = MaterialTheme.typography.bodyMedium
            )
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