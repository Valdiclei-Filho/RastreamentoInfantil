package com.example.rastreamentoinfantil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.rastreamentoinfantil.MyFirebaseMessagingService.Companion.CHANNEL_ID
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.screen.Navigation
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.service.LocationService
import com.example.rastreamentoinfantil.ui.theme.RastreamentoInfantilTheme
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.viewmodel.MainViewModelFactory
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Launcher para solicitar múltiplas permissões
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allPermissionsGranted = false
                }
            }

            if (allPermissionsGranted) {
                println("Todas as permissões de localização concedidas.")
            } else {
                println("Pelo menos uma permissão de localização foi negada.")
                // Ex: showPermissionDeniedDialog()
            }
        }

    private fun checkAndRequestLocationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in locationPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            println("Todas as permissões de localização já estavam concedidas.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestLocationPermissions()

        val firebaseRepository = FirebaseRepository()
        val locationService = LocationService(this)
        val geocodingService = GeocodingService(this)
        val factory = MainViewModelFactory(firebaseRepository, locationService, geocodingService)

        // Corrigido: inicializar a propriedade da classe, não uma variável local
        mainViewModel = ViewModelProvider(this, factory).get(MainViewModel::class.java)

        FirebaseApp.initializeApp(this)

        createNotificationChannel()

        setContent {
            RastreamentoInfantilTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation(activity = this, mainViewModel = mainViewModel)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alerts"
            val descriptionText = "Notifications of alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        mainViewModel.startLocationMonitoring()
    }

    override fun onPause() {
        super.onPause()
        mainViewModel.stopLocationMonitoring()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Toast.makeText(this, "Permissão concedida", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
            }
        }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "Permissão já concedida", Toast.LENGTH_SHORT).show()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                // Explique para o usuário por que a permissão é necessária (exibir diálogo)
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }
    }

}
