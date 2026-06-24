package com.blacksamdev.bbsmoove.service

import android.media.AudioManager
import android.media.ToneGenerator
import com.blacksamdev.bbsmoove.model.SpeedState

/**
 * Un seul bip à chaque CHANGEMENT de couleur (pas en continu tant qu'on
 * reste dans le même état) -- comportement validé dans la maquette HTML.
 * ToneGenerator évite d'avoir à embarquer des fichiers .wav/.mp3 pour 3 sons.
 */
class AlertSoundPlayer {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    fun playFor(state: SpeedState) {
        val tone = when (state) {
            SpeedState.OK -> ToneGenerator.TONE_PROP_BEEP
            SpeedState.ATTENTION -> ToneGenerator.TONE_PROP_BEEP2
            SpeedState.EXCES -> ToneGenerator.TONE_SUP_ERROR
        }
        toneGenerator.startTone(tone, 180)
    }

    fun release() = toneGenerator.release()
}
