package com.example.rastreamentoinfantil.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class LocationPermissionHelper(private val activity: FragmentActivity) {
    
    companion object {
        private const val TAG = "LocationPermissionHelper"
        
        // Códigos de permissão
        const val PERMISSION_REQUEST_CODE = 100
        const val BACKGROUND_PERMISSION_REQUEST_CODE = 101
        
        // Verificar se tem permissão de localização básica
        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        // Verificar se tem permissão de localização em segundo plano
        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Para versões anteriores ao Android 10, não é necessária
            }
        }
        
        // Verificar se deve mostrar explicação para permissão
        fun shouldShowLocationPermissionRationale(activity: Activity): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        // Verificar se deve mostrar explicação para permissão em segundo plano
        fun shouldShowBackgroundLocationPermissionRationale(activity: Activity): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                false
            }
        }
    }
    
    // Launcher para permissão de localização básica
    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        Log.d(TAG, "locationPermissionLauncher: FINE_LOCATION=$fineLocationGranted, COARSE_LOCATION=$coarseLocationGranted")
        
        if (fineLocationGranted || coarseLocationGranted) {
            Log.d(TAG, "locationPermissionLauncher: Permissão de localização concedida")
            onLocationPermissionGranted?.invoke()
        } else {
            Log.w(TAG, "locationPermissionLauncher: Permissão de localização negada")
            onLocationPermissionDenied?.invoke()
        }
    }
    
    // Launcher para permissão de localização em segundo plano
    private val backgroundLocationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "backgroundLocationPermissionLauncher: BACKGROUND_LOCATION=$isGranted")
        
        if (isGranted) {
            Log.d(TAG, "backgroundLocationPermissionLauncher: Permissão de localização em segundo plano concedida")
            onBackgroundLocationPermissionGranted?.invoke()
        } else {
            Log.w(TAG, "backgroundLocationPermissionLauncher: Permissão de localização em segundo plano negada")
            onBackgroundLocationPermissionDenied?.invoke()
        }
    }
    
    // Callbacks
    var onLocationPermissionGranted: (() -> Unit)? = null
    var onLocationPermissionDenied: (() -> Unit)? = null
    var onBackgroundLocationPermissionGranted: (() -> Unit)? = null
    var onBackgroundLocationPermissionDenied: (() -> Unit)? = null
    
    // Solicitar permissão de localização básica
    fun requestLocationPermission() {
        Log.d(TAG, "requestLocationPermission: Solicitando permissão de localização")
        
        val permissions = mutableListOf<String>()
        
        // Adicionar permissões necessárias
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            locationPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d(TAG, "requestLocationPermission: Permissões já concedidas")
            onLocationPermissionGranted?.invoke()
        }
    }
    
    // Solicitar permissão de localização em segundo plano
    fun requestBackgroundLocationPermission() {
        Log.d(TAG, "requestBackgroundLocationPermission: Solicitando permissão de localização em segundo plano")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(TAG, "requestBackgroundLocationPermission: Android < 10, permissão não necessária")
            onBackgroundLocationPermissionGranted?.invoke()
            return
        }
        
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "requestBackgroundLocationPermission: Permissão já concedida")
            onBackgroundLocationPermissionGranted?.invoke()
            return
        }
        
        // Verificar se tem permissão básica primeiro
        if (!hasLocationPermission(activity)) {
            Log.w(TAG, "requestBackgroundLocationPermission: Permissão básica não concedida")
            requestLocationPermission()
            return
        }
        
        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    
    // Abrir configurações do app para permissão manual
    fun openAppSettings() {
        Log.d(TAG, "openAppSettings: Abrindo configurações do app")
        
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        activity.startActivity(intent)
    }
    
    // Verificar se deve ignorar otimização de bateria
    fun shouldRequestIgnoreBatteryOptimization(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    
    // Abrir configurações de otimização de bateria
    fun openBatteryOptimizationSettings() {
        Log.d(TAG, "openBatteryOptimizationSettings: Abrindo configurações de otimização de bateria")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }
} 