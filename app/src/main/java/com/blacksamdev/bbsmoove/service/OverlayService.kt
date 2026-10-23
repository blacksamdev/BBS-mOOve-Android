package com.blacksamdev.bbsmoove.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.blacksamdev.bbsmoove.data.SettingsRepository
import com.blacksamdev.bbsmoove.model.SpeedState
import com.blacksamdev.bbsmoove.ui.HudActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Affiche la bulle flottante (mode compact) par-dessus les autres apps.
 *
 * Interactions :
 *  - TAP court  -> rouvre mOOve en plein écran (et retire la bulle)
 *  - APPUI LONG -> petite vibration, puis on peut déplacer la bulle au doigt ;
 *    la position est mémorisée.
 *
 * Contenu réglable (options) : vitesse seule / vitesse+limite / +radar.
 * L'overlay lit HudSnapshot, alimenté par le HUD -> pas de lookup dupliqué.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubble: LinearLayout
    private lateinit var speedText: TextView
    private lateinit var limitText: TextView
    private lateinit var dangerText: TextView
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var settingsRepo: SettingsRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null

    private val vibrator: Vibrator by lazy {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settingsRepo = SettingsRepository(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        // Le GPS doit tourner pour alimenter HudSnapshot même si l'activité
        // est en arrière-plan ou détruite : on le (re)démarre par sécurité.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, LocationTrackingService::class.java))
        } else {
            startService(Intent(this, LocationTrackingService::class.java))
        }
        buildBubble()
        observeSnapshot()
    }

    private fun startForegroundWithNotification() {
        val channelId = "bbs_moove_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Bulle BBS mOOve", NotificationManager.IMPORTANCE_LOW,
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HudActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BBS mOOve")
            .setContentText("Bulle active — toucher pour ouvrir")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun buildBubble() {
        // Conteneur vertical sombre, arrondi via un fond dessiné à la main.
        bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6101418"))
            setPadding(28, 20, 28, 20)
        }
        speedText = TextView(this).apply {
            setTextColor(Color.parseColor("#3FAE6B"))
            textSize = 30f
        }
        limitText = TextView(this).apply {
            setTextColor(Color.parseColor("#C9C2B0"))
            textSize = 14f
        }
        dangerText = TextView(this).apply {
            setTextColor(Color.parseColor("#D6463F"))
            textSize = 12f
        }
        bubble.addView(speedText)
        bubble.addView(limitText)
        bubble.addView(dangerText)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Position initiale : mémorisée si dispo, sinon coin haut-gauche décalé.
        scope.launch {
            val s = settingsRepo.settings.first()
            params.x = if (s.overlayX >= 0) s.overlayX else 40
            params.y = if (s.overlayY >= 0) s.overlayY else 200
            runCatching { windowManager.updateViewLayout(bubble, params) }
        }

        attachTouchHandling()
        windowManager.addView(bubble, params)
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    // --- Tap vs appui long + déplacement ---

    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var dragging = false
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        // Passage en mode déplacement : petite vibration de confirmation.
        longPressFired = true
        dragging = true
        vibrate()
    }

    private fun attachTouchHandling() {
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dragging = false
                    longPressFired = false
                    // Arme l'appui long (600 ms).
                    bubble.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    // Avant l'appui long : un mouvement franc annule le long-press
                    // (l'utilisateur fait défiler, pas un appui) sans déplacer.
                    if (!dragging && (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP)) {
                        bubble.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(bubble, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    bubble.removeCallbacks(longPressRunnable)
                    val dx = abs(event.rawX - touchStartX)
                    val dy = abs(event.rawY - touchStartY)
                    if (dragging) {
                        // Fin de déplacement : on mémorise la position.
                        val fx = params.x
                        val fy = params.y
                        scope.launch { settingsRepo.setOverlayPosition(fx, fy) }
                    } else if (!longPressFired && dx < TOUCH_SLOP && dy < TOUCH_SLOP) {
                        // Tap court : rouvre le HUD plein écran et retire la bulle.
                        openHudFullScreen()
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Geste interrompu par le système : on annule proprement
                    // l'appui long en attente, sans tap ni déplacement.
                    bubble.removeCallbacks(longPressRunnable)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun openHudFullScreen() {
        startActivity(
            Intent(this, HudActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        stopSelf() // on quitte le mode compact
    }

    /**
     * Petite vibration de confirmation de l'appui long. Ne doit JAMAIS faire
     * planter l'app : sans la permission VIBRATE, Vibrator lève une
     * SecurityException qui tuait le processus (et donc la bulle). On se
     * replie sur le retour haptique de la vue, qui ne demande aucune
     * permission.
     */
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (e: Exception) {
            bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // --- Mise à jour de l'affichage depuis HudSnapshot + réglage contenu ---

    private fun observeSnapshot() {
        collectJob = scope.launch {
            // Le contenu réglé (0/1/2) est lu une fois puis suivi.
            settingsRepo.settings.collect { s ->
                render(HudSnapshot.data.value, s.overlayContent)
            }
        }
        scope.launch {
            HudSnapshot.data.collect { data ->
                val content = settingsRepo.settings.first().overlayContent
                render(data, content)
            }
        }
    }

    private fun render(data: HudSnapshot.Data, content: Int) {
        val color = when (data.state) {
            SpeedState.OK -> "#3FAE6B"
            SpeedState.ATTENTION -> "#E2932E"
            SpeedState.EXCES -> "#D6463F"
        }
        speedText.setTextColor(Color.parseColor(color))
        speedText.text = "${data.speedKmh}"

        // content : 0 = vitesse seule, 1 = +limite, 2 = +radar
        limitText.visibility = if (content >= 1) View.VISIBLE else View.GONE
        limitText.text = "/ ${data.limitKmh}"

        val showDanger = content >= 2 && data.dangerActive
        dangerText.visibility = if (showDanger) View.VISIBLE else View.GONE
        dangerText.text = data.dangerDistanceM?.let { "⚠ ${it} m" } ?: "⚠"
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        collectJob?.cancel()
        bubble.removeCallbacks(longPressRunnable)
        runCatching { windowManager.removeView(bubble) }
    }

    companion object {
        private const val NOTIF_ID = 4201
        private const val LONG_PRESS_MS = 600L
        private const val TOUCH_SLOP = 24f

        /** Vrai tant que la bulle est affichée (l'activité s'y réfère pour
         *  ne pas couper le GPS en mode compact). */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
