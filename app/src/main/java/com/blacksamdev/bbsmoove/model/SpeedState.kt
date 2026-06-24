package com.blacksamdev.bbsmoove.model

import androidx.compose.ui.graphics.Color

/**
 * État du compteur selon le dépassement de la limite du tronçon.
 *
 * Règle validée :
 *   - speed <= limit            -> OK (vert)
 *   - limit < speed <= limit+3  -> ATTENTION (orange)
 *   - speed > limit+3           -> EXCES (rouge)
 */
enum class SpeedState(val color: Color) {
    OK(Color(0xFF3FAE6B)),
    ATTENTION(Color(0xFFE2932E)),
    EXCES(Color(0xFFD6463F));

    companion object {
        fun from(speedKmh: Int, limitKmh: Int): SpeedState {
            val over = speedKmh - limitKmh
            return when {
                over <= 0 -> OK
                over <= 3 -> ATTENTION
                else -> EXCES
            }
        }
    }
}

/**
 * Fréquences de bip distinctes par état, pour le retour sonore au changement
 * de couleur (un seul bip au moment de la transition, pas en continu).
 */
fun SpeedState.alertToneHz(): Int = when (this) {
    SpeedState.OK -> 660
    SpeedState.ATTENTION -> 440
    SpeedState.EXCES -> 320
}
