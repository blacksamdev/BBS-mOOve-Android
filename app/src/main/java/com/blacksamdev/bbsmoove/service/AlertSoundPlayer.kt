package com.blacksamdev.bbsmoove.service

import android.media.AudioManager
import android.media.ToneGenerator
import com.blacksamdev.bbsmoove.model.SpeedState

/**
 * Son directionnel à chaque changement de couleur, selon le SENS :
 *
 *  - Aggravation (on accélère / on franchit un seuil vers le haut) :
 *      vert -> orange   : double bip d'attention
 *      orange/vert -> rouge : bip d'erreur franc
 *  - Amélioration (on ralentit, on revient dans les clous) :
 *      rouge -> orange, orange -> vert, rouge -> vert : son doux distinct
 *  - Premier passage vers le vert depuis l'arrêt (prev == null) : MUET,
 *    pour ne pas biper bêtement au démarrage.
 *
 * ToneGenerator évite d'embarquer des fichiers audio.
 */
class AlertSoundPlayer {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    private fun rank(state: SpeedState): Int = when (state) {
        SpeedState.OK -> 0
        SpeedState.ATTENTION -> 1
        SpeedState.EXCES -> 2
    }

    /**
     * @param prev état précédent (null = premier calcul, au démarrage)
     * @param next nouvel état
     */
    fun playTransition(prev: SpeedState?, next: SpeedState) {
        // Démarrage : pas de son en entrant dans le vert la première fois.
        if (prev == null) return

        val delta = rank(next) - rank(prev)
        when {
            delta > 0 -> {
                // Aggravation : son d'alerte selon le niveau atteint.
                val tone = if (next == SpeedState.EXCES) {
                    ToneGenerator.TONE_SUP_ERROR      // rouge : bip franc
                } else {
                    ToneGenerator.TONE_PROP_BEEP2     // orange : double bip
                }
                toneGenerator.startTone(tone, 180)
            }
            delta < 0 -> {
                // Amélioration : un seul son doux, quel que soit le palier
                // regagné (rouge->orange, orange->vert, rouge->vert).
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            }
            // delta == 0 : même état, aucun son (ne devrait pas arriver ici).
        }
    }

    fun release() = toneGenerator.release()
}
