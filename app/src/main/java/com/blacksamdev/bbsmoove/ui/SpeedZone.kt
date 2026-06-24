package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import com.blacksamdev.bbsmoove.model.SpeedState
import com.blacksamdev.bbsmoove.ui.theme.Bone
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.BgPanel

/**
 * Zone 1 du HUD : le compteur de vitesse.
 *
 * Reprend les décisions de la maquette :
 *  - chiffres 7-segments écartés (espacement déjà géré dans SevenSegmentNumber)
 *  - badge de limite décentré (bas-droite, pas centré) et atténué (BoneDim,
 *    pas blanc pur) -- demande explicite "moins blanche"
 *  - pas de cadran rond autour (retiré sur demande)
 */
@Composable
fun SpeedZone(
    speedKmh: Int,
    limitKmh: Int,
    speedState: SpeedState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val digitHeight = (minOf(maxWidth, maxHeight) * 0.32f)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SevenSegmentNumber(
                speedKmh = speedKmh,
                color = speedState.color,
                modifier = Modifier.height(digitHeight),
            )
            Text(
                text = "km / h",
                color = speedState.color,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // Badge limite : décentré bas-droite, ton atténué (pas blanc pur)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 32.dp, bottom = 24.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(BgPanel, CircleShape)
                .border(2.dp, BoneDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = limitKmh.toString(),
                color = BoneDim,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            )
        }
    }
}
