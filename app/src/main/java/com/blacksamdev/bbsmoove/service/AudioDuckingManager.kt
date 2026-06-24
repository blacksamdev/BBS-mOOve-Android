package com.blacksamdev.bbsmoove.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Baisse temporairement le volume de la musique pendant une alerte zone de
 * danger (comme Coyote), puis le restaure. Fonctionne avec n'importe quel
 * lecteur respectant le focus audio (Groove, Spotify, etc.) -- pas besoin
 * de coordination spécifique avec Groove, c'est une API système standard.
 */
class AudioDuckingManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var isDucking = false

    /** À appeler quand l'alerte zone de danger se déclenche. */
    fun startDucking() {
        if (isDucking) return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .build()

        val result = audioManager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
            isDucking = true
        }
    }

    /** À appeler quand l'alerte se termine (zone de danger dépassée). */
    fun stopDucking() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        isDucking = false
    }
}
