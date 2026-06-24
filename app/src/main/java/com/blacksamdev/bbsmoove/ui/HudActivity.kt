package com.blacksamdev.bbsmoove.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.blacksamdev.bbsmoove.service.LocationTrackingService
import com.blacksamdev.bbsmoove.ui.theme.BbsMooveTheme

class HudActivity : ComponentActivity() {

    private val viewModel: HudViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            startLocationService()
        }
        // Si refusé : l'UI retombe sur speedKmh=0 / accuracy=0 via le ViewModel,
        // pas de crash, mais le HUD reste inerte -- prévoir un message à l'écran
        // si on veut guider l'utilisateur vers les réglages (TODO UX).
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Écran toujours allumé pendant l'utilisation (support voiture)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ensurePermissionsThenStart()

        setContent {
            BbsMooveTheme {
                HudScreen(viewModel = viewModel)
            }
        }
    }

    private fun ensurePermissionsThenStart() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startLocationService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        stopService(Intent(this, LocationTrackingService::class.java))
        super.onDestroy()
    }
}
