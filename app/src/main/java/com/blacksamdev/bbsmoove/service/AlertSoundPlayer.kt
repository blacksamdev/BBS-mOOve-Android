package com.blacksamdev.bbsmoove.service

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.blacksamdev.bbsmoove.R
import com.blacksamdev.bbsmoove.model.SpeedState

/**
 * Son directionnel à chaque changement de couleur, selon le SENS :
 *
 *  - Aggravation (on accélère / on franchit un seuil vers le haut) :
 *      vert -> orange       : double bip d'attention (ToneGenerator)
 *      orange/vert -> rouge : bip d'erreur franc (ToneGenerator)
 *  - Amélioration (on ralentit, on revient dans les clous) :
 *      deux bips DESCENDANTS aigu->grave (fichier res/raw embarqué), car
 *      ToneGenerator ne sait pas produire un grave pur -> franchement
 *      distinct des bips aigus d'alerte.
 *  - Premier passage vers le vert depuis l'arrêt (prev == null) : MUET.
 */
class AlertSoundPlayer(private val context: Context) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    private var descendPlayer: MediaPlayer? = null

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
                // Amélioration : deux bips descendants (fichier embarqué).
                playDescending()
            }
            // delta == 0 : même état, aucun son.
        }
    }

    private fun playDescending() {
        // Libère une éventuelle lecture précédente avant d'en relancer une.
        descendPlayer?.release()
        descendPlayer = MediaPlayer.create(context, R.raw.retour_descendant)?.apply {
            setOnCompletionListener {
                it.release()
                if (descendPlayer === it) descendPlayer = null
            }
            start()
        }
    }

    fun release() {
        toneGenerator.release()
        descendPlayer?.release()
        descendPlayer = null
    }
}
