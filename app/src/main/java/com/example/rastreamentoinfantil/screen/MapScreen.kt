package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Importe Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.model.Geofence // Importe sua data class

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val currentLocation by mainViewModel.currentLocation.collectAsState()
    val geofence by mainViewModel.geofenceArea.collectAsState() // Observe a geofence
    val isInsideGeofence by mainViewModel.isUserInsideGeofence.collectAsState() // Observe o status

    val defaultLocation = LatLng(-23.550520, -46.633308)
    val initialZoom = 15f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, initialZoom)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(userLatLng, cameraPositionState.position.zoom),
                durationMs = 10000
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
    ) {
        // Marcador para a localização atual do usuário
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            Marker(
                state = MarkerState(position = userLatLng),
                title = "Você está aqui",
                snippet = "Localização atual"
            )
        }

        // Desenha o círculo da Geofence
        geofence?.let { fence ->
            val centerLatLng = LatLng(fence.coordinates.latitude, fence.coordinates.longitude)
            val geofenceStrokeColor: Color
            val geofenceFillColor: Color

            when (isInsideGeofence) {
                true -> { // Usuário DENTRO da geofence
                    geofenceStrokeColor = Color.Green.copy(alpha = 0.8f)
                    geofenceFillColor = Color.Green.copy(alpha = 0.3f)
                }

                false -> { // Usuário FORA da geofence
                    geofenceStrokeColor = Color.Red.copy(alpha = 0.8f)
                    geofenceFillColor = Color.Red.copy(alpha = 0.3f)
                }

                null -> { // Status indefinido (sem localização ou geofence ainda)
                    geofenceStrokeColor = Color.Gray.copy(alpha = 0.7f)
                    geofenceFillColor = Color.Gray.copy(alpha = 0.2f)
                }
            }

            Circle(
                center = centerLatLng,
                radius = fence.radius.toDouble(), // Já é Double se definido assim na sua classe Geofence
                strokeColor = geofenceStrokeColor,
                strokeWidth = 5f,
                fillColor = geofenceFillColor
            )

            Marker(
                state = MarkerState(position = centerLatLng),
                title = "Área Segura (${fence.id})",
                snippet = "Centro da geofence"
            )
        }
    }
}