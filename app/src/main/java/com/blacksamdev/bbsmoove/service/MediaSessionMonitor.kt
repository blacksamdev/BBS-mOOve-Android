package com.blacksamdev.bbsmoove.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.blacksamdev.bbsmoove.model.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Niveau 1 retenu : on écoute l'API système MediaSession plutôt que de
 * coder un lien spécifique à BBS Groove. Conséquence assumée : ça marche
 * aussi avec Spotify, Deezer, etc. -- pas que Groove -- ce qui est en fait
 * la bonne propriété pour un HUD générique ("ce qui joue", point.).
 *
 * Nécessite une permission spéciale "Notification access" accordée par
 * l'utilisateur dans les réglages système (pas une permission runtime
 * classique) -- cf. checklist de test en fin de fichier projet.
 *
 * Un BBS_SPEED_NOTIFICATION_LISTENER (NotificationListenerService) doit
 * être déclaré dans le manifest pour que MediaSessionManager renvoie des
 * contrôleurs valides ; voir NotificationListener.kt.
 */
class MediaSessionMonitor(private val context: Context) {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val listenerComponent = ComponentName(context, NotificationListener::class.java)

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateFromControllers(controllers.orEmpty())
        }

    fun start() {
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener, listenerComponent,
            )
            updateFromControllers(
                sessionManager.getActiveSessions(listenerComponent).orEmpty(),
            )
        } catch (e: SecurityException) {
            // Permission "Notification access" pas encore accordée par l'utilisateur.
            _nowPlaying.value = null
        }
    }

    fun stop() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    private fun updateFromControllers(controllers: List<android.media.session.MediaController>) {
        val active = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (active == null) {
            _nowPlaying.value = null
            return
        }

        val metadata = active.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "—"
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "—"
        val isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING

        _nowPlaying.value = NowPlaying(title = title, artist = artist, isPlaying = isPlaying)
    }
}
