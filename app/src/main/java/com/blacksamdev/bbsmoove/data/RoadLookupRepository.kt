package com.blacksamdev.bbsmoove.data

import android.content.Context
import com.blacksamdev.bbsmoove.model.Junction
import com.blacksamdev.bbsmoove.model.RoadInfo
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository pour la limite de vitesse / type de jonction du tronçon courant.
 *
 * Flux :
 *  1. RoadDb (SQLite, assets/db/osm_speed.db) -> pré-filtre par bounding box
 *     autour de la position (rapide, indexé) pour ne récupérer qu'une
 *     poignée de segments candidats.
 *  2. road_lookup.nearest_segment (Python) -> calcule la distance exacte
 *     point-segment et choisit le bon tronçon parmi les candidats.
 *
 * Ce découpage évite de charger toute la base en mémoire à chaque update
 * GPS : SQLite fait le gros filtrage, Python fait juste l'affinage fin.
 */
class RoadLookupRepository(
    context: Context,
    private val searchRadiusM: Double = 80.0,
) {
    private val roadDb = RoadDb(context)
    private val python: PyObject = Python.getInstance().getModule("road_lookup")

    /**
     * À appeler depuis un contexte coroutine (Dispatchers.Default), pas le thread UI.
     *
     * @param heading cap GPS courant en degrés (0-360), ou null si indispo/à l'arrêt
     * @param accuracyM précision GPS en mètres, ou null
     * @param prevSegmentId id du tronçon retenu au tick précédent (continuité), ou null
     */
    fun lookup(
        lat: Double,
        lon: Double,
        heading: Float? = null,
        accuracyM: Float? = null,
        prevSegmentId: Long? = null,
    ): RoadInfo? {
        val candidates = roadDb.segmentsNear(lat, lon, marginDeg = 0.0015) // ~150m
        if (candidates.isEmpty()) return null

        val segmentsJson = JSONArray().apply {
            candidates.forEach { seg ->
                put(
                    JSONObject().apply {
                        put("id", seg.id)
                        put("points", JSONArray(seg.points.map { JSONArray(listOf(it.first, it.second)) }))
                        put("maxspeed", seg.maxspeed ?: JSONObject.NULL)
                        put("junction", seg.junction ?: JSONObject.NULL)
                        put("is_agglomeration", seg.isAgglomeration)
                    }
                )
            }
        }

        val resultStr = python
            .callAttr(
                "nearest_segment",
                lat, lon, segmentsJson.toString(), searchRadiusM,
                heading?.toDouble() ?: -1.0,
                accuracyM?.toDouble() ?: -1.0,
                prevSegmentId ?: -1L,
            )
            ?.toString()
            ?: return null

        if (resultStr == "None") return null

        val obj = JSONObject(resultStr)
        return RoadInfo(
            limitKmh = obj.getInt("limit"),
            junction = Junction.fromTag(obj.optString("junction", null)),
            distanceM = obj.getDouble("distance_m"),
            segmentId = if (obj.isNull("id")) null else obj.getLong("id"),
        )
    }

    /** Recharge la base après un téléchargement de région réussi. */
    fun reload() = roadDb.reload()

    fun close() = roadDb.close()
}
