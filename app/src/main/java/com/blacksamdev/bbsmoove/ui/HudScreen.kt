package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksamdev.bbsmoove.ui.theme.BgDeep
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
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

            // Badge de version discret (coin bas-gauche) : permet de vérifier
            // d'un coup d'œil quelle build tourne réellement sur le téléphone.
            Text(
                text = "v${com.blacksamdev.bbsmoove.BuildConfig.VERSION_NAME}",
                color = BoneDim,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp),
            )
        }
    }
}
