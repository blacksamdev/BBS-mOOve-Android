package com.blacksamdev.bbsmoove.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Réglages utilisateur du panneau d'options.
 *
 * Valeurs par défaut (décidées avec Michael) :
 *  - Téléchargement WiFi uniquement : OFF
 *  - Seuil orange : +1 km/h (contraint < seuil rouge, appliqué à la lecture)
 *  - Seuil rouge  : +4 km/h
 *  - Son vert / orange / rouge : ON
 *  - Son danger : OFF ; distance de déclenchement danger : 900 m
 *  - Écran toujours allumé : ON
 *  - Mode HUD miroir (projection pare-brise) : OFF
 *  - Consommation OBD : OFF ; cylindrée 1000 cm³ ; étalonnage 100 %
 *  - Ligne de stats : automatique (conso si boîtier connecté, sinon vitesse)
 */
data class Settings(
    val wifiOnlyDownload: Boolean = false,
    val orangeThresholdKmh: Int = 1,
    val redThresholdKmh: Int = 4,
    val soundGreen: Boolean = true,
    val soundOrange: Boolean = true,
    val soundRed: Boolean = true,
    val soundDanger: Boolean = false,
    val dangerDistanceM: Int = 900,
    val keepScreenOn: Boolean = true,
    val mirrorMode: Boolean = false,
    /** Contenu de la bulle overlay : 0=vitesse seule, 1=vitesse+limite, 2=+radar. */
    val overlayContent: Int = 1,
    /** Dernière position X de la bulle (px), -1 = pas encore placée. */
    val overlayX: Int = -1,
    val overlayY: Int = -1,
    /** Lecture de la consommation via un boîtier OBD-II Bluetooth. */
    val obdEnabled: Boolean = false,
    /** Adresse MAC du boîtier choisi parmi les appareils appairés. */
    val obdDeviceAddress: String? = null,
    val obdDeviceName: String? = null,
    /** Cylindrée du moteur, utilisée seulement par l'estimation MAP. */
    val obdDisplacementCc: Int = 1000,
    /** Facteur d'étalonnage de la consommation (1.0 = 100 %), ajusté au plein. */
    val obdCalibration: Double = 1.0,
    /** Ligne de stats du HUD : 0 = vitesse, 1 = consommation, 2 = auto. */
    val statsMode: Int = 2,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
        val ORANGE_THRESHOLD = intPreferencesKey("orange_threshold_kmh")
        val RED_THRESHOLD = intPreferencesKey("red_threshold_kmh")
        val SOUND_GREEN = booleanPreferencesKey("sound_green")
        val SOUND_ORANGE = booleanPreferencesKey("sound_orange")
        val SOUND_RED = booleanPreferencesKey("sound_red")
        val SOUND_DANGER = booleanPreferencesKey("sound_danger")
        val DANGER_DISTANCE = intPreferencesKey("danger_distance_m")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val MIRROR_MODE = booleanPreferencesKey("mirror_mode")
        val OVERLAY_CONTENT = intPreferencesKey("overlay_content")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
        val OBD_ENABLED = booleanPreferencesKey("obd_enabled")
        val OBD_ADDRESS = stringPreferencesKey("obd_device_address")
        val OBD_NAME = stringPreferencesKey("obd_device_name")
        val OBD_DISPLACEMENT = intPreferencesKey("obd_displacement_cc")
        val OBD_CALIBRATION = doublePreferencesKey("obd_calibration")
        val STATS_MODE = intPreferencesKey("stats_mode")
        // Carburant compté depuis le dernier plein (pas exposé dans Settings :
        // il change toutes les 30 s et ne doit pas faire redessiner l'écran).
        val OBD_TANK_LITERS = doublePreferencesKey("obd_tank_liters")
        // Métadonnées de mise à jour des cartes (pas exposées dans Settings)
        val DATA_VERSION = stringPreferencesKey("data_version")
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
    }

    /** Flux des réglages, avec la contrainte orange < rouge appliquée à la lecture. */
    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val red = p[Keys.RED_THRESHOLD] ?: 4
        val orangeRaw = p[Keys.ORANGE_THRESHOLD] ?: 1
        Settings(
            wifiOnlyDownload = p[Keys.WIFI_ONLY] ?: false,
            // La contrainte métier "orange < rouge" est garantie ici quoi
            // qu'il y ait dans le stockage (robuste aux états incohérents).
            orangeThresholdKmh = orangeRaw.coerceAtMost(red - 1),
            redThresholdKmh = red,
            soundGreen = p[Keys.SOUND_GREEN] ?: true,
            soundOrange = p[Keys.SOUND_ORANGE] ?: true,
            soundRed = p[Keys.SOUND_RED] ?: true,
            soundDanger = p[Keys.SOUND_DANGER] ?: false,
            dangerDistanceM = p[Keys.DANGER_DISTANCE] ?: 900,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            mirrorMode = p[Keys.MIRROR_MODE] ?: false,
            overlayContent = p[Keys.OVERLAY_CONTENT] ?: 1,
            overlayX = p[Keys.OVERLAY_X] ?: -1,
            overlayY = p[Keys.OVERLAY_Y] ?: -1,
            obdEnabled = p[Keys.OBD_ENABLED] ?: false,
            obdDeviceAddress = p[Keys.OBD_ADDRESS],
            obdDeviceName = p[Keys.OBD_NAME],
            obdDisplacementCc = p[Keys.OBD_DISPLACEMENT] ?: 1000,
            obdCalibration = p[Keys.OBD_CALIBRATION] ?: 1.0,
            statsMode = p[Keys.STATS_MODE] ?: 2,
        )
    }

    suspend fun setWifiOnly(value: Boolean) =
        context.dataStore.edit { it[Keys.WIFI_ONLY] = value }

    /** Fixe le seuil orange, borné à [1, rouge-1]. */
    suspend fun setOrangeThreshold(value: Int) =
        context.dataStore.edit { p ->
            val red = p[Keys.RED_THRESHOLD] ?: 4
            p[Keys.ORANGE_THRESHOLD] = value.coerceIn(1, red - 1)
        }

    /** Fixe le seuil rouge, borné à [2, 30] ; remonte l'orange si besoin. */
    suspend fun setRedThreshold(value: Int) =
        context.dataStore.edit { p ->
            val red = value.coerceIn(2, 30)
            p[Keys.RED_THRESHOLD] = red
            val orange = p[Keys.ORANGE_THRESHOLD] ?: 1
            if (orange >= red) p[Keys.ORANGE_THRESHOLD] = red - 1
        }

    suspend fun setSoundGreen(value: Boolean) =
        context.dataStore.edit { it[Keys.SOUND_GREEN] = value }

    suspend fun setSoundOrange(value: Boolean) =
        context.dataStore.edit { it[Keys.SOUND_ORANGE] = value }

    suspend fun setSoundRed(value: Boolean) =
        context.dataStore.edit { it[Keys.SOUND_RED] = value }

    suspend fun setSoundDanger(value: Boolean) =
        context.dataStore.edit { it[Keys.SOUND_DANGER] = value }

    /** Distance de déclenchement de l'alerte danger, bornée à [200, 2000] m. */
    suspend fun setDangerDistance(value: Int) =
        context.dataStore.edit { it[Keys.DANGER_DISTANCE] = value.coerceIn(200, 2000) }

    suspend fun setKeepScreenOn(value: Boolean) =
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }

    suspend fun setMirrorMode(value: Boolean) =
        context.dataStore.edit { it[Keys.MIRROR_MODE] = value }

    suspend fun setOverlayContent(value: Int) =
        context.dataStore.edit { it[Keys.OVERLAY_CONTENT] = value.coerceIn(0, 2) }

    /** Mémorise la position de la bulle après un déplacement. */
    suspend fun setOverlayPosition(x: Int, y: Int) =
        context.dataStore.edit { it[Keys.OVERLAY_X] = x; it[Keys.OVERLAY_Y] = y }

    // --- Consommation (OBD) ---

    suspend fun setObdEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.OBD_ENABLED] = value }

    suspend fun setObdDevice(address: String, name: String) =
        context.dataStore.edit { it[Keys.OBD_ADDRESS] = address; it[Keys.OBD_NAME] = name }

    /** Cylindrée bornée à [500, 5000] cm³. */
    suspend fun setObdDisplacement(value: Int) =
        context.dataStore.edit { it[Keys.OBD_DISPLACEMENT] = value.coerceIn(500, 5000) }

    /** Facteur d'étalonnage borné à [0,5 ; 2,0]. */
    suspend fun setObdCalibration(value: Double) =
        context.dataStore.edit { it[Keys.OBD_CALIBRATION] = value.coerceIn(0.5, 2.0) }

    suspend fun setStatsMode(value: Int) =
        context.dataStore.edit { it[Keys.STATS_MODE] = value.coerceIn(0, 2) }

    suspend fun tankLiters(): Double =
        context.dataStore.data.first()[Keys.OBD_TANK_LITERS] ?: 0.0

    suspend fun setTankLiters(value: Double) =
        context.dataStore.edit { it[Keys.OBD_TANK_LITERS] = value.coerceAtLeast(0.0) }

    // --- Métadonnées de mise à jour des cartes ---

    /** Version (published_at GitHub) des données actuellement installées, ou null. */
    suspend fun dataVersion(): String? =
        context.dataStore.data.first()[Keys.DATA_VERSION]

    suspend fun setDataVersion(version: String) =
        context.dataStore.edit { it[Keys.DATA_VERSION] = version }

    /** Horodatage du dernier check de MAJ (pour le cooldown 24 h). */
    suspend fun lastUpdateCheckMs(): Long =
        context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK_MS] ?: 0L

    suspend fun setLastUpdateCheckMs(ms: Long) =
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_MS] = ms }
}
