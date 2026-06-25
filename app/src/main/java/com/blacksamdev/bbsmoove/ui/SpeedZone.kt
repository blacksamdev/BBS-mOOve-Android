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
        // On exploite toute la place dispo. Le facteur largeur dépend du
        // nombre de chiffres (1, 2 ou 3 : "9", "90", "180") pour que même
        // un "180" tienne en largeur sans déborder.
        val digitCount = speedKmh.coerceAtLeast(0).toString().length
        // Ratio d'un chiffre 7-seg : largeur ≈ 0.52 × hauteur (44/84).
        // Largeur totale ≈ digitCount × 0.52 × h + espacements.
        val maxByHeight = maxHeight * 0.62f
        val maxByWidth = (maxWidth * 0.88f) / (digitCount * 0.60f)
        val digitHeight = minOf(maxByHeight, maxByWidth)

        // Badge limite ≈ 3/4 de la hauteur d'un chiffre
        val badgeSize = digitHeight * 0.75f

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
                fontSize = 14.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // Badge limite : décentré bas-droite, ton atténué (pas blanc pur),
        // taille proportionnelle aux chiffres de vitesse.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp)
                .size(badgeSize)
                .shadow(2.dp, CircleShape)
                .background(BgPanel, CircleShape)
                .border(2.dp, BoneDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = limitKmh.toString(),
                color = BoneDim,
                fontWeight = FontWeight.Bold,
                fontSize = (badgeSize.value * 0.34f).sp,
            )
        }
    }
}
