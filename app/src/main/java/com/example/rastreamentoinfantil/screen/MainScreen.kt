package com.example.rastreamentoinfantil.screen

import androidx.compose.ui.semantics.error
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions

@Composable
fun MainScreen(mainViewModel: MainViewModel, navController: NavController) {
    val dimensions = rememberResponsiveDimensions()
    LaunchedEffect(Unit) {
        mainViewModel.syncCurrentUser()
    }
    val locationRecords by mainViewModel.locationRecords.observeAsState(emptyList())
    val isLoading by mainViewModel.isLoading.observeAsState(false)
    val error by mainViewModel.error.observeAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMediumDp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (dimensions.isTablet) 500.dp else dimensions.screenWidth.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(dimensions.paddingMediumDp),
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmallDp)
            ) {
                items(locationRecords) { record ->
                    val color = if (record.isOutOfRoute == true) Color.Red else Color.Green
                    Column(
                        modifier = Modifier
                            .padding(dimensions.paddingSmallDp)
                    ) {
                        Text(
                            text = "Localização: ${record.address}",
                            color = color
                        )
                        Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))
                        Text(text = "Data/Hora: ${record.dateTime}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimensions.paddingLargeDp))

        Button(onClick = {
            navController.navigate("mapscreen")
        }, modifier = Modifier
            .fillMaxWidth()
            .height(dimensions.buttonHeightDp)
            .widthIn(max = if (dimensions.isTablet) 400.dp else dimensions.screenWidth.dp)
        ) {
            Text("Abrir Mapa")
        }

        if (!error.isNullOrEmpty()) {
            showDialog = true
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Erro") },
                text = { Text(error!!) },
                confirmButton = {
                    Button(onClick = {
                        showDialog = false
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}