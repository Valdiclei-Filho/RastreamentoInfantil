package com.example.rastreamentoinfantil.screen

import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.maps.android.compose.*
// import androidx.compose.runtime.getValue // Já importado por androidx.compose.runtime.*
// import androidx.compose.runtime.setValue // Já importado por androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Mantenha, mas vamos discutir o padrão
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.screen.AppDestinations.ROUTE_LIST_SCREEN
// import com.google.android.libraries.mapsplatform.transportation.consumer.model.Route // REMOVA ESTA LINHA CONFLITANTE
import com.example.rastreamentoinfantil.model.Route // CERTIFIQUE-SE QUE ESTA É A CORRETA
import com.google.maps.android.PolyUtil
import java.util.UUID

// Imports para PatternItem, Dash, Gap
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel, // Removido o default viewModel() aqui, passe explicitamente
    navController: NavHostController
) {
    val currentLocation by mainViewModel.currentLocation.collectAsState()
    val currentGeofence by mainViewModel.geofenceArea.collectAsState()
    val isInsideGeofence by mainViewModel.isUserInsideGeofence.collectAsState()

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }

    val routes by mainViewModel.routes.collectAsStateWithLifecycle()
    val isLoadingRoutes by mainViewModel.isLoadingRoutes.collectAsStateWithLifecycle()

    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            mainViewModel.loadUserRoutes(currentUserId)
        }
    }

    var newGeofenceCenter by remember { mutableStateOf<LatLng?>(null) }
    var newGeofenceRadius by remember { mutableStateOf(100f) } // Inicializa com um padrão
    var isEditingGeofence by remember { mutableStateOf(false) }

    val defaultLocation = LatLng(-23.550520, -46.633308) // São Paulo como padrão
    val initialZoom = 15f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, initialZoom)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            // Verifica se o usuário está movendo o mapa manualmente
            // e se não estamos no modo de edição da geofence.
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

    // Função auxiliar para parsear a cor da rota de forma segura
    // Usamos remember para que o cálculo só ocorra se route.routeColor mudar
    @Composable
    fun rememberParsedRouteColor(routeColorString: String?, defaultColorValue: String = "#3F51B5"): Color {
        return remember(routeColorString) {
            try {
                Color(android.graphics.Color.parseColor(routeColorString ?: defaultColorValue))
            } catch (e: Exception) {
                Log.w("MapScreen", "Cor inválida para rota: $routeColorString", e)
                Color(android.graphics.Color.parseColor(defaultColorValue)) // Fallback
            }
        }
    }


    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true),
            onMapClick = { latLng ->
                if (isEditingGeofence) {
                    newGeofenceCenter = latLng
                }
            }
        ) { // Início do ComposableMapScope

            if (isLoadingRoutes && routes.isEmpty()) { // Mostra apenas se estiver carregando E não houver rotas
                // Não é possível colocar CircularProgressIndicator diretamente aqui
                // porque este é um ComposableMapScope, não um escopo de layout normal.
                // Você pode colocar um Marker especial ou nada.
                // O CircularProgressIndicator foi movido para a Column de controles.
            }

            routes.forEach { route ->
                val routeColor = rememberParsedRouteColor(route.routeColor, "#3F51B5")

                // 1. Tente decodificar a polyline AQUI, fora de qualquer chamada Composable direta
                val decodedPath: List<LatLng>? = if (!route.encodedPolyline.isNullOrBlank()) {
                    try {
                        val path = PolyUtil.decode(route.encodedPolyline)
                        if (path.size >= 2) path else null // Retorna null se não tiver pontos suficientes
                    } catch (e: Exception) {
                        Log.e("MapScreen", "Erro ao decodificar polyline para rota '${route.name}'", e)
                        null // Retorna null em caso de exceção
                    }
                } else {
                    Log.w("MapScreen", "Rota '${route.name}' não tem encodedPolyline.")
                    null
                }

                // 2. Agora, use o resultado (decodedPath) para decidir o que compor
                if (decodedPath != null) {
                    Polyline(
                        points = decodedPath,
                        color = if (route.isActive) routeColor else routeColor.copy(alpha = 0.5f),
                        width = 8f,
                        clickable = true,
                        onClick = {
                            Log.d("MapScreen", "Rota (com polyline) clicada: ${route.name} - ID: ${route.id}")
                            // mainViewModel.loadRouteDetails(route.id!!)
                        }
                    )
                } else {
                    // Se decodedPath for null (seja por erro, sem polyline, ou poucos pontos)
                    // desenhe a linha de fallback.
                    Log.w("MapScreen", "Desenhando fallback para rota '${route.name}'.")
                    drawFallbackRouteLine(route, routeColor.copy(alpha = 0.7f))
                }

                // Marcadores para origem, destino e waypoints (inalterado)
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

            currentLocation?.let { location ->
                val userLatLng = LatLng(location.latitude, location.longitude)
                Marker(
                    state = MarkerState(position = userLatLng),
                    title = "Você está aqui",
                    snippet = "Localização atual",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            currentGeofence?.let { fence ->
                if (!isEditingGeofence || newGeofenceCenter == null) {
                    val centerLatLng = LatLng(fence.coordinates.latitude, fence.coordinates.longitude)
                    val (strokeColor, fillColor) = when (isInsideGeofence) {
                        true -> Color.Green.copy(alpha = 0.8f) to Color.Green.copy(alpha = 0.3f)
                        false -> Color.Red.copy(alpha = 0.8f) to Color.Red.copy(alpha = 0.3f)
                        null -> Color.Gray.copy(alpha = 0.7f) to Color.Gray.copy(alpha = 0.2f)
                    }
                    Circle(
                        center = centerLatLng,
                        radius = fence.radius.toDouble(),
                        strokeColor = strokeColor,
                        strokeWidth = 5f,
                        fillColor = fillColor
                    )
                    Marker(
                        state = MarkerState(position = centerLatLng),
                        title = fence.name ?: "Área Segura",
                        snippet = "Raio: ${fence.radius.toInt()}m" // Usar toInt() para melhor formatação
                    )
                }
            }

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
                    title = "Novo Centro Geofence", // Título mais descritivo
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
                )
            }
        } // Fim do ComposableMapScope

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (isLoadingRoutes && routes.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isEditingGeofence) {
                Text("Raio da Geofence: ${newGeofenceRadius.toInt()} m", color = Color.Black) // Use Color.Black ou outra cor de MaterialTheme
                Slider(
                    value = newGeofenceRadius,
                    onValueChange = { newGeofenceRadius = it },
                    valueRange = 50f..1000f,
                    steps = ((1000f - 50f) / 50f).toInt() - 1 // Pulos de 50m
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    newGeofenceCenter?.let { center ->
                        val geofenceToSave = Geofence(
                            id = currentGeofence?.id ?: UUID.randomUUID().toString(),
                            name = currentGeofence?.name ?: "Nova Área Segura",
                            radius = newGeofenceRadius,
                            coordinates = Coordinate(center.latitude, center.longitude)
                        )
                        mainViewModel.updateUserGeofence(geofenceToSave)
                    }
                    isEditingGeofence = false
                }) {
                    Text("Salvar Geofence")
                }
                Button(onClick = {
                    newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
                    newGeofenceRadius = currentGeofence?.radius ?: 100f
                    isEditingGeofence = false
                }) {
                    Text("Cancelar Edição") // Texto mais claro
                }
                if (currentGeofence != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        mainViewModel.updateUserGeofence(null)
                        isEditingGeofence = false
                        newGeofenceCenter = null
                    }) {
                        Text("Remover Geofence Atual")
                    }
                }

            } else {
                Button(onClick = {
                    newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
                        ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
                    newGeofenceRadius = currentGeofence?.radius ?: 100f
                    isEditingGeofence = true
                }) {
                    Text(if (currentGeofence == null) "Definir Nova Geofence" else "Editar Geofence")
                }
            }
            Spacer(modifier = Modifier.height(8.dp)) // Adicionado Spacer antes do botão de Gerenciar Rotas
            Button(onClick = { navController.navigate(ROUTE_LIST_SCREEN) }) {
                Text("Gerenciar Rotas")
            }
        }
    }
}

// A função drawFallbackRouteLine está correta em termos de escopo (ComposableMapScope)
// e acesso aos campos de 'route' assuming 'route' is your model.
@Composable
@GoogleMapComposable
private fun drawFallbackRouteLine(
    route: Route, // Seja explícito com o tipo Route aqui
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
            points = fallbackPoints, // MutableList é aceitável aqui, pois List é esperado
            color = color.copy(alpha = 0.6f),
            width = 6f,
            pattern = listOf<PatternItem>(Dash(20f), Gap(10f)) // Tipos explícitos para clareza
        )
    }
}