// Novo arquivo: screen/RouteEditScreen.kt
package com.example.rastreamentoinfantil.screen

import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlin.collections.map
import kotlin.collections.forEachIndexed
import com.example.rastreamentoinfantil.model.RoutePoint
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    routeId: String? // Null para criar, não-null para editar
) {
    val scope = rememberCoroutineScope() // Import androidx.compose.runtime.rememberCoroutineScope
    val existingRoute by mainViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeOpStatus by mainViewModel.routeOperationStatus.collectAsStateWithLifecycle()

    var routeName by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.name ?: "") }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // Para simplificar, vamos definir origem e destino como os primeiros e últimos waypoints se existirem
    // Em uma versão mais completa, você teria campos separados ou lógica mais robusta.

    var originPoint by remember(existingRoute) {
        mutableStateOf(existingRoute?.origin?.let { LatLng(it.latitude, it.longitude) })
    }
    var destinationPoint by remember(existingRoute) {
        mutableStateOf(existingRoute?.destination?.let { LatLng(it.latitude, it.longitude) })
    }
    var isRouteActive by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.isActive ?: false) }

    var displayableEncodedPolyline by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Carregar detalhes da rota se estiver editando
    LaunchedEffect(routeId) {
        if (routeId != null) {
            mainViewModel.loadRouteDetails(routeId)
        } else {
            // Se for criação, limpar qualquer rota selecionada anteriormente
            //mainViewModel.clearSelectedRoute() // Você precisaria adicionar essa função no ViewModel
            routeName = ""
            routePoints = emptyList()
            originPoint = null
            destinationPoint = null
            isRouteActive = true
            displayableEncodedPolyline = null
        }
    }

    // Atualizar campos quando existingRoute carregar (para edição)
    LaunchedEffect(existingRoute, routeId) { // Adicione routeId aqui se ele influenciar o efeito
        if (existingRoute != null && routeId != null) { // Modo Edição com dados carregados
            val routeData = existingRoute!! // Sabemos que não é nulo aqui
            routeName = routeData.name
            originPoint = routeData.origin?.let { coord -> LatLng(coord.latitude, coord.longitude) }
            destinationPoint = routeData.destination?.let { coord -> LatLng(coord.latitude, coord.longitude) }
            routePoints = routeData.waypoints?.map { coord -> LatLng(coord.latitude, coord.longitude) } ?: emptyList()
            isRouteActive = routeData.isActive
            displayableEncodedPolyline = routeData.encodedPolyline // Carrega o polyline existente para exibição
        } else if (routeId == null) { // Modo Criação (ou existingRoute é nulo e é criação)
            routeName = ""
            originPoint = null
            destinationPoint = null
            routePoints = emptyList()
            isRouteActive = false
            displayableEncodedPolyline = null
        }
        // Se existingRoute for nulo E routeId NÃO for nulo, significa que estamos esperando dados de edição que ainda não chegaram.
        // O LaunchedEffect(routeId) { mainViewModel.loadRouteDetails(routeId) } cuidará de carregar os dados,
        // e então este LaunchedEffect(existingRoute, routeId) será reativado.
    }

    LaunchedEffect(routeOpStatus) {
        when (val status = routeOpStatus) {
            is MainViewModel.RouteOperationStatus.Success -> {
                snackbarHostState.showSnackbar(status.message, duration = SnackbarDuration.Short)
                mainViewModel.clearRouteOperationStatus()
                navController.popBackStack() // Voltar para a lista após salvar
            }
            is MainViewModel.RouteOperationStatus.Error -> {
                snackbarHostState.showSnackbar(status.errorMessage, duration = SnackbarDuration.Long, withDismissAction = true)
                mainViewModel.clearRouteOperationStatus()
            }
            else -> {}
        }
    }

    val cameraPositionState = rememberCameraPositionState() // Você pode querer definir uma posição inicial

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (routeId == null) "Criar Nova Rota" else "Editar Rota") },
                actions = {
                    IconButton(onClick = {
                        val finalOrigin = originPoint?.let { RoutePoint(latitude = it.latitude, longitude = it.longitude, address = "Origem") } // Placeholder para endereço
                        val finalDestination = destinationPoint?.let { RoutePoint(latitude = it.latitude, longitude = it.longitude, address = "Destino") } // Placeholder
                        val finalWaypoints = routePoints.map { RoutePoint(latitude = it.latitude, longitude = it.longitude) }

                        if (routeName.isNotBlank() && finalOrigin != null && finalDestination != null) {
                            val pointsHaveChanged = routeId == null || // É uma nova rota
                                    existingRoute?.origin?.let { LatLng(it.latitude, it.longitude) } != originPoint ||
                                    existingRoute?.destination?.let { LatLng(it.latitude, it.longitude) } != destinationPoint ||
                                    (existingRoute?.waypoints?.map { LatLng(it.latitude, it.longitude) } ?: emptyList()) != routePoints

                            if (pointsHaveChanged) {
                                // ----> CHAMADA A createRouteWithDirections <----
                                mainViewModel.createRouteWithDirections(
                                    name = routeName,
                                    originPoint = finalOrigin,
                                    destinationPoint = finalDestination,
                                    waypointsList = finalWaypoints.takeIf { it.isNotEmpty() }, // Passa null se vazio
                                    isActive = isRouteActive
                                    // routeColor pode ser adicionado como parâmetro aqui se você tiver um seletor de cor na UI
                                )
                            } else if (routeId != null && existingRoute != null) {
                            // Pontos não mudaram, mas outros campos (nome, isActive) podem ter mudado.
                            // Salva a rota com o encodedPolyline existente.
                            val routeToUpdate = existingRoute!!.copy( // Sabemos que existingRoute não é nulo aqui
                                name = routeName,
                                isActive = isRouteActive,
                                // Certifique-se que o encodedPolyline existente é mantido
                                encodedPolyline = existingRoute!!.encodedPolyline
                            )
                            mainViewModel.addOrUpdateRoute(routeToUpdate) // Assume que addOrUpdateRoute não recalcula o polyline
                        } else {
                            // Caso improvável: routeId é nulo (nova rota), mas pointsHaveChanged é falso.
                            // Isso não deveria acontecer com a lógica acima.
                            // Ainda assim, como fallback, chamar createRouteWithDirections.
                            mainViewModel.createRouteWithDirections(
                                name = routeName,
                                originPoint = finalOrigin,
                                destinationPoint = finalDestination,
                                waypointsList = finalWaypoints.takeIf { it.isNotEmpty() },
                                isActive = isRouteActive
                            )
                        }
                        } else {
                            scope.launch { // Use o scope aqui
                                snackbarHostState.showSnackbar(
                                    "Nome, origem e destino são obrigatórios.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Check, "Salvar Rota")
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("Nome da Rota") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Rota Ativa:", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isRouteActive,
                    onCheckedChange = { isRouteActive = it }
                )
            }


            Spacer(Modifier.height(16.dp))
            Text("Definir no Mapa:", style = MaterialTheme.typography.titleMedium)
            Text("Clique no mapa para definir Origem, depois Destino, e então os Pontos Intermediários.", style = MaterialTheme.typography.bodySmall)

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)) { // Altura fixa para o mapa, ajuste conforme necessário
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true),
                    onMapClick = { latLng ->
                        if (originPoint == null) {
                            originPoint = latLng
                        } else if (destinationPoint == null) {
                            destinationPoint = latLng
                        } else {
                            routePoints = routePoints + latLng // Adiciona como waypoint
                        }
                    }
                ) {
                    originPoint?.let {
                        Marker(state = MarkerState(position = it), title = "Origem")
                    }
                    destinationPoint?.let {
                        Marker(state = MarkerState(position = it), title = "Destino", icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_BLUE))
                    }
                    routePoints.forEachIndexed { index, point ->
                        Marker(state = MarkerState(position = point), title = "Ponto ${index + 1}", icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN))
                    }

                    displayableEncodedPolyline?.let { encodedPath ->
                        val decodedPath: List<LatLng> = try {
                            com.google.maps.android.PolyUtil.decode(encodedPath)
                        } catch (e: Exception) {
                            Log.e("RouteEditScreen", "Falha ao decodificar polyline: $encodedPath", e)
                            emptyList()
                        }
                        if (decodedPath.isNotEmpty()) {
                            Polyline(points = decodedPath, color = MaterialTheme.colorScheme.primary, width = 8f)
                        }
                    } ?: run {
                        // Fallback: Desenhar polylines retas entre os pontos definidos manualmente
                        // como feedback visual imediato durante a edição.
                        val allPointsForPolyline = mutableListOf<LatLng>()
                        originPoint?.let { allPointsForPolyline.add(it) }
                        allPointsForPolyline.addAll(routePoints)
                        destinationPoint?.let { allPointsForPolyline.add(it) }

                        if (allPointsForPolyline.size >= 2) {
                            Polyline(points = allPointsForPolyline, color = MaterialTheme.colorScheme.secondary, width = 5f)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { // Botão para limpar os pontos do mapa
                originPoint = null
                destinationPoint = null
                routePoints = emptyList()
                displayableEncodedPolyline = null
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar Pontos do Mapa")
            }

            // Lista de pontos (opcional, para visualização/remoção manual)
            // Spacer(Modifier.height(16.dp))
            // Text("Pontos da Rota:", style = MaterialTheme.typography.titleMedium)
            // LazyColumn(modifier = Modifier.weight(1f)) {
            //     originPoint?.let { item { PointItem(point = it, type = "Origem") { originPoint = null } } }
            //     destinationPoint?.let { item { PointItem(point = it, type = "Destino") { destinationPoint = null } } }
            //     itemsIndexed(routePoints) { index, point ->
            //         PointItem(point = point, type = "Ponto ${index + 1}") {
            //             routePoints = routePoints.toMutableList().apply { removeAt(index) }
            //         }
            //     }
            // }
        }
    }
}

// @Composable
// fun PointItem(point: LatLng, type: String, onRemove: () -> Unit) {
//     Row(
//         modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
//         verticalAlignment = Alignment.CenterVertically,
//         horizontalArrangement = Arrangement.SpaceBetween
//     ) {
//         Text("$type: (${String.format("%.4f", point.latitude)}, ${String.format("%.4f", point.longitude)})")
//         IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
//             Icon(Icons.Default.DeleteForever, "Remover Ponto")
//         }
//     }
// }