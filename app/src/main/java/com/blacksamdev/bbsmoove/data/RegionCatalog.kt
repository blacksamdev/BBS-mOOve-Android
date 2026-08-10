package com.blacksamdev.bbsmoove.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.hypot

/**
 * Catalogue des régions (anciennes régions Geofabrik) chargé depuis
 * assets/regions.json. Sert à détecter dans quelle région se trouve une
 * position GPS, et à connaître les régions limitrophes.
 */
class RegionCatalog(context: Context) {

    data class Region(
        val code: String,       // nom de fichier Geofabrik (ex "bourgogne")
        val displayName: String,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
        val neighbors: List<String>,
    ) {
        val centerLat get() = (minLat + maxLat) / 2
        val centerLon get() = (minLon + maxLon) / 2

        fun contains(lat: Double, lon: Double): Boolean =
            lat in minLat..maxLat && lon in minLon..maxLon
    }

    private val regions: Map<String, Region>

    init {
        val json = context.assets.open("regions.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json).getJSONObject("regions")
        val map = mutableMapOf<String, Region>()
        root.keys().forEach { code ->
            val r = root.getJSONObject(code)
            val bbox = r.getJSONArray("bbox")
            val neigh = r.getJSONArray("neighbors")
            val neighbors = (0 until neigh.length()).map { neigh.getString(it) }
            map[code] = Region(
                code = code,
                displayName = r.getString("name"),
                minLat = bbox.getDouble(0),
                maxLat = bbox.getDouble(1),
                minLon = bbox.getDouble(2),
                maxLon = bbox.getDouble(3),
                neighbors = neighbors,
            )
        }
        regions = map
    }

    fun byCode(code: String): Region? = regions[code]

    /**
     * Région contenant le point. Les bounding boxes se chevauchent en
     * bordure : en cas d'ambiguïté, on prend celle dont le CENTRE est le plus
     * proche du point (heuristique simple et robuste sans polygones).
     * Renvoie null si le point n'est dans aucune région (hors métropole).
     */
    fun detectRegion(lat: Double, lon: Double): Region? {
        val candidates = regions.values.filter { it.contains(lat, lon) }
        return when (candidates.size) {
            0 -> null
            1 -> candidates.first()
            else -> candidates.minByOrNull {
                hypot(it.centerLat - lat, it.centerLon - lon)
            }
        }
    }

    /**
     * Régions limitrophes de la région donnée que le point "approche" : on
     * les propose en anticipation quand on roule près d'une frontière. Ici,
     * simple renvoi de la liste des voisins déclarés.
     */
    fun neighborsOf(code: String): List<Region> =
        regions[code]?.neighbors?.mapNotNull { regions[it] } ?: emptyList()
}
