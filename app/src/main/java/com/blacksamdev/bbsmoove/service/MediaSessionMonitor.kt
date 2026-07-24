package com.blacksamdev.bbsmoove.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import com.blacksamdev.bbsmoove.model.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Écoute l'API système MediaSession : fonctionne avec BBS Groove comme avec
 * n'importe quel lecteur (Spotify, YouTube Music...) -- "ce qui joue", point.
 *
 * Nécessite la permission spéciale "Accès aux notifications", que l'utilisateur
 * doit activer À LA MAIN dans les réglages système. Tant qu'elle manque, aucun
 * média ne peut être détecté : on expose donc [permissionGranted] pour que le
 * HUD puisse le signaler et proposer d'ouvrir les réglages (avant, l'échec
 * était silencieux et donnait l'impression que la fonction ne marchait pas).
 */
class MediaSessionMonitor(private val context: Context) {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** False tant que l'utilisateur n'a pas accordé l'accès aux notifications. */
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val listenerComponent = ComponentName(context, NotificationListener::class.java)

    /** Contrôleur suivi actuellement, avec son callback (pour le détacher). */
    private var trackedController: MediaController? = null

    /**
     * Callback attaché au contrôleur actif : sans lui, on ne serait prévenu
     * que des changements de LISTE de sessions, pas des changements de morceau
     * ni de pause/lecture -- l'affichage resterait figé sur le premier titre.
     */
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publish(trackedController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish(trackedController)
        }

        override fun onSessionDestroyed() {
            detachController()
            refreshSessions()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateFromControllers(controllers.orEmpty())
        }

    /** Vrai si l'accès aux notifications est accordé à cette app. */
    fun isNotificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(":").any { it.contains(context.packageName) }
    }

    fun start() {
        val granted = isNotificationAccessGranted()
        _permissionGranted.value = granted
        if (!granted) {
            _nowPlaying.value = null
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener, listenerComponent,
            )
            refreshSessions()
        } catch (e: SecurityException) {
            _permissionGranted.value = false
            _nowPlaying.value = null
        }
    }

    /** À rappeler quand l'app revient au premier plan (permission peut avoir changé). */
    fun refresh() {
        if (!_permissionGranted.value && isNotificationAccessGranted()) {
            start() // la permission vient d'être accordée : on (re)démarre
        } else if (_permissionGranted.value) {
            refreshSessions()
        }
    }

    private fun refreshSessions() {
        try {
            updateFromControllers(sessionManager.getActiveSessions(listenerComponent).orEmpty())
        } catch (e: SecurityException) {
            _permissionGranted.value = false
            _nowPlaying.value = null
        }
    }

    fun stop() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            // ignore
        }
        detachController()
    }

    // --- Commandes de lecture ---
    // Elles sont envoyées au lecteur actif (Groove, Spotify...) via la
    // MediaSession : c'est la même API que les boutons du casque ou de
    // l'écran de verrouillage.

    fun playPause() {
        val controller = trackedController ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        trackedController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        trackedController?.transportControls?.skipToPrevious()
    }

    private fun detachController() {
        trackedController?.unregisterCallback(controllerCallback)
        trackedController = null
    }

    private fun updateFromControllers(controllers: List<MediaController>) {
        // On privilégie une session en cours de lecture, sinon la première.
        val active = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (active == null) {
            detachController()
            _nowPlaying.value = null
            return
        }

        // Change de contrôleur suivi si besoin (ex : on passe de Groove à Spotify)
        if (trackedController?.sessionToken != active.sessionToken) {
            detachController()
            trackedController = active
            active.registerCallback(controllerCallback)
        }
        publish(active)
    }

    private fun publish(controller: MediaController?) {
        if (controller == null) {
            _nowPlaying.value = null
            return
        }
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "—"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "—"
        // La pochette peut arriver sous plusieurs clés selon le lecteur.
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            artwork = artwork,
        )
    }
}
