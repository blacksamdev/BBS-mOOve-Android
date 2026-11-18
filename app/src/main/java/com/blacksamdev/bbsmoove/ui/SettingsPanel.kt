package com.blacksamdev.bbsmoove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksamdev.bbsmoove.data.Settings
import com.blacksamdev.bbsmoove.obd.BtDeviceInfo
import kotlin.math.roundToInt
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
 *  - Consommation via boîtier OBD-II (off) + étalonnage au plein
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
    onOverlayContent: (Int) -> Unit,
    obdStatusText: String,
    obdDevices: List<BtDeviceInfo>,
    obdTankLiters: Double,
    onObdEnabled: (Boolean) -> Unit,
    onObdDevice: (BtDeviceInfo) -> Unit,
    onObdDisplacement: (Int) -> Unit,
    onObdCalibrate: (Double, (String) -> Unit) -> Unit,
    onObdResetTank: () -> Unit,
    onObdResetCalibration: () -> Unit,
    onStatsMode: (Int) -> Unit,
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

            SectionTitle("Bulle flottante (mode compact)")
            Text(
                "Contenu de la bulle affichée par-dessus les autres apps (Waze…).",
                color = BoneDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgPanel)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val options = listOf(
                    0 to "Vitesse seule",
                    1 to "Vitesse + limite",
                    2 to "Vitesse + limite + radar",
                )
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOverlayContent(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (settings.overlayContent == value) "◉" else "○",
                            color = if (settings.overlayContent == value) Gold else BoneDim,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Text(label, color = Bone, fontSize = 14.sp)
                    }
                }
            }
            Text(
                "Depuis le HUD, touchez ❏ pour réduire en bulle. Un appui long " +
                    "sur la bulle (petite vibration) permet de la déplacer ; " +
                    "un appui court rouvre l'écran complet.",
                color = BoneDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )

            SectionTitle("Consommation (boîtier OBD)")
            ToggleRow("Lire la consommation", settings.obdEnabled, onObdEnabled)
            if (!settings.obdEnabled) {
                Hint(
                    "Nécessite un boîtier OBD-II ELM327 Bluetooth branché sur la " +
                        "prise diagnostic de la voiture, appairé au téléphone.",
                )
            } else {
                Hint("État : $obdStatusText")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("Boîtier Bluetooth", color = Bone, fontSize = 14.sp)
                    if (obdDevices.isEmpty()) {
                        Text(
                            "Aucun appareil appairé. Appaire d'abord le boîtier dans " +
                                "les réglages Bluetooth du téléphone (code souvent 1234 ou 0000).",
                            color = BoneDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    obdDevices.forEach { device ->
                        RadioLine(
                            selected = device.address == settings.obdDeviceAddress,
                            label = device.name,
                            detail = device.address,
                            onClick = { onObdDevice(device) },
                        )
                    }
                }

                StepperRow(
                    label = "Cylindrée",
                    value = settings.obdDisplacementCc,
                    unit = "cm³",
                    onDecrement = { onObdDisplacement(settings.obdDisplacementCc - 50) },
                    onIncrement = { onObdDisplacement(settings.obdDisplacementCc + 50) },
                    canDecrement = settings.obdDisplacementCc > 500,
                    canIncrement = settings.obdDisplacementCc < 5000,
                )
                Hint(
                    "Sert seulement si la voiture ne transmet ni débit de carburant " +
                        "ni débit d'air : la consommation est alors estimée à partir " +
                        "de la pression d'admission. Arrondis au 50 le plus proche.",
                )

                // Étalonnage au plein
                var litersText by remember { mutableStateOf("") }
                var message by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("Étalonnage au plein", color = Bone, fontSize = 14.sp)
                    Text(
                        "Compté depuis le dernier plein : ${fmt1(obdTankLiters)} L · " +
                            "facteur ${(settings.obdCalibration * 100).roundToInt()} %",
                        color = BoneDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = litersText,
                        onValueChange = { text ->
                            litersText = text.filter { it.isDigit() || it == ',' || it == '.' }
                        },
                        label = { Text("Litres mis à la pompe") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Bone,
                            unfocusedTextColor = Bone,
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = BoneDim,
                            focusedLabelColor = Gold,
                            unfocusedLabelColor = BoneDim,
                            cursorColor = Gold,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        ActionButton("Étalonner") {
                            val liters = litersText.replace(',', '.').toDoubleOrNull()
                            if (liters == null || liters <= 0.0) {
                                message = "Saisis les litres affichés par la pompe."
                            } else {
                                onObdCalibrate(liters) { result ->
                                    message = result
                                    litersText = ""
                                }
                            }
                        }
                        ActionButton("Plein sans étalonner") {
                            onObdResetTank()
                            message = "Compteur remis à zéro."
                        }
                    }
                    message?.let {
                        Text(
                            it,
                            color = Gold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Hint(
                    "Fais le plein à ras bord. Au premier plein, touche « Plein sans " +
                        "étalonner » pour démarrer le compteur. Aux pleins suivants, " +
                        "saisis les litres affichés par la pompe puis « Étalonner » : " +
                        "l'app corrige son calcul. Un ou deux pleins suffisent.",
                )
                Row(modifier = Modifier.padding(horizontal = 14.dp)) {
                    ActionButton("Remettre l'étalonnage à 100 %") {
                        onObdResetCalibration()
                        message = "Étalonnage remis à 100 %."
                    }
                }

                Text(
                    "Ligne du bas du HUD",
                    color = Bone,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    listOf(
                        2 to "Automatique : consommation si le boîtier est connecté",
                        1 to "Toujours la consommation",
                        0 to "Toujours la vitesse (moyenne, max, durée)",
                    ).forEach { (value, label) ->
                        RadioLine(
                            selected = settings.statsMode == value,
                            label = label,
                            onClick = { onStatsMode(value) },
                        )
                    }
                }
                Hint(
                    "Pense à débrancher le boîtier si la voiture reste plusieurs " +
                        "jours sans rouler : il est alimenté en permanence et vide " +
                        "doucement la batterie.",
                )
            }

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
                // Le « + » n'a de sens que pour les seuils de dépassement.
                text = if (unit == "km/h") "+$value $unit" else "$value $unit",
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
private fun Hint(text: String) {
    Text(
        text,
        color = BoneDim,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun RadioLine(
    selected: Boolean,
    label: String,
    detail: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "◉" else "○",
            color = if (selected) Gold else BoneDim,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column {
            Text(label, color = Bone, fontSize = 14.sp)
            if (detail != null) {
                Text(detail, color = BoneDim, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Gold,
        fontSize = 12.sp,
        modifier = Modifier
            .border(1.dp, Gold, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun fmt1(value: Double): String = "%.1f".format(value).replace('.', ',')

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
