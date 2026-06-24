package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.blacksamdev.bbsmoove.ui.theme.BgDeep
import android.content.res.Configuration

/**
 * Split 50/50 qui suit l'orientation physique du téléphone :
 *  - portrait : zone 1 (compteur) en HAUT, zone 2 (bonus) en BAS
 *  - paysage  : zone 1 (compteur) à GAUCHE, zone 2 (bonus) à DROITE
 *
 * Décision actée dès le départ de la conversation : "Gauche et haut
 * doivent avoir le compteur de vitesse".
 */
@Composable
fun HudScreen(viewModel: HudViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDucking = uiState.dangerInfo?.shouldAlert == true && uiState.nowPlaying != null

    Surface(color = BgDeep, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    SpeedZone(
                        speedKmh = uiState.speedKmh,
                        limitKmh = uiState.limitKmh,
                        speedState = uiState.speedState,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        InfoZone(
                            nowPlaying = uiState.nowPlaying,
                            accuracyM = uiState.accuracyM,
                            avgSpeedKmh = uiState.avgSpeedKmh,
                            maxSpeedKmh = uiState.maxSpeedKmh,
                            tripDurationSec = uiState.tripDurationSec,
                            isDucking = isDucking,
                        )
                        DangerOverlay(uiState.dangerInfo, isDucking)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SpeedZone(
                        speedKmh = uiState.speedKmh,
                        limitKmh = uiState.limitKmh,
                        speedState = uiState.speedState,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        InfoZone(
                            nowPlaying = uiState.nowPlaying,
                            accuracyM = uiState.accuracyM,
                            avgSpeedKmh = uiState.avgSpeedKmh,
                            maxSpeedKmh = uiState.maxSpeedKmh,
                            tripDurationSec = uiState.tripDurationSec,
                            isDucking = isDucking,
                        )
                        DangerOverlay(uiState.dangerInfo, isDucking)
                    }
                }
            }
        }
    }
}
