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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksamdev.bbsmoove.data.RegionDownloadManager
import com.blacksamdev.bbsmoove.ui.theme.BgDeep
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.Gold
import com.blacksamdev.bbsmoove.ui.theme.GoldDim
import com.blacksamdev.bbsmoove.ui.theme.StateRed
import android.content.res.Configuration
import kotlinx.coroutines.launch

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
    val settings by viewModel.settings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDucking = uiState.dangerInfo?.shouldAlert == true && uiState.nowPlaying != null

    // Écran toujours allumé (option, on par défaut) : indispensable pour un
    // HUD -- sans ça le téléphone se met en veille en pleine conduite.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Mode miroir (projection pare-brise) : tout le HUD est inversé
    // horizontalement pour que son reflet sur le pare-brise soit à l'endroit.
    // Le panneau d'options, lui, reste NON inversé (on le manipule en main).
    Surface(color = BgDeep, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (settings.mirrorMode) -1f else 1f },
        ) {
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

            // Bouton d'options discret (coin haut-gauche).
            Text(
                text = "⚙",
                color = BoneDim,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clickable { showSettings = true }
                    .padding(start = 10.dp, top = 6.dp, end = 12.dp, bottom = 12.dp),
            )
        }

        // Panneau d'options par-dessus tout, HORS du bloc miroir : on le
        // manipule téléphone en main, il doit rester à l'endroit.
        if (showSettings) {
            val repo = viewModel.settingsRepository()
            SettingsPanel(
                settings = settings,
                onWifiOnly = { v -> scope.launch { repo.setWifiOnly(v) } },
                onOrangeThreshold = { v -> scope.launch { repo.setOrangeThreshold(v) } },
                onRedThreshold = { v -> scope.launch { repo.setRedThreshold(v) } },
                onSoundGreen = { v -> scope.launch { repo.setSoundGreen(v) } },
                onSoundOrange = { v -> scope.launch { repo.setSoundOrange(v) } },
                onSoundRed = { v -> scope.launch { repo.setSoundRed(v) } },
                onSoundDanger = { v -> scope.launch { repo.setSoundDanger(v) } },
                onDangerDistance = { v -> scope.launch { repo.setDangerDistance(v) } },
                onKeepScreenOn = { v -> scope.launch { repo.setKeepScreenOn(v) } },
                onMirrorMode = { v -> scope.launch { repo.setMirrorMode(v) } },
                onClose = { showSettings = false },
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
