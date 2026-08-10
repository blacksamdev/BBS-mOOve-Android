package com.blacksamdev.bbsmoove.service

import com.blacksamdev.bbsmoove.model.SpeedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État minimal du HUD (vitesse, limite, couleur, danger) publié pour que la
 * bulle overlay puisse l'afficher sans refaire les lookups. Le HudViewModel
 * le met à jour à chaque calcul ; l'OverlayService l'observe.
 *
 * Objet statique : une seule source de vérité, lisible par le service même
 * quand l'activité n'est pas au premier plan.
 */
object HudSnapshot {

    data class Data(
        val speedKmh: Int = 0,
        val limitKmh: Int = 50,
        val state: SpeedState = SpeedState.OK,
        val dangerActive: Boolean = false,
        val dangerDistanceM: Int? = null,
    )

    private val _data = MutableStateFlow(Data())
    val data: StateFlow<Data> = _data.asStateFlow()

    fun update(
        speedKmh: Int,
        limitKmh: Int,
        state: SpeedState,
        dangerActive: Boolean,
        dangerDistanceM: Int?,
    ) {
        _data.value = Data(speedKmh, limitKmh, state, dangerActive, dangerDistanceM)
    }
}
