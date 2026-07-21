package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksamdev.bbsmoove.data.Settings
import com.blacksamdev.bbsmoove.ui.theme.BgDeep
import com.blacksamdev.bbsmoove.ui.theme.BgPanel
import com.blacksamdev.bbsmoove.ui.theme.Bone
import com.blacksamdev.bbsmoove.ui.theme.BoneDim
import com.blacksamdev.bbsmoove.ui.theme.Gold
import com.blacksamdev.bbsmoove.ui.theme.StateGreen
import com.blacksamdev.bbsmoove.ui.theme.StateOrange
import com.blacksamdev.bbsmoove.ui.theme.StateRed

/**
 * Panneau d'options plein écran (par-dessus le HUD).
 * Réglages décidés avec Michael :
 *  - Téléchargement WiFi uniquement (off par défaut)
 *  - Zone rouge : seuil km/h (+4) + son (on)
 *  - Zone orange : seuil km/h (+1, < rouge) + son (on)
 *  - Zone verte : son (on)
 *  - Zone danger : distance (900 m) + son (off)
 *  - Écran toujours allumé (on)
 *  - Mode HUD miroir (off)
 */
@Composable
fun SettingsPanel(
    settings: Settings,
    currentLimitKmh: Int,
    currentOsmWayId: Long?,
    onOpenOsm: (Long) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onOrangeThreshold: (Int) -> Unit,
    onRedThreshold: (Int) -> Unit,
    onSoundGreen: (Boolean) -> Unit,
    onSoundOrange: (Boolean) -> Unit,
    onSoundRed: (Boolean) -> Unit,
    onSoundDanger: (Boolean) -> Unit,
    onDangerDistance: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onMirrorMode: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // En-tête : titre + fermeture
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Options", color = Gold, fontSize = 20.sp)
                Text(
                    "✕",
                    color = BoneDim,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp),
                )
            }

            SectionTitle("Général")
            ToggleRow("Cartes en WiFi uniquement", settings.wifiOnlyDownload, onWifiOnly)
            ToggleRow("Écran toujours allumé", settings.keepScreenOn, onKeepScreenOn)
            ToggleRow("Mode miroir (pare-brise)", settings.mirrorMode, onMirrorMode)

            SectionTitle("Zone verte", StateGreen)
            ToggleRow("Son au retour dans les clous", settings.soundGreen, onSoundGreen)

            SectionTitle("Zone orange", StateOrange)
            StepperRow(
                label = "Seuil de dépassement",
                value = settings.orangeThresholdKmh,
                unit = "km/h",
                onDecrement = { onOrangeThreshold(settings.orangeThresholdKmh - 1) },
                onIncrement = { onOrangeThreshold(settings.orangeThresholdKmh + 1) },
                // bornes affichées : 1 .. rouge-1 (contrainte garantie côté repo)
                canDecrement = settings.orangeThresholdKmh > 1,
                canIncrement = settings.orangeThresholdKmh < settings.redThresholdKmh - 1,
            )
            ToggleRow("Son", settings.soundOrange, onSoundOrange)

            SectionTitle("Zone rouge", StateRed)
            StepperRow(
                label = "Seuil de dépassement",
                value = settings.redThresholdKmh,
                unit = "km/h",
                onDecrement = { onRedThreshold(settings.redThresholdKmh - 1) },
                onIncrement = { onRedThreshold(settings.redThresholdKmh + 1) },
                canDecrement = settings.redThresholdKmh > 2,
                canIncrement = settings.redThresholdKmh < 30,
            )
            ToggleRow("Son", settings.soundRed, onSoundRed)

            SectionTitle("Zone de danger", StateRed)
            StepperRow(
                label = "Distance d'alerte",
                value = settings.dangerDistanceM,
                unit = "m",
                step = 100,
                onDecrement = { onDangerDistance(settings.dangerDistanceM - 100) },
                onIncrement = { onDangerDistance(settings.dangerDistanceM + 100) },
                canDecrement = settings.dangerDistanceM > 200,
                canIncrement = settings.dangerDistanceM < 2000,
            )
            ToggleRow("Son d'alerte", settings.soundDanger, onSoundDanger)

            SectionTitle("Données cartographiques")
            if (currentOsmWayId != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .clickable { onOpenOsm(currentOsmWayId) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Limite ici : $currentLimitKmh km/h",
                        color = Bone,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Fausse ? Toucher pour ouvrir cette route sur OpenStreetMap et la corriger",
                        color = Gold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "way $currentOsmWayId",
                        color = BoneDim,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                Text(
                    "Route non identifiée pour l'instant (attendre un point GPS).",
                    color = BoneDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Text(
                "Les limites viennent d'OpenStreetMap, corrigeable par tous. " +
                    "Une correction est reprise à la prochaine mise à jour des cartes.",
                color = BoneDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    accent: androidx.compose.ui.graphics.Color = Gold,
) {
    Text(
        text = title.uppercase(),
        color = accent,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Bone, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = BgDeep,
                uncheckedThumbColor = BoneDim,
                uncheckedTrackColor = BgDeep,
            ),
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
    step: Int = 1,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f) : le label se compresse (ellipse) au lieu de pousser
        // les boutons -/+ hors de l'écran quand il est long.
        Text(
            label,
            color = Bone,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", enabled = canDecrement, onClick = onDecrement)
            Text(
                text = if (unit == "m") "$value $unit" else "+$value $unit",
                color = Gold,
                fontSize = 15.sp,
                modifier = Modifier.width(80.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepperButton("+", enabled = canIncrement, onClick = onIncrement)
        }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = if (enabled) Gold else BoneDim,
        fontSize = 22.sp,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 2.dp),
    )
}
