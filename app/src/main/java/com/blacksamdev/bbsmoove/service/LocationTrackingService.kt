package com.blacksamdev.bbsmoove.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.blacksamdev.bbsmoove.model.GpsFix
import com.blacksamdev.bbsmoove.ui.HudActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service en avant-plan qui pousse la position/vitesse GPS en continu.
 *
 * On utilise un foreground service (pas juste un callback dans l'Activity)
 * pour deux raisons :
 *  - le tracking GPS continue même si l'écran s'éteint ou que l'app passe
 *    en arrière-plan (cas réel : téléphone qui se verrouille sur son
 *    support pendant la conduite)
 *  - Android exige un foreground service pour la localisation prolongée
 *    depuis Android 8+ (et un type explicite "location" depuis Android 14)
 */
class LocationTrackingService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "bbs_speed_location"
        private const val NOTIF_ID = 1001
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_INTERVAL_MS = 500L

        private val _gpsFix = MutableStateFlow<GpsFix?>(null)

        /** Lecture publique du dernier fix GPS, observable depuis le ViewModel du HUD. */
        val gpsFix: StateFlow<GpsFix?> get() = _gpsFix.asStateFlow()
    }

    private lateinit var fusedClient: FusedLocationProviderClient

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val speedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
            // Le cap GPS (bearing) n'est fiable qu'en mouvement ; à l'arrêt
            // il est aléatoire. On ne le fournit donc qu'au-dessus de ~5 km/h.
            val heading = if (loc.hasBearing() && speedKmh >= 5) loc.bearing else null
            _gpsFix.value = GpsFix(
                lat = loc.latitude,
                lon = loc.longitude,
                speedKmh = speedKmh,
                accuracyM = loc.accuracy,
                headingDeg = heading,
                timestampMs = loc.time,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()

        // NB : la permission ACCESS_FINE_LOCATION doit déjà avoir été
        // accordée avant le démarrage du service (vérifié dans HudActivity).
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        _gpsFix.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null // pas de binding nécessaire, on passe par le StateFlow statique
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BBS Speed — suivi GPS",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HudActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BBS Speed actif")
            .setContentText("Suivi de la vitesse en cours")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // à remplacer par une icône BBS dédiée
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}
