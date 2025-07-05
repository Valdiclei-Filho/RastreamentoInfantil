package com.example.rastreamentoinfantil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.rastreamentoinfantil.service.MyFirebaseMessagingService.Companion.CHANNEL_ID
import com.example.rastreamentoinfantil.helper.GeofenceHelper
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.screen.MapScreen
import com.example.rastreamentoinfantil.screen.Navigation
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.service.LocationService
import com.example.rastreamentoinfantil.ui.theme.RastreamentoInfantilTheme
import com.example.rastreamentoinfantil.viewmodel.MainViewModel
import com.example.rastreamentoinfantil.viewmodel.MainViewModelFactory
import com.google.firebase.FirebaseApp
import androidx.lifecycle.lifecycleScope
import com.example.rastreamentoinfantil.helper.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.rastreamentoinfantil.helper.RouteHelper
import com.example.rastreamentoinfantil.helper.LocationPermissionHelper
import com.example.rastreamentoinfantil.service.BackgroundLocationService
import kotlinx.coroutines.Dispatchers

const val GEOFENCE_CHANNEL_ID = "geofence_channel_id"
const val ROUTE_CHANNEL_ID = "route_channel_id"

class MainActivity : FragmentActivity(){

    private lateinit var mainViewModel: MainViewModel
    private lateinit var locationPermissionHelper: LocationPermissionHelper

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val requestLocationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allLocationPermissionsGranted = permissions.entries.all { it.value }
            if (allLocationPermissionsGranted) {
                Log.d("MainActivity", "Todas as permissões de localização concedidas")
                // Iniciar monitoramento em background
                lifecycleScope.launch(Dispatchers.IO) {
                    mainViewModel.startLocationMonitoring()
                }
            } else {
                Log.e("MainActivity", "Permissões de localização negadas")
                Toast.makeText(this, "Permissões de localização são necessárias", Toast.LENGTH_LONG).show()
            }
        }

    // 2. Novo Launcher para a PERMISSÃO ÚNICA DE NOTIFICAÇÃO
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                println("Permissão de notificação CONCEDIDA.")
                Toast.makeText(this, "Permissão de notificação concedida", Toast.LENGTH_SHORT).show()
            } else {
                println("Permissão de notificação NEGADA.")
                Toast.makeText(this, "Permissão de notificação negada. Funcionalidades podem ser limitadas.", Toast.LENGTH_LONG).show()
            }
            // Solicita a permissão de localização logo após a de notificação
            requestLocationPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate iniciado")

        // Inicializa o Firebase primeiro (operação síncrona rápida)
        FirebaseApp.initializeApp(this)

        // Cria os canais de notificação (operação síncrona rápida)
        createNotificationChannels()

        // Inicializa os serviços e ViewModel (operação síncrona rápida)
        val firebaseRepository = FirebaseRepository()
        val locationService = LocationService(this)
        val geocodingService = GeocodingService(this)
        val geofenceHelper = GeofenceHelper()
        val routeHelper = RouteHelper()
        val factory = MainViewModelFactory(application, firebaseRepository, locationService, geocodingService, geofenceHelper, routeHelper)

        mainViewModel = ViewModelProvider(this, factory).get(MainViewModel::class.java)
        
        // Inicializa o helper de permissões
        locationPermissionHelper = LocationPermissionHelper(this)
        setupLocationPermissionCallbacks()

        // Solicita permissões ANTES de liberar a navegação/login
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Se não precisa de permissão de notificação, já pede localização
            requestLocationPermissions()
        }

        // Renderiza a UI imediatamente
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

        // Inicializa operações pesadas em background
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "Iniciando operações em background")
            
            // Inicializa observadores de eventos
            launch {
                mainViewModel.showExitNotificationEvent.collectLatest { geofenceId ->
                    NotificationHelper.showGeofenceExitNotification(this@MainActivity, geofenceId)
                }
            }
            launch {
                mainViewModel.showRouteExitNotificationEvent.collectLatest { routeName ->
                    NotificationHelper.showRouteExitNotification(this@MainActivity, routeName)
                }
            }
            
            // Verifica permissões e inicia monitoramento
            if (hasLocationPermissions()) {
                Log.d("MainActivity", "Permissões OK, iniciando monitoramento de localização")
                mainViewModel.startLocationMonitoring()
            } else {
                Log.d("MainActivity", "Solicitando permissões de localização")
                // Volta para a thread principal para solicitar permissões
                runOnUiThread {
                    requestLocationPermissions()
                }
            }
        }
        
        Log.d("MainActivity", "onCreate finalizado")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para geofence
            val geofenceChannel = NotificationChannel(
                GEOFENCE_CHANNEL_ID,
                "Alertas de Geofence",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de entrada/saída de áreas seguras"
                enableVibration(true)
            }

            // Canal para rota
            val routeChannel = NotificationChannel(
                ROUTE_CHANNEL_ID,
                "Alertas de Rota",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de desvio de rota"
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(geofenceChannel)
            notificationManager.createNotificationChannel(routeChannel)
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return locationPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupLocationPermissionCallbacks() {
        locationPermissionHelper.onLocationPermissionGranted = {
            Log.d("MainActivity", "Permissão de localização concedida")
            // Solicitar permissão de localização em segundo plano
            locationPermissionHelper.requestBackgroundLocationPermission()
        }
        
        locationPermissionHelper.onLocationPermissionDenied = {
            Log.w("MainActivity", "Permissão de localização negada")
            Toast.makeText(this, "Permissão de localização é necessária para o funcionamento do app", Toast.LENGTH_LONG).show()
        }
        
        locationPermissionHelper.onBackgroundLocationPermissionGranted = {
            Log.d("MainActivity", "Permissão de localização em segundo plano concedida")
            startBackgroundLocationService()
        }
        
        locationPermissionHelper.onBackgroundLocationPermissionDenied = {
            Log.w("MainActivity", "Permissão de localização em segundo plano negada")
            Toast.makeText(this, "Permissão de localização em segundo plano é recomendada para monitoramento contínuo", Toast.LENGTH_LONG).show()
            // Mesmo sem permissão em segundo plano, iniciar o serviço normal
            startBackgroundLocationService()
        }
    }
    
    private fun startBackgroundLocationService() {
        Log.d("MainActivity", "startBackgroundLocationService: Iniciando serviço de localização em segundo plano")
        
        val serviceIntent = Intent(this, BackgroundLocationService::class.java).apply {
            action = BackgroundLocationService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun stopBackgroundLocationService() {
        Log.d("MainActivity", "stopBackgroundLocationService: Parando serviço de localização em segundo plano")
        
        val serviceIntent = Intent(this, BackgroundLocationService::class.java).apply {
            action = BackgroundLocationService.ACTION_STOP
        }
        
        startService(serviceIntent)
    }

    private fun requestLocationPermissions() {
        locationPermissionHelper.requestLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        
        // Verificar se o serviço está rodando e reiniciar se necessário
        if (LocationPermissionHelper.hasLocationPermission(this)) {
            startBackgroundLocationService()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Não parar o serviço aqui, deixar rodando em segundo plano
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Parar o serviço apenas se o app for completamente fechado
        // stopBackgroundLocationService()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Toast.makeText(this, "Permissão concedida", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
            }
        }
}
