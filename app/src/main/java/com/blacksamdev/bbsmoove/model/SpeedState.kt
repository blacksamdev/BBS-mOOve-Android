package com.blacksamdev.bbsmoove.model

import androidx.compose.ui.graphics.Color

/**
 * État du compteur selon le dépassement de la limite du tronçon.
 *
 * Les seuils sont configurables dans les options (défauts : orange à +1,
 * rouge à +4, avec la contrainte orange < rouge garantie par le
 * SettingsRepository) :
 *   - over < orangeAt            -> OK (vert)
 *   - orangeAt <= over < redAt   -> ATTENTION (orange)
 *   - over >= redAt              -> EXCES (rouge)
 */
enum class SpeedState(val color: Color) {
    OK(Color(0xFF3FAE6B)),
    ATTENTION(Color(0xFFE2932E)),
    EXCES(Color(0xFFD6463F));

    companion object {
        fun from(
            speedKmh: Int,
            limitKmh: Int,
            orangeAt: Int = 1,
            redAt: Int = 4,
        ): SpeedState {
            val over = speedKmh - limitKmh
            return when {
                over >= redAt -> EXCES
                over >= orangeAt -> ATTENTION
                else -> OK
            }
        }
    }
}
