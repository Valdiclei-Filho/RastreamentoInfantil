package com.example.rastreamentoinfantil.screen

import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.maps.android.compose.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.model.RoutePoint
import com.example.rastreamentoinfantil.screen.AppDestinations.ROUTE_LIST_SCREEN
import com.example.rastreamentoinfantil.screen.AppDestinations.FAMILY_SCREEN
import com.example.rastreamentoinfantil.model.Route
import com.example.rastreamentoinfantil.screen.AppDestinations.LOGIN_SCREEN
import com.google.maps.android.PolyUtil
import java.util.UUID
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalContext
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.viewmodel.LoginViewModelFactory
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.MainActivity
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    navController: NavHostController
) {
    val dimensions = rememberResponsiveDimensions()
    val scope = rememberCoroutineScope()
    
    // Sincronizar usuário logado ao abrir a tela
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }

    // Criar o LoginViewModel no contexto @Composable
    val loginViewModel = ViewModelProvider(
        LocalContext.current as MainActivity,
        LoginViewModelFactory(FirebaseRepository())
    )[LoginViewModel::class.java]

    val currentLocation by mainViewModel.currentLocation.collectAsState()
    val currentGeofence by mainViewModel.geofenceArea.collectAsState()
    val isInsideGeofence by mainViewModel.isUserInsideGeofence.collectAsState()
    val routes by mainViewModel.routes.collectAsState()
    val isLoadingRoutes by mainViewModel.isLoadingRoutes.collectAsState()
    val isResponsible by mainViewModel.isResponsible.collectAsStateWithLifecycle()

    // Observar geofences ativas
    val geofences by mainViewModel.geofences.collectAsStateWithLifecycle()
    val activeGeofences = remember(geofences) {
        mainViewModel.getActiveGeofencesForUser()
    }
    
    // Observar status de cada geofence
    val geofenceStatusMap by mainViewModel.geofenceStatusMap.collectAsStateWithLifecycle()

    var isEditingGeofence by remember { mutableStateOf(false) }
    var newGeofenceCenter by remember { mutableStateOf<LatLng?>(null) }
    var newGeofenceRadius by remember { mutableStateOf(100f) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }

    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            mainViewModel.loadUserRoutes(currentUserId)
        }
    }

    val defaultLocation = LatLng(-23.550520, -46.633308) // São Paulo como padrão
    val initialZoom = 15f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, initialZoom)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            if (!isEditingGeofence && cameraPositionState.cameraMoveStartedReason != CameraMoveStartedReason.GESTURE) {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(userLatLng, cameraPositionState.position.zoom),
                    durationMs = 1000
                )
            }
        }
    }

    LaunchedEffect(currentGeofence) {
        if (!isEditingGeofence) {
            newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
            newGeofenceRadius = currentGeofence?.radius ?: 100f
        }
    }

    // Filtrar rotas ativas para o dia atual
    val activeRoutesForToday = remember(routes) {
        mainViewModel.getActiveRoutesForToday()
    }

    // Função auxiliar para parsear a cor da rota de forma segura
    @Composable
    fun rememberParsedRouteColor(routeColorString: String?, defaultColorValue: String = "#3F51B5"): Color {
        return remember(routeColorString) {
            try {
                Color(android.graphics.Color.parseColor(routeColorString ?: defaultColorValue))
            } catch (e: Exception) {
                Log.w("MapScreen", "Cor inválida para rota: $routeColorString", e)
                Color(android.graphics.Color.parseColor(defaultColorValue))
            }
        }
    }

    val dependentsLocations by mainViewModel.dependentsLocations.collectAsStateWithLifecycle()
    val dependentsInfo by mainViewModel.dependentsInfo.collectAsStateWithLifecycle()
    val currentUser = mainViewModel.currentUser.value
    val familyId = currentUser?.familyId

    // Iniciar atualização periódica das localizações dos dependentes ao abrir a tela (apenas para responsáveis)
    LaunchedEffect(isResponsible, familyId) {
        if (isResponsible && familyId != null) {
            mainViewModel.startDependentsLocationUpdates(familyId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão de geofence
                        IconButton(
                            onClick = { navController.navigate(AppDestinations.GEOFENCE_LIST_SCREEN) }
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Áreas Seguras")
                        }
                        
                        // Botão de rotas
                        IconButton(
                            onClick = { navController.navigate(ROUTE_LIST_SCREEN) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Rotas")
                        }
                        
                        // Botão de família
                        IconButton(
                            onClick = { navController.navigate(FAMILY_SCREEN) }
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Família")
                        }
                        
                        // Botão de histórico de notificações
                        IconButton(
                            onClick = { navController.navigate(AppDestinations.NOTIFICATION_HISTORY_SCREEN) }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Histórico de Notificações")
                        }
                        
                        // Botão de logout
                        IconButton(
                            onClick = {
                                mainViewModel.resetAuthenticationState()
                                loginViewModel.signOut()
                                navController.navigate(LOGIN_SCREEN) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sair")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false, // Removido pois agora está na BottomBar
                    mapToolbarEnabled = false
                ),
            onMapClick = { latLng ->
                if (isEditingGeofence) {
                    newGeofenceCenter = latLng
                }
            }
            ) {
                // Renderizar rotas
                activeRoutesForToday.forEach { route ->
                val routeColor = rememberParsedRouteColor(route.routeColor, "#3F51B5")

                val decodedPath: List<LatLng>? = if (!route.encodedPolyline.isNullOrBlank()) {
                    try {
                        val path = PolyUtil.decode(route.encodedPolyline)
                            if (path.size >= 2) path else null
                    } catch (e: Exception) {
                        Log.e("MapScreen", "Erro ao decodificar polyline para rota '${route.name}'", e)
                            null
                    }
                } else {
                    Log.w("MapScreen", "Rota '${route.name}' não tem encodedPolyline.")
                    null
                }

                if (decodedPath != null) {
                    Polyline(
                        points = decodedPath,
                        color = if (route.isActive) routeColor else routeColor.copy(alpha = 0.5f),
                        width = 8f,
                        clickable = true,
                        onClick = {
                            Log.d("MapScreen", "Rota (com polyline) clicada: ${route.name} - ID: ${route.id}")
                            route.id?.let { routeId ->
                                Log.d("MapScreen", "Carregando detalhes da rota: $routeId")
                                mainViewModel.loadRouteDetails(routeId)
                            } ?: Log.e("MapScreen", "ID da rota é nulo!")
                        }
                    )
                } else {
                    drawFallbackRouteLine(route, routeColor.copy(alpha = 0.7f))
                }

                    // Marcadores para origem, destino e waypoints
                route.origin?.let { point ->
                    Marker(
                        state = MarkerState(position = LatLng(point.latitude, point.longitude)),
                        title = "${route.name} - Origem",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
                route.destination?.let { point ->
                    Marker(
                        state = MarkerState(position = LatLng(point.latitude, point.longitude)),
                        title = "${route.name} - Destino",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
                route.waypoints?.forEachIndexed { index, waypoint ->
                    Marker(
                        state = MarkerState(position = LatLng(waypoint.latitude, waypoint.longitude)),
                        title = "${route.name} - Parada ${index + 1}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                    )
                }
            }

                // Marcador da localização atual
            currentLocation?.let { location ->
                val userLatLng = LatLng(location.latitude, location.longitude)
                Marker(
                    state = MarkerState(position = userLatLng),
                    title = "Você está aqui",
                    snippet = "Localização atual",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

                // Exibir marcadores dos dependentes (apenas para responsáveis)
                if (isResponsible) {
                    dependentsLocations.forEach { (dependentId, locationRecord) ->
                        val dependentInfo = dependentsInfo[dependentId]
                        val dependentName = dependentInfo?.name ?: "Dependente"
                        val lat = locationRecord.latitude
                        val lng = locationRecord.longitude
                        if (lat != null && lng != null) {
                            Marker(
                                state = MarkerState(position = LatLng(lat, lng)),
                                title = dependentName,
                                snippet = "Última localização: ${locationRecord.dateTime}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                            )
                        }
                    }
                }

                // Geofences ativas
                activeGeofences.forEach { geofence ->
                    val centerLatLng = LatLng(geofence.coordinates.latitude, geofence.coordinates.longitude)
                    
                    // Determinar cor baseada no status da geofence e localização do usuário
                    val strokeColor = when {
                        currentLocation == null -> Color.Gray.copy(alpha = 0.7f) // Sem localização = cinza
                        geofenceStatusMap[geofence.id] == true -> Color.Green.copy(alpha = 0.8f) // Dentro = verde
                        geofenceStatusMap[geofence.id] == false -> Color.Red.copy(alpha = 0.8f) // Fora = vermelho
                        else -> Color.Gray.copy(alpha = 0.7f) // Status desconhecido = cinza
                    }
                    val fillColor = strokeColor.copy(alpha = 0.2f)
                    
                    Circle(
                        center = centerLatLng,
                        radius = geofence.radius.toDouble(),
                        strokeColor = strokeColor,
                        strokeWidth = 3f,
                        fillColor = fillColor
                    )
                    
                    // Determinar texto do snippet baseado no status
                    val statusText = when {
                        currentLocation == null -> "Sem localização"
                        geofenceStatusMap[geofence.id] == true -> "Dentro da área"
                        geofenceStatusMap[geofence.id] == false -> "Fora da área"
                        else -> "Status desconhecido"
                    }
                    
                    Marker(
                        state = MarkerState(position = centerLatLng),
                        title = geofence.name,
                        snippet = "Raio: ${geofence.radius.toInt()}m - $statusText"
                    )
            }

                // Geofence em edição
            if (isEditingGeofence && newGeofenceCenter != null) {
                Circle(
                    center = newGeofenceCenter!!,
                    radius = newGeofenceRadius.toDouble(),
                    strokeColor = Color.Magenta.copy(alpha = 0.7f),
                    strokeWidth = 7f,
                    fillColor = Color.Magenta.copy(alpha = 0.2f)
                )
                Marker(
                    state = MarkerState(position = newGeofenceCenter!!),
                        title = "Novo Centro Geofence",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
                )
                }
            }

            // Loading indicator
            if (isLoadingRoutes && routes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimensions.paddingMediumDp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// Função auxiliar para desenhar rotas de fallback
@Composable
@GoogleMapComposable
private fun drawFallbackRouteLine(
    route: Route,
    color: Color
) {
    val fallbackPoints = mutableListOf<LatLng>()
    route.origin?.let { point -> fallbackPoints.add(LatLng(point.latitude, point.longitude)) }
    route.waypoints?.forEach { waypoint ->
        fallbackPoints.add(LatLng(waypoint.latitude, waypoint.longitude))
    }
    route.destination?.let { point -> fallbackPoints.add(LatLng(point.latitude, point.longitude)) }

    if (fallbackPoints.size >= 2) {
        Polyline(
            points = fallbackPoints,
            color = color.copy(alpha = 0.6f),
            width = 6f,
            pattern = listOf<PatternItem>(Dash(20f), Gap(10f))
        )
    }
}