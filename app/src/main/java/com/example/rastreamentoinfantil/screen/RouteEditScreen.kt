// Novo arquivo: screen/RouteEditScreen.kt
package com.example.rastreamentoinfantil.screen

import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.auth.FirebaseAuth
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions
import com.example.rastreamentoinfantil.service.GeocodingService
import android.location.Location
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    routeId: String? // Null para criar, não-null para editar
) {
    val dimensions = rememberResponsiveDimensions()
    val scope = rememberCoroutineScope()
    val existingRoute by mainViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeOpStatus by mainViewModel.routeOperationStatus.collectAsStateWithLifecycle()

    var routeName by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.name ?: "") }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var originPoint by remember(existingRoute) {
        mutableStateOf(existingRoute?.origin?.let { LatLng(it.latitude, it.longitude) })
    }
    var destinationPoint by remember(existingRoute) {
        mutableStateOf(existingRoute?.destination?.let { LatLng(it.latitude, it.longitude) })
    }
    var isRouteActive by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.isActive ?: false) }
    var selectedActiveDays by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.activeDays ?: emptyList()) }
    var selectedTargetUserId by rememberSaveable(existingRoute) { mutableStateOf(existingRoute?.targetUserId ?: "") }
    var displayableEncodedPolyline by remember { mutableStateOf<String?>(null) }

    // Estados para seções colapsáveis
    var showConfigSection by remember { mutableStateOf(false) }
    var showDaysSection by remember { mutableStateOf(false) }
    var showFamilySection by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Observar dados do ViewModel
    val isResponsible by mainViewModel.isResponsible.collectAsStateWithLifecycle()
    val familyMembers by mainViewModel.familyMembers.collectAsStateWithLifecycle()

    // Verificação de autenticação e responsável
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    if (firebaseUser == null) {
        Log.w("RouteEditScreen", "Tentativa de acessar criação de rota sem usuário logado!")
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Usuário não autenticado. Faça login para criar uma rota.")
        }
        return
    }

    // Verificar se é responsável
    if (!isResponsible) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Apenas responsáveis podem criar e editar rotas.")
            Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Voltar")
            }
        }
        return
    }

    // Sincronizar usuário logado ao abrir a tela
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }

    // Carregar detalhes da rota se estiver editando
    LaunchedEffect(routeId) {
        if (routeId != null) {
            mainViewModel.loadRouteDetails(routeId)
        } else {
            routeName = ""
            routePoints = emptyList()
            originPoint = null
            destinationPoint = null
            isRouteActive = true
            displayableEncodedPolyline = null
        }
    }

    // Atualizar campos quando existingRoute carregar (para edição)
    LaunchedEffect(existingRoute, routeId) {
        if (existingRoute != null && routeId != null) {
            val routeData = existingRoute!!
            routeName = routeData.name
            originPoint = routeData.origin?.let { coord -> LatLng(coord.latitude, coord.longitude) }
            destinationPoint = routeData.destination?.let { coord -> LatLng(coord.latitude, coord.longitude) }
            routePoints = routeData.waypoints?.map { coord -> LatLng(coord.latitude, coord.longitude) } ?: emptyList()
            isRouteActive = routeData.isActive
            selectedActiveDays = routeData.activeDays
            selectedTargetUserId = routeData.targetUserId ?: ""
            displayableEncodedPolyline = routeData.encodedPolyline
        } else if (routeId == null) {
            routeName = ""
            originPoint = null
            destinationPoint = null
            routePoints = emptyList()
            isRouteActive = false
            selectedActiveDays = emptyList()
            selectedTargetUserId = ""
            displayableEncodedPolyline = null
        }
    }

    LaunchedEffect(routeOpStatus) {
        when (val status = routeOpStatus) {
            is MainViewModel.RouteOperationStatus.Success -> {
                snackbarHostState.showSnackbar(status.message, duration = SnackbarDuration.Short)
                mainViewModel.clearRouteOperationStatus()
                // Forçar recarregamento das rotas após salvar
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                firebaseUser?.uid?.let { mainViewModel.loadUserRoutes(it) }
                navController.popBackStack()
            }
            is MainViewModel.RouteOperationStatus.Error -> {
                snackbarHostState.showSnackbar(status.errorMessage, duration = SnackbarDuration.Long, withDismissAction = true)
                mainViewModel.clearRouteOperationStatus()
            }
            else -> {}
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(-23.550520, -46.633308), // São Paulo como padrão
            12f
        )
    }

    // Função para resetar todos os pontos da rota, incluindo origem e destino
    fun resetRoutePoints() {
        originPoint = null
        destinationPoint = null
        routePoints = emptyList()
        displayableEncodedPolyline = null
    }

    // Lógica de adição de pontos
    fun addPointToRoute(latLng: LatLng) {
        if (originPoint == null) {
            originPoint = latLng
            displayableEncodedPolyline = null
        } else if (destinationPoint == null) {
            destinationPoint = latLng
            displayableEncodedPolyline = null
        } else {
            routePoints = routePoints + latLng
            displayableEncodedPolyline = null
        }
    }

    val context = LocalContext.current
    val geocodingService = remember { GeocodingService(context) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (routeId == null) "Criar Nova Rota" else "Editar Rota") },
                actions = {
                    IconButton(onClick = {
                        val firebaseUser = FirebaseAuth.getInstance().currentUser
                        Log.d("RouteEditScreen", "Clique em salvar rota: FirebaseAuth.currentUser =  [${firebaseUser?.uid}, email = ${firebaseUser?.email}")
                        // Buscar endereço real para origem e destino
                        if (originPoint != null && destinationPoint != null) {
                            val originLoc = Location("").apply {
                                latitude = originPoint!!.latitude
                                longitude = originPoint!!.longitude
                            }
                            val destLoc = Location("").apply {
                                latitude = destinationPoint!!.latitude
                                longitude = destinationPoint!!.longitude
                            }
                            // Buscar endereços de forma assíncrona
                            var originAddress: String? = null
                            var destAddress: String? = null
                            var readyToSave = false
                            val trySave = {
                                if (originAddress != null && destAddress != null) {
                                    val finalOrigin = RoutePoint(latitude = originPoint!!.latitude, longitude = originPoint!!.longitude, address = originAddress)
                                    val finalDestination = RoutePoint(latitude = destinationPoint!!.latitude, longitude = destinationPoint!!.longitude, address = destAddress)
                                    val finalWaypoints = routePoints.map { RoutePoint(latitude = it.latitude, longitude = it.longitude) }
                                    if (routeName.isNotBlank() && finalOrigin != null && finalDestination != null) {
                                        if (routeId != null && existingRoute != null) {
                                            existingRoute?.let { route ->
                                                mainViewModel.createRouteWithDirections(
                                                    name = routeName,
                                                    originPoint = finalOrigin,
                                                    destinationPoint = finalDestination,
                                                    waypointsList = finalWaypoints.takeIf { it.isNotEmpty() },
                                                    isActive = isRouteActive,
                                                    activeDays = selectedActiveDays,
                                                    targetUserId = selectedTargetUserId.takeIf { it.isNotEmpty() },
                                                    id = route.id, // Mantenha o id original
                                                    createdByUserId = route.createdByUserId // Mantenha o criador original
                                                )
                                            }
                                        } else {
                                            mainViewModel.createRouteWithDirections(
                                                name = routeName,
                                                originPoint = finalOrigin,
                                                destinationPoint = finalDestination,
                                                waypointsList = finalWaypoints.takeIf { it.isNotEmpty() },
                                                isActive = isRouteActive,
                                                activeDays = selectedActiveDays,
                                                targetUserId = selectedTargetUserId.takeIf { it.isNotEmpty() }
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Nome, origem e destino são obrigatórios.",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                }
                            }
                            // Buscar endereço de origem
                            geocodingService.getAddressFromLocation(originLoc) { address ->
                                originAddress = address
                                trySave()
                            }
                            // Buscar endereço de destino
                            geocodingService.getAddressFromLocation(destLoc) { address ->
                                destAddress = address
                                trySave()
                            }
                        } else {
                            scope.launch {
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(dimensions.paddingMediumDp)
        ) {
            // Seção do Mapa (prioridade máxima) - SEM SCROLL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (dimensions.isTablet) 500.dp else 400.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMediumDp)
            ) {
                    Text(
                        "Definir Rota no Mapa",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = dimensions.paddingSmallDp)
                    )
                    Text(
                        "Clique para definir: 1º Origem, 2º Destino, 3º Pontos Intermediários",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = dimensions.paddingMediumDp)
                    )

                    Box(
                        modifier = Modifier
                .fillMaxWidth()
                            .weight(1f)
                    ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                mapToolbarEnabled = false,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true,
                                tiltGesturesEnabled = true,
                                rotationGesturesEnabled = true
                            ),
                    onMapClick = { latLng ->
                                addPointToRoute(latLng)
                    }
                ) {
                    originPoint?.let {
                                Marker(
                                    state = MarkerState(position = it),
                                    title = "Origem",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                                )
                    }
                    destinationPoint?.let {
                                Marker(
                                    state = MarkerState(position = it),
                                    title = "Destino",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                )
                    }
                    routePoints.forEachIndexed { index, point ->
                                Marker(
                                    state = MarkerState(position = point),
                                    title = "Ponto ${index + 1}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                                )
                    }

                            if (displayableEncodedPolyline != null) {
                        val decodedPath: List<LatLng> = try {
                                    com.google.maps.android.PolyUtil.decode(displayableEncodedPolyline!!)
                        } catch (e: Exception) {
                                    Log.e("RouteEditScreen", "Falha ao decodificar polyline: $displayableEncodedPolyline", e)
                            emptyList()
                        }
                        if (decodedPath.isNotEmpty()) {
                            Polyline(points = decodedPath, color = MaterialTheme.colorScheme.primary, width = 8f)
                        }
                            } else {
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
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmallDp)
                    ) {
                        Button(
                            onClick = {
                                resetRoutePoints()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Limpar Pontos")
            }

                        // Mostrar pontos definidos
                        Text(
                            "Pontos: ${(if (originPoint != null) 1 else 0) + (if (destinationPoint != null) 1 else 0) + routePoints.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = dimensions.paddingSmallDp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))

            // Seções de configuração com scroll próprio
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMediumDp)
            ) {
                item {
            // Seção de Configuração Básica (colapsável)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMediumDp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Configuração Básica",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { showConfigSection = !showConfigSection }) {
                            Icon(
                                if (showConfigSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showConfigSection) "Recolher" else "Expandir"
                            )
                        }
                    }
                    
                    if (showConfigSection) {
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                        
                        OutlinedTextField(
                            value = routeName,
                            onValueChange = { routeName = it },
                            label = { Text("Nome da Rota") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rota Ativa:", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(dimensions.paddingSmallDp))
                            Switch(
                                checked = isRouteActive,
                                onCheckedChange = { isRouteActive = it }
                            )
                                }
                        }
                    }
                }
            }

                item {
            // Seção de Dias da Semana (colapsável)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMediumDp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Dias da Semana",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { showDaysSection = !showDaysSection }) {
                            Icon(
                                if (showDaysSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showDaysSection) "Recolher" else "Expandir"
                            )
                        }
                    }
                    
                    if (showDaysSection) {
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                        
                        val weekDays = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
                        val linhas = weekDays.chunked(3)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            linhas.forEach { linha ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    linha.forEach { dia ->
                                        val selecionado = selectedActiveDays.contains(dia)
                                        FilterChip(
                                            selected = selecionado,
                                            onClick = {
                                                selectedActiveDays = if (selecionado) selectedActiveDays - dia else selectedActiveDays + dia
                                            },
                                            label = {
                                                Text(
                                                    dia,
                                                    color = if (selecionado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                                )
                                            },
                                            modifier = Modifier.padding(vertical = 1.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
        }
    }
}
            }

                item {
            // Seção de Família (colapsável)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevationDp)
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMediumDp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Usuários da Família",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { showFamilySection = !showFamilySection }) {
                            Icon(
                                if (showFamilySection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showFamilySection) "Recolher" else "Expandir"
                            )
                        }
                    }
                    
                    if (showFamilySection) {
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                                
                                // Opção "Nenhum" para não atribuir a nenhum membro
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedTargetUserId.isEmpty(),
                                        onClick = { selectedTargetUserId = "" }
                                    )
                                    Column {
                                        Text("Nenhum (apenas responsável)", style = MaterialTheme.typography.bodyMedium)
                                        Text("A rota ficará visível apenas para você", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                        
                        if (familyMembers.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.height(150.dp)
                            ) {
                                items(familyMembers) { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedTargetUserId == member.id,
                                            onClick = { selectedTargetUserId = member.id ?: "" }
                                        )
                                        Column {
                                            Text(member.name ?: "Sem nome", style = MaterialTheme.typography.bodyMedium)
                                            Text(member.email ?: "", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Nenhum membro da família encontrado", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingLargeDp))
        }
    }
}