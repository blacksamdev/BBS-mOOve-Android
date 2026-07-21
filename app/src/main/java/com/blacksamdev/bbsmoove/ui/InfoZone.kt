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
import com.blacksamdev.bbsmoove.model.NowPlaying
import com.blacksamdev.bbsmoove.ui.theme.Bone
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.BgPanel2
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (nowPlaying != null) {
            MediaPanel(nowPlaying, isDucking)
        } else {
            GpsPanel(accuracyM)
        }

        StatsRow(avgSpeedKmh, maxSpeedKmh, tripDurationSec)
    }
}

@Composable
private fun MediaPanel(nowPlaying: NowPlaying, isDucking: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(56.dp)
                .background(BgPanel2, RoundedCornerShape(6.dp))
                .border(1.dp, LineColor, RoundedCornerShape(6.dp)),
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
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Text("♪", color = GoldDim, fontSize = 18.sp)
            }
        }
        Text(
            text = nowPlaying.title,
            color = Bone,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = nowPlaying.artist,
            color = BoneDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("⏮", color = androidx.compose.ui.graphics.Color(0xFFC9A227), fontSize = 16.sp)
            Text("⏸", color = androidx.compose.ui.graphics.Color(0xFFC9A227), fontSize = 16.sp)
            Text("⏭", color = androidx.compose.ui.graphics.Color(0xFFC9A227), fontSize = 16.sp)
        }
        if (isDucking) {
            Text(
                text = "🔉 Volume musique réduit",
                color = StateRed,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
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
