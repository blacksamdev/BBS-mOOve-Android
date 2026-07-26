package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import com.blacksamdev.bbsmoove.model.NowPlaying
import com.blacksamdev.bbsmoove.ui.theme.Bone
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.BgPanel2
import com.blacksamdev.bbsmoove.ui.theme.Gold
import com.blacksamdev.bbsmoove.ui.theme.GoldDim
import com.blacksamdev.bbsmoove.ui.theme.LineColor
import com.blacksamdev.bbsmoove.ui.theme.StateRed

/**
 * Zone 2 du HUD : musique (niveau 1, MediaSession) si une lecture est
 * active, sinon précision GPS en comblement -- décision actée : "rien de
 * spécial, on met la qualité du GPS pour combler le vide".
 */
@Composable
fun InfoZone(
    nowPlaying: NowPlaying?,
    accuracyM: Float,
    avgSpeedKmh: Int,
    maxSpeedKmh: Int,
    tripDurationSec: Int,
    isDucking: Boolean,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Marge basse plus grande : le badge diagnostic (version, bases,
            // seg/osm) est affiché en bas de l'écran et venait chevaucher la
            // rangée de stats.
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (nowPlaying != null) {
            // Le panneau média prend sa hauteur naturelle (contenu ancré en
            // haut à l'intérieur), et le weight pousse les stats en bas tout
            // en gardant le média groupé -- pas de boutons qui flottent vers
            // les stats en paysage.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MediaPanel(
                    nowPlaying = nowPlaying,
                    isDucking = isDucking,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }
        } else {
            GpsPanel(accuracyM)
        }

        StatsRow(avgSpeedKmh, maxSpeedKmh, tripDurationSec)
    }
}

@Composable
private fun MediaPanel(
    nowPlaying: NowPlaying,
    isDucking: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availH = maxHeight.value
        val availW = maxWidth.value

        // Tailles de texte/boutons calées sur la LARGEUR du bloc (stable dans
        // les deux orientations), pas sur la hauteur.
        val titleSize = (availW * 0.055f).coerceIn(13f, 20f)
        val artistSize = titleSize * 0.8f
        val btnSize = (availW * 0.07f).coerceIn(20f, 30f)

        // On réserve la hauteur du texte + des boutons ; la pochette prend le
        // reste, bornée AUSSI par la largeur (sinon démesurée en paysage).
        val reservedDp =
            titleSize * 1.35f +
            artistSize * 1.35f +
            btnSize * 1.7f +
            (if (isDucking) 16f else 0f) +
            36f

        val artSize = minOf(
            (availW * 0.5f).dp,
            (availH - reservedDp).dp,
        ).coerceIn(40.dp, 180.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(artSize)
                    .background(BgPanel2, RoundedCornerShape(8.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Pochette fournie par le lecteur (Groove, Spotify...) si dispo,
                // sinon la note de musique en repli.
                val art = nowPlaying.artwork
                if (art != null) {
                    androidx.compose.foundation.Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Text("♪", color = GoldDim, fontSize = (artSize.value * 0.32f).sp)
                }
            }
            Text(
                text = nowPlaying.title,
                color = Bone,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
            Text(
                text = nowPlaying.artist,
                color = BoneDim,
                fontSize = artistSize.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            )
            // Boutons RÉELLEMENT actifs : ils pilotent le lecteur via la
            // MediaSession (comme les boutons du casque).
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TransportButton("⏮", btnSize, onPrevious)
                // L'icône reflète l'état réel de lecture.
                TransportButton(if (nowPlaying.isPlaying) "⏸" else "▶", btnSize, onPlayPause)
                TransportButton("⏭", btnSize, onNext)
            }
            if (isDucking) {
                Text(
                    text = "🔉 Volume musique réduit",
                    color = StateRed,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TransportButton(symbol: String, sizeSp: Float, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = Gold,
        fontSize = sizeSp.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            // Zone tactile large : on doit pouvoir viser sans quitter la route
            // des yeux plus d'un instant.
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun GpsPanel(accuracyM: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⛛", color = GoldDim, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
        Column {
            Text(
                text = "± ${"%.1f".format(accuracyM).replace('.', ',')} m",
                color = Bone,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
            Text(
                text = "PRÉCISION GPS",
                color = BoneDim,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun StatsRow(avgSpeedKmh: Int, maxSpeedKmh: Int, tripDurationSec: Int) {
    val minutes = tripDurationSec / 60
    val seconds = tripDurationSec % 60
    val duration = "%02d:%02d".format(minutes, seconds)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem("MOY.", "$avgSpeedKmh km/h")
        StatItem("MAX", "$maxSpeedKmh km/h")
        StatItem("DURÉE", duration)
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = BoneDim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(
            value,
            color = Bone,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
