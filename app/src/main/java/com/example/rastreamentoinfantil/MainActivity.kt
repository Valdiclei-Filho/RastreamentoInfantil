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
import androidx.lifecycle.lifecycleScope // Para launchWhenStarted
import com.example.rastreamentoinfantil.helper.NotificationHelper
import kotlinx.coroutines.flow.collectLatest // Para coletar o SharedFlow
import kotlinx.coroutines.launch

const val GEOFENCE_CHANNEL_ID = "geofence_channel_id"

class MainActivity : ComponentActivity(){

    private lateinit var mainViewModel: MainViewModel

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

        // 1. Launcher para MÚLTIPLAS PERMISSÕES DE LOCALIZAÇÃO (Já existente e correto)
        private val requestLocationPermissionsLauncher = // Renomeado para clareza, se quiser
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allLocationPermissionsGranted = permissions.entries.all { it.value } // Checa se TODAS as permissões pedidas foram concedidas

                // Filtra para garantir que estamos falando das permissões de localização
                val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

                if (fineLocationGranted && coarseLocationGranted) { // Ou apenas allLocationPermissionsGranted se só pedir localização
                    println("Todas as permissões de localização concedidas.")
                } else {
                    println("Pelo menos uma permissão de localização foi negada.")
                    // Lidar com a negação das permissões de localização
                }
            }

        // 2. Novo Launcher para a PERMISSÃO ÚNICA DE NOTIFICAÇÃO
        private val requestNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    println("Permissão de notificação CONCEDIDA.")
                    // Ação opcional se concedida aqui, como mostrar um Toast
                    Toast.makeText(this, "Permissão de notificação concedida", Toast.LENGTH_SHORT).show()
                } else {
                    println("Permissão de notificação NEGADA.")
                    // Ação opcional se negada, como mostrar um Toast ou diálogo
                    Toast.makeText(this, "Permissão de notificação negada. Funcionalidades podem ser limitadas.", Toast.LENGTH_LONG).show()
                }
            }

        // ... (locationPermissions, checkAndRequestLocationPermissions - usando requestLocationPermissionsLauncher) ...
        private fun checkAndRequestLocationPermissions() {
            val permissionsToRequest = mutableListOf<String>()
            for (permission in locationPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                // Use o launcher correto para localização
                requestLocationPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                println("Todas as permissões de localização já estavam concedidas.")
            }
        }

        private fun askNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                when {
                    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                        println("Permissão de notificação já concedida.")
                    }
                    shouldShowRequestPermissionRationale(permission) -> {
                        println("Mostrar justificativa para permissão de notificação.")
                        // Exemplo: mostrar um AlertDialog aqui explicando por que você precisa da permissão
                        // e então, na ação positiva do diálogo, chamar o launcher:
                        // showRationaleDialogForNotificationPermission {
                        //    requestNotificationPermissionLauncher.launch(permission) // Usa o launcher de notificação
                        // }
                        // Por agora, pedindo diretamente após o log:
                        requestNotificationPermissionLauncher.launch(permission) // <<< USA O LAUNCHER CORRETO (singular)
                    }
                    else -> {
                        requestNotificationPermissionLauncher.launch(permission) // <<< USA O LAUNCHER CORRETO (singular)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestLocationPermissions()

        val firebaseRepository = FirebaseRepository()
        val locationService = LocationService(this)
        val geocodingService = GeocodingService(this)
        val geofenceHelper = GeofenceHelper() // Crie uma instância
        val factory = MainViewModelFactory(application, firebaseRepository, locationService, geocodingService, geofenceHelper)

        // Corrigido: inicializar a propriedade da classe, não uma variável local
        mainViewModel = ViewModelProvider(this, factory).get(MainViewModel::class.java)

        createNotificationChannel() // Crie o canal de notificação
        askNotificationPermission() // Solicite a permissão

        FirebaseApp.initializeApp(this)

        lifecycleScope.launch { // Use launch, ou launchWhenStarted se preferir
            mainViewModel.showExitNotificationEvent.collectLatest { geofenceId ->
                NotificationHelper.showGeofenceExitNotification(this@MainActivity, geofenceId)
            }
        }

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
            val name = getString(R.string.channel_name) // Você precisará adicionar isso em strings.xml
            val descriptionText = getString(R.string.channel_description) // E isso também
            val importance = NotificationManager.IMPORTANCE_HIGH // Alta importância para alertas
            val channel = NotificationChannel(GEOFENCE_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            // Registre o canal com o sistema
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            println("Canal de notificação criado.")
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
}
