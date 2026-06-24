package com.blacksamdev.bbsmoove.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksamdev.bbsmoove.model.DangerZoneInfo
import com.blacksamdev.bbsmoove.ui.theme.Bone
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.StateRed
import androidx.compose.foundation.background

/**
 * Alerte plein écran "zone de danger" (terme générique, jamais "radar" --
 * cf. décret 2012 sur la signalisation des contrôles routiers).
 *
 * Fond noir opaque pleine zone -- demande explicite, pas de dégradé
 * transparent comme dans une version précédente de la maquette.
 */
@Composable
fun DangerOverlay(
    dangerInfo: DangerZoneInfo?,
    isDucking: Boolean,
    modifier: Modifier = Modifier,
) {
    val show = dangerInfo?.shouldAlert == true

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WarningTriangle()
                Text(
                    text = "Zone de danger",
                    color = Bone,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = "${dangerInfo?.distanceM?.toInt() ?: 0} m",
                    color = StateRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Réduisez votre vitesse",
                    color = BoneDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (isDucking) {
                    Text(
                        text = "🔉 Musique baissée",
                        color = StateRed,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningTriangle() {
    Canvas(modifier = Modifier.size(58.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = StateRed)
    }
}
