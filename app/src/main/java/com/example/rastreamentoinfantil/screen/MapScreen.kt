package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory // Importe este
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.rastreamentoinfantil.viewmodel.MainViewModel

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val currentLocation by mainViewModel.currentLocation.collectAsState()

    // Posição inicial da câmera (pode ser um local padrão ou a última localização conhecida)
    val defaultLocation = LatLng(-23.550520, -46.633308) // São Paulo como exemplo
    val initialZoom = 15f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, initialZoom)
    }

    // Efeito para mover a câmera quando a localização do usuário muda
    // Usamos 'key1 = currentLocation' para que o LaunchedEffect seja re-executado
    // apenas quando 'currentLocation' mudar.
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            // Anima a câmera para a nova posição do usuário
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(userLatLng, cameraPositionState.position.zoom), // Mantém o zoom atual ou defina um novo
                durationMs = 1000 // Duração da animação em milissegundos
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(), // Ocupa todo o espaço disponível
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false // Desabilitamos o botão de "minha localização" padrão
            // pois estamos controlando o marcador e a câmera manualmente
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true, // Habilita controles de zoom
            myLocationButtonEnabled = false
        )
    ) {
        // Marcador para a localização atual do usuário
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            Marker(
                state = MarkerState(position = userLatLng),
                title = "Você está aqui",
                snippet = "Localização atual"
                // Você pode personalizar o ícone do marcador aqui:
                // icon = BitmapDescriptorFactory.fromResource(R.drawable.seu_icone_marcador)
            )
        }

        // Aqui você poderá adicionar outros marcadores ou desenhos no futuro
        // (como a área da geofence)
    }
}