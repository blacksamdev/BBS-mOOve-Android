package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.blacksamdev.bbsmoove.data.RegionDownloadManager
import com.blacksamdev.bbsmoove.ui.theme.BgDeep
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.Gold
import com.blacksamdev.bbsmoove.ui.theme.GoldDim
import com.blacksamdev.bbsmoove.ui.theme.StateRed
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
    val downloadState by viewModel.downloadState.collectAsState()
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

            // Badge diagnostic (coin bas-gauche) : version + base routes
            // réellement chargée + dernière erreur de lookup s'il y en a une.
            // Permet de diagnostiquer sans adb.
            val lookupError by viewModel.lookupError.collectAsState()
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp),
            ) {
                lookupError?.let { err ->
                    Text(
                        text = err,
                        color = StateRed,
                        fontSize = 9.sp,
                    )
                }
                Text(
                    text = "v${com.blacksamdev.bbsmoove.BuildConfig.VERSION_NAME} · ${viewModel.roadDbSource()} · ${viewModel.radarDbSource()}",
                    color = BoneDim,
                    fontSize = 9.sp,
                )
            }

            // Bouton "Télécharger ma région" + progression (coin haut-droite).
            RegionDownloadControl(
                state = downloadState,
                onDownload = { viewModel.downloadCurrentRegion() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun RegionDownloadControl(
    state: RegionDownloadManager.State,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is RegionDownloadManager.State.Ready -> {
            // Carte prête : on n'affiche rien (le bouton a disparu).
        }
        is RegionDownloadManager.State.Downloading -> {
            Text(
                text = "Téléchargement carte… ${state.percent}%",
                color = Gold,
                fontSize = 11.sp,
                modifier = modifier,
            )
        }
        is RegionDownloadManager.State.Decompressing -> {
            Text(
                text = "Décompression…",
                color = Gold,
                fontSize = 11.sp,
                modifier = modifier,
            )
        }
        is RegionDownloadManager.State.Error -> {
            Text(
                text = "Erreur : ${state.message} — toucher pour réessayer",
                color = StateRed,
                fontSize = 11.sp,
                modifier = modifier.clickable { onDownload() },
            )
        }
        RegionDownloadManager.State.Idle -> {
            Text(
                text = "⬇ Télécharger ma région",
                color = Gold,
                fontSize = 12.sp,
                modifier = modifier
                    .border(1.dp, GoldDim, RoundedCornerShape(4.dp))
                    .clickable { onDownload() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
