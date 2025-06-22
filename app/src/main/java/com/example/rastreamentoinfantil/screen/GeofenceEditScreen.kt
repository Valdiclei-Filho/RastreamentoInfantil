package com.example.rastreamentoinfantil.screen

import android.util.Log
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.auth.FirebaseAuth
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceEditScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    geofenceId: String? // Null para criar, não-null para editar
) {
    val dimensions = rememberResponsiveDimensions()
    val scope = rememberCoroutineScope()
    val existingGeofence by mainViewModel.selectedGeofence.collectAsStateWithLifecycle()
    val geofenceOpStatus by mainViewModel.geofenceOperationStatus.collectAsStateWithLifecycle()

    var geofenceName by rememberSaveable(existingGeofence) { mutableStateOf(existingGeofence?.name ?: "") }
    
    // Usar uma variável local para controlar o estado
    var _geofenceCenter by remember { mutableStateOf<LatLng?>(existingGeofence?.coordinates?.let { LatLng(it.latitude, it.longitude) }) }
    val geofenceCenter by derivedStateOf { _geofenceCenter }
    
    var geofenceRadius by rememberSaveable(existingGeofence) { mutableStateOf(existingGeofence?.radius ?: 100f) }
    var isGeofenceActive by rememberSaveable(existingGeofence) { mutableStateOf(existingGeofence?.isActive ?: false) }
    var selectedTargetUserId by rememberSaveable(existingGeofence) { mutableStateOf(existingGeofence?.targetUserId ?: "") }

    // Estados para seções colapsáveis
    var showConfigSection by remember { mutableStateOf(false) }
    var showFamilySection by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Observar dados do ViewModel
    val isResponsible by mainViewModel.isResponsible.collectAsStateWithLifecycle()
    val familyMembers by mainViewModel.familyMembers.collectAsStateWithLifecycle()

    // Verificação de autenticação e responsável
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    if (firebaseUser == null) {
        Log.w("GeofenceEditScreen", "Tentativa de acessar criação de geofence sem usuário logado!")
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Usuário não autenticado. Faça login para criar uma área segura.")
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
            Text("Apenas responsáveis podem criar e editar áreas seguras.")
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

    // Carregar detalhes da geofence se estiver editando
    LaunchedEffect(geofenceId) {
        if (geofenceId != null) {
            mainViewModel.loadGeofenceDetails(geofenceId)
        } else {
            geofenceName = ""
            _geofenceCenter = null
            geofenceRadius = 100f
            isGeofenceActive = true
            selectedTargetUserId = ""
        }
    }

    // Atualizar campos quando existingGeofence carregar (para edição)
    LaunchedEffect(existingGeofence, geofenceId) {
        if (existingGeofence != null && geofenceId != null) {
            val geofenceData = existingGeofence!!
            geofenceName = geofenceData.name
            _geofenceCenter = geofenceData.coordinates.let { LatLng(it.latitude, it.longitude) }
            geofenceRadius = geofenceData.radius
            isGeofenceActive = geofenceData.isActive
            selectedTargetUserId = geofenceData.targetUserId ?: ""
        } else if (geofenceId == null) {
            geofenceName = ""
            _geofenceCenter = null
            geofenceRadius = 100f
            isGeofenceActive = false
            selectedTargetUserId = ""
        }
    }

    LaunchedEffect(geofenceOpStatus) {
        when (val status = geofenceOpStatus) {
            is MainViewModel.GeofenceOperationStatus.Success -> {
                snackbarHostState.showSnackbar(status.message, duration = SnackbarDuration.Short)
                mainViewModel.clearGeofenceOperationStatus()
                navController.popBackStack()
            }
            is MainViewModel.GeofenceOperationStatus.Error -> {
                snackbarHostState.showSnackbar(status.errorMessage, duration = SnackbarDuration.Long, withDismissAction = true)
                mainViewModel.clearGeofenceOperationStatus()
            }
            else -> {}
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            geofenceCenter ?: LatLng(-23.550520, -46.633308), // São Paulo como padrão
            15f
        )
    }

    // Atualizar câmera quando o centro da geofence mudar
    LaunchedEffect(geofenceCenter) {
        geofenceCenter?.let { center ->
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(center, cameraPositionState.position.zoom),
                durationMs = 1000
            )
        }
    }

    // Logging para debug
    LaunchedEffect(geofenceCenter) {
        Log.d("GeofenceEditScreen", "LaunchedEffect: geofenceCenter mudou para: $geofenceCenter")
    }
    
    // Logging adicional para debug de recomposição
    Log.d("GeofenceEditScreen", "Recomposição: geofenceCenter = $geofenceCenter")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (geofenceId == null) "Criar Nova Área Segura" else "Editar Área Segura") },
                actions = {
                    IconButton(onClick = {
                        val firebaseUser = FirebaseAuth.getInstance().currentUser
                        Log.d("GeofenceEditScreen", "Clique em salvar geofence: FirebaseAuth.currentUser = ${firebaseUser?.uid}")
                        
                        if (geofenceName.isNotBlank() && geofenceCenter != null) {
                            val geofenceToSave = Geofence(
                                id = geofenceId ?: UUID.randomUUID().toString(),
                                name = geofenceName,
                                radius = geofenceRadius,
                                coordinates = Coordinate(geofenceCenter!!.latitude, geofenceCenter!!.longitude),
                                isActive = isGeofenceActive,
                                targetUserId = selectedTargetUserId.takeIf { it.isNotEmpty() },
                                createdByUserId = firebaseUser?.uid ?: ""
                            )
                            
                            if (geofenceId != null) {
                                mainViewModel.updateGeofence(geofenceToSave)
                            } else {
                                mainViewModel.createGeofence(geofenceToSave)
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Nome e localização são obrigatórios.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Check, "Salvar Área Segura")
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
            // Seção do Mapa
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
                        "Definir Área Segura no Mapa",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = dimensions.paddingSmallDp)
                    )
                    Text(
                        if (geofenceCenter == null) 
                            "Clique no mapa para definir o centro da área segura" 
                        else 
                            "Centro da área segura definido. Clique em outro local para alterar.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = dimensions.paddingMediumDp),
                        color = if (geofenceCenter == null) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) 
                        else 
                            MaterialTheme.colorScheme.primary
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
                                Log.d("GeofenceEditScreen", "Mapa clicado em: ${latLng.latitude}, ${latLng.longitude}")
                                Log.d("GeofenceEditScreen", "Antes de atualizar: geofenceCenter = $geofenceCenter")
                                _geofenceCenter = latLng
                                Log.d("GeofenceEditScreen", "geofenceCenter atualizado para: $geofenceCenter")
                                Log.d("GeofenceEditScreen", "Após atualizar: geofenceCenter = $geofenceCenter")
                            }
                        ) {
                            Log.d("GeofenceEditScreen", "GoogleMap content: geofenceCenter = $geofenceCenter")
                            geofenceCenter?.let { center ->
                                Log.d("GeofenceEditScreen", "Renderizando geofence no centro: ${center.latitude}, ${center.longitude}")
                                Marker(
                                    state = MarkerState(position = center),
                                    title = "Centro da Área Segura"
                                )
                                
                                Circle(
                                    center = center,
                                    radius = geofenceRadius.toDouble(),
                                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    strokeColor = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3f
                                )
                            } ?: run {
                                Log.d("GeofenceEditScreen", "geofenceCenter é null, não renderizando geofence")
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmallDp)
                    ) {
                        Button(
                            onClick = {
                                Log.d("GeofenceEditScreen", "Limpando localização da geofence")
                                Log.d("GeofenceEditScreen", "Antes de limpar: geofenceCenter = $geofenceCenter")
                                _geofenceCenter = null
                                Log.d("GeofenceEditScreen", "Depois de limpar: geofenceCenter = $geofenceCenter")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (geofenceCenter == null) 
                                    MaterialTheme.colorScheme.secondary 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (geofenceCenter == null) "Localização Limpa" else "Limpar Localização")
                        }

                        Text(
                            "Raio: ${geofenceRadius.toInt()}m",
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
                    // Seção de Configuração Básica
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
                                    value = geofenceName,
                                    onValueChange = { geofenceName = it },
                                    label = { Text("Nome da Área Segura") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))
                                
                                Text("Raio: ${geofenceRadius.toInt()}m", style = MaterialTheme.typography.bodyLarge)
                                Slider(
                                    value = geofenceRadius,
                                    onValueChange = { geofenceRadius = it },
                                    valueRange = 50f..1000f,
                                    steps = 19
                                )
                                
                                Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Área Ativa:", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(modifier = Modifier.width(dimensions.paddingSmallDp))
                                    Switch(
                                        checked = isGeofenceActive,
                                        onCheckedChange = { isGeofenceActive = it }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Seção de Família
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
                                    "Usuário da Família",
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
                                        Text("A área segura ficará visível apenas para você", style = MaterialTheme.typography.bodySmall)
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
        }
    }
} 