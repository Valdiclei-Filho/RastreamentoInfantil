package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.Box // Adicionado para sobrepor controles
import androidx.compose.foundation.layout.Column // Adicionado para organizar controles
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button // Adicionado para botões
import androidx.compose.material3.Slider // Adicionado para o Slider
import androidx.compose.material3.Text // Adicionado para exibir texto
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory // Para ícones de marcador personalizados
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.Geofence
import java.util.UUID // Para gerar IDs de geofence temporários/novos

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel
) {
    val currentLocation by mainViewModel.currentLocation.collectAsState()
    val currentGeofence by mainViewModel.geofenceArea.collectAsState()
    val isInsideGeofence by mainViewModel.isUserInsideGeofence.collectAsState()

    // Estados para a edição da geofence
    var newGeofenceCenter by remember { mutableStateOf<LatLng?>(null) }
    var newGeofenceRadius by remember { mutableStateOf(currentGeofence?.radius ?: 100f) } // Inicializa com o raio atual ou um padrão
    var isEditingGeofence by remember { mutableStateOf(false) } // Para controlar a visibilidade dos controles de edição


    val defaultLocation = LatLng(-23.550520, -46.633308) // São Paulo como padrão
    val initialZoom = 15f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, initialZoom)
    }

    // Atualiza a câmera para a localização do usuário
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            if (!isEditingGeofence) { // Só move a câmera se não estiver editando
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(userLatLng, cameraPositionState.position.zoom),
                    durationMs = 1000
                )
            }
        }
    }

    // Quando a geofence existente muda (e não estamos editando ativamente um novo centro),
    // atualizamos o centro em potencial para edição e o raio.
    LaunchedEffect(currentGeofence) {
        if (!isEditingGeofence) {
            newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
            newGeofenceRadius = currentGeofence?.radius ?: 100f
        }
    }


    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false), // Desabilitado para não confundir com o marcador de usuário
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true), // Habilita o botão "My Location" do Google Maps
            onMapClick = { latLng ->
                if (isEditingGeofence) {
                    newGeofenceCenter = latLng // Define o novo centro ao clicar no mapa
                }
            }
        ) {
            // Marcador para a localização atual do usuário
            currentLocation?.let { location ->
                val userLatLng = LatLng(location.latitude, location.longitude)
                Marker(
                    state = MarkerState(position = userLatLng),
                    title = "Você está aqui",
                    snippet = "Localização atual",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) // Cor diferente
                )
            }

            // Desenha a GEOFENCE ATIVA (do ViewModel)
            currentGeofence?.let { fence ->
                if (!isEditingGeofence || newGeofenceCenter == null) { // Só desenha a geofence ativa se não estiver editando uma nova, ou se a nova não tiver centro
                    val centerLatLng = LatLng(fence.coordinates.latitude, fence.coordinates.longitude)
                    val geofenceStrokeColor: Color
                    val geofenceFillColor: Color

                    when (isInsideGeofence) {
                        true -> {
                            geofenceStrokeColor = Color.Green.copy(alpha = 0.8f)
                            geofenceFillColor = Color.Green.copy(alpha = 0.3f)
                        }
                        false -> {
                            geofenceStrokeColor = Color.Red.copy(alpha = 0.8f)
                            geofenceFillColor = Color.Red.copy(alpha = 0.3f)
                        }
                        null -> {
                            geofenceStrokeColor = Color.Gray.copy(alpha = 0.7f)
                            geofenceFillColor = Color.Gray.copy(alpha = 0.2f)
                        }
                    }

                    Circle(
                        center = centerLatLng,
                        radius = fence.radius.toDouble(),
                        strokeColor = geofenceStrokeColor,
                        strokeWidth = 5f,
                        fillColor = geofenceFillColor
                    )
                    Marker(
                        state = MarkerState(position = centerLatLng),
                        title = fence.name ?: "Área Segura",
                        snippet = "Raio: ${fence.radius}m"
                    )
                }
            }

            // Desenha a GEOFENCE EM EDIÇÃO (temporária)
            if (isEditingGeofence && newGeofenceCenter != null) {
                Circle(
                    center = newGeofenceCenter!!,
                    radius = newGeofenceRadius.toDouble(),
                    strokeColor = Color.Magenta.copy(alpha = 0.7f),
                    strokeWidth = 7f, // Mais grossa para destacar
                    fillColor = Color.Magenta.copy(alpha = 0.2f)
                )
                Marker(
                    state = MarkerState(position = newGeofenceCenter!!),
                    title = "Novo Centro",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
                )
            }
        }

        // Controles de Edição da Geofence (sobrepostos ao mapa)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (isEditingGeofence) {
                Text("Raio: ${newGeofenceRadius.toInt()} m", color = Color.Black)
                Slider(
                    value = newGeofenceRadius,
                    onValueChange = { newGeofenceRadius = it },
                    valueRange = 50f..1000f, // Exemplo de range, ajuste conforme necessário
                    steps = ((1000f - 50f) / 10f).toInt() -1 // Opcional: define "pulos" no slider
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    newGeofenceCenter?.let { center ->
                        val geofenceToSave = Geofence(
                            id = currentGeofence?.id ?: UUID.randomUUID().toString(), // Reusa ID ou cria novo
                            name = currentGeofence?.name ?: "Nova Área Segura", // Reusa nome ou default
                            radius = newGeofenceRadius,
                            coordinates = Coordinate(center.latitude, center.longitude)
                        )
                        mainViewModel.updateUserGeofence(geofenceToSave) // Chama o ViewModel
                    }
                    isEditingGeofence = false // Sai do modo de edição
                }) {
                    Text("Salvar Geofence")
                }
                Button(onClick = {
                    // Restaura os valores para os da geofence ativa ou limpa
                    newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
                    newGeofenceRadius = currentGeofence?.radius ?: 100f
                    isEditingGeofence = false
                }) {
                    Text("Cancelar")
                }
                // Botão para deletar a geofence existente
                if (currentGeofence != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        mainViewModel.updateUserGeofence(null) // Passa null para deletar
                        isEditingGeofence = false // Sai do modo de edição
                        newGeofenceCenter = null // Limpa o centro de edição
                    }) {
                        Text("Remover Geofence Atual")
                    }
                }

            } else {
                Button(onClick = {
                    // Entra no modo de edição
                    // Se já existe uma geofence, usa seus valores como ponto de partida
                    // Se não, o newGeofenceCenter pode ser null (usuário clica para definir) ou a localização atual
                    newGeofenceCenter = currentGeofence?.let { LatLng(it.coordinates.latitude, it.coordinates.longitude) }
                        ?: currentLocation?.let { LatLng(it.latitude, it.longitude) } // Ponto de partida
                    newGeofenceRadius = currentGeofence?.radius ?: 100f
                    isEditingGeofence = true
                }) {
                    Text(if (currentGeofence == null) "Definir Nova Geofence" else "Editar Geofence")
                }
            }
        }
    }
}