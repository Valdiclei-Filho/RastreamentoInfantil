package com.example.rastreamentoinfantil.screen

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.viewmodel.GeofenceViewModel
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions

@Composable
fun GeofenceConfigScreen(
    viewModel: GeofenceViewModel = viewModel(),
    onSaveSuccess: () -> Unit
) {
    val dimensions = rememberResponsiveDimensions()
    var latitudeInput by remember { mutableStateOf("") }
    var longitudeInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf("") }
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onSaveSuccess()
            viewModel.resetSaveSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMediumDp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Configurar Geofence", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))

        OutlinedTextField(
            value = latitudeInput,
            onValueChange = { latitudeInput = it },
            label = { Text("Latitude") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))

        OutlinedTextField(
            value = longitudeInput,
            onValueChange = { longitudeInput = it },
            label = { Text("Longitude") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))

        OutlinedTextField(
            value = radiusInput,
            onValueChange = { radiusInput = it },
            label = { Text("Raio (metros)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))

        Button(
            onClick = {
                val latitude = latitudeInput.toDoubleOrNull()
                val longitude = longitudeInput.toDoubleOrNull()
                val radius = radiusInput.toFloatOrNull()

                if (latitude != null && longitude != null && radius != null) {
                    val coordinate = Coordinate(latitude = latitude, longitude = longitude)
                    val geofence = Geofence(coordinates = coordinate, radius = radius)
                    viewModel.saveGeofence(geofence)
                } else {
                    viewModel.setError("Por favor, insira latitude, longitude e raio válidos.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Salvar Geofence")
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = dimensions.paddingMediumDp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = dimensions.paddingMediumDp))
        }
    }
}