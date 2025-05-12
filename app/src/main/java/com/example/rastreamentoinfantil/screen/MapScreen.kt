package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapScreen() {
    // Coordenada inicial para a câmera do mapa (pode ser alterada para a localização atual do usuário)
    val saoPaulo = LatLng(-23.55052, -46.633308)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(saoPaulo, 10f) // Nível de zoom inicial
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        // Aqui você pode adicionar marcadores, polilinhas, círculos, etc.
        // Vamos adicionar um marcador simples para o local inicial
        Marker(
            state = rememberMarkerState(position = saoPaulo),
            title = "São Paulo",
            snippet = "Marcador inicial"
        )
    }
}