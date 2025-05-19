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
                durationMs = 1000
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
            Circle(
                center = centerLatLng,
                radius = fence.radius.toDouble(), // Raio em metros
                strokeColor = Color.Blue.copy(alpha = 0.7f), // Cor da borda
                strokeWidth = 5f, // Largura da borda
                fillColor = Color.Blue.copy(alpha = 0.2f) // Cor do preenchimento
            )

            // Opcional: Adicionar um marcador para o centro da geofence
            Marker(
                state = MarkerState(position = centerLatLng),
                title = "Área Segura (${fence.id})",
                snippet = "Centro da geofence",
                // Você pode usar um ícone diferente para o centro da geofence
                // icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }
    }
}