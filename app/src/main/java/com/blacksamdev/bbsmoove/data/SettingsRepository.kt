package com.blacksamdev.bbsmoove.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
}
