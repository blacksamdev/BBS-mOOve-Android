package com.blacksamdev.bbsmoove.service

import android.content.Context
import android.media.MediaPlayer
import com.blacksamdev.bbsmoove.R
import com.blacksamdev.bbsmoove.model.SpeedState

/**
 * Son directionnel à chaque changement de couleur, selon le SENS.
 *
 * TOUS les sons passent par MediaPlayer et des fichiers WAV embarqués
 * (res/raw) générés à la MÊME amplitude -> volumes homogènes entre eux.
 * (On n'utilise plus ToneGenerator, dont le niveau différait du MediaPlayer
 * et créait un déséquilibre entre alertes et son de retour.)
 *
 *  - Aggravation :
 *      vert -> orange       : double bip aigu (montee_orange)
 *      orange/vert -> rouge : bip franc insistant (montee_rouge)
 *  - Amélioration (retour dans les clous) :
 *      deux bips descendants aigu->grave (retour_descendant)
 *  - Premier passage vers le vert depuis l'arrêt (prev == null) : MUET.
 */
class AlertSoundPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    private fun rank(state: SpeedState): Int = when (state) {
        SpeedState.OK -> 0
        SpeedState.ATTENTION -> 1
        SpeedState.EXCES -> 2
    }

    /**
     * @param prev état précédent (null = premier calcul, au démarrage)
     * @param next nouvel état
     * @param soundGreen/soundOrange/soundRed toggles des options : le son
     *        d'une transition est contrôlé par le toggle de la zone
     *        d'ARRIVÉE (monter en orange -> soundOrange ; revenir au vert
     *        -> soundGreen ; etc.)
     */
    fun playTransition(
        prev: SpeedState?,
        next: SpeedState,
        soundGreen: Boolean = true,
        soundOrange: Boolean = true,
        soundRed: Boolean = true,
    ) {
        // Démarrage : pas de son en entrant dans le vert la première fois.
        if (prev == null) return

        val enabled = when (next) {
            SpeedState.OK -> soundGreen
            SpeedState.ATTENTION -> soundOrange
            SpeedState.EXCES -> soundRed
        }
        if (!enabled) return

        val delta = rank(next) - rank(prev)
        val resId = when {
            delta > 0 && next == SpeedState.EXCES -> R.raw.montee_rouge
            delta > 0 -> R.raw.montee_orange
            delta < 0 -> R.raw.retour_descendant
            else -> return // même état : aucun son
        }
        play(resId)
    }

    /** Son d'alerte à l'entrée en zone de danger (si activé dans les options). */
    fun playDangerAlert() {
        play(R.raw.danger_alert)
    }

    private fun play(resId: Int) {
        // Libère la lecture précédente avant d'en relancer une (évite le
        // cumul de MediaPlayer si les transitions s'enchaînent vite).
        player?.release()
        player = MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            start()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
