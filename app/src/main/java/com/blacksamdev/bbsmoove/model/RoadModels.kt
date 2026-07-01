package com.blacksamdev.bbsmoove.model

/** Résultat du lookup road_lookup.py côté Kotlin. */
data class RoadInfo(
    val limitKmh: Int,
    val junction: Junction?,
    val distanceM: Double,
    val segmentId: Long?,
)

enum class Junction {
    ROUNDABOUT,
    INTERSECTION;

    companion object {
        fun fromTag(tag: String?): Junction? = when (tag) {
            "roundabout" -> ROUNDABOUT
            "intersection" -> INTERSECTION
            else -> null
        }
    }
}

/** Résultat du lookup danger_lookup.py côté Kotlin. */
data class DangerZoneInfo(
    val distanceM: Double,
    val limitKmh: Int?,
    val shouldAlert: Boolean,
    val category: DangerCategory,
)

enum class DangerCategory {
    DANGER,      // radar de vitesse -> alerte rouge "Zone de danger"
    ATTENTION;   // franchissement -> alerte orange "Zone d'attention"

    companion object {
        fun fromTag(tag: String?): DangerCategory =
            if (tag == "attention") ATTENTION else DANGER
    }
}

/** Position + vitesse + précision telles que fournies par FusedLocationProviderClient. */
data class GpsFix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Int,
    val accuracyM: Float,
    val headingDeg: Float?,   // cap réel, null si à l'arrêt / indispo
    val timestampMs: Long,
)

/** Métadonnées média telles qu'exposées par MediaSession (niveau 1 retenu). */
data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artworkUri: String? = null,
)
