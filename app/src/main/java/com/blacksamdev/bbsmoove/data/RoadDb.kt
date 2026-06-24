package com.blacksamdev.bbsmoove.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Représente un tronçon candidat lu depuis osm_speed.db.
 *
 * Schéma SQLite attendu (table road_segments) :
 *   id INTEGER PRIMARY KEY
 *   min_lat, max_lat, min_lon, max_lon REAL   -- bounding box pré-calculée
 *   points_json TEXT                          -- "[[lat,lon], [lat,lon], ...]"
 *   maxspeed INTEGER NULL
 *   junction TEXT NULL                        -- "roundabout" | "intersection" | NULL
 *   is_agglomeration INTEGER                  -- 0/1
 *
 * Index recommandé : CREATE INDEX idx_bbox ON road_segments(min_lat, max_lat, min_lon, max_lon);
 */
data class CandidateSegment(
    val points: List<Pair<Double, Double>>,
    val maxspeed: Int?,
    val junction: String?,
    val isAgglomeration: Boolean,
)

class RoadDb(context: Context, dbAssetName: String = "osm_speed.db") {

    private val db: SQLiteDatabase = openFromAssets(context, dbAssetName)

    /** Copie la DB depuis les assets vers un emplacement accessible en lecture/écriture SQLite. */
    private fun openFromAssets(context: Context, assetName: String): SQLiteDatabase {
        val dbFile = File(context.getDatabasePath(assetName).path)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open("db/$assetName").use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * Renvoie les segments dont la bounding box intersecte la zone de
     * recherche autour de (lat, lon). C'est un pré-filtre grossier mais
     * rapide (indexé) -- le calcul de distance fin est fait ensuite côté
     * Python (road_lookup.nearest_segment).
     */
    fun segmentsNear(lat: Double, lon: Double, marginDeg: Double): List<CandidateSegment> {
        val minLat = lat - marginDeg
        val maxLat = lat + marginDeg
        val minLon = lon - marginDeg
        val maxLon = lon + marginDeg

        val cursor = db.query(
            "road_segments",
            arrayOf("points_json", "maxspeed", "junction", "is_agglomeration"),
            "max_lat >= ? AND min_lat <= ? AND max_lon >= ? AND min_lon <= ?",
            arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString()),
            null, null, null,
        )

        val results = mutableListOf<CandidateSegment>()
        cursor.use {
            while (it.moveToNext()) {
                val pointsJson = it.getString(0)
                val maxspeed = if (it.isNull(1)) null else it.getInt(1)
                val junction = if (it.isNull(2)) null else it.getString(2)
                val isAgglo = it.getInt(3) == 1

                val points = parsePointsJson(pointsJson)
                results.add(CandidateSegment(points, maxspeed, junction, isAgglo))
            }
        }
        return results
    }

    private fun parsePointsJson(json: String): List<Pair<Double, Double>> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val pair = arr.getJSONArray(i)
            pair.getDouble(0) to pair.getDouble(1)
        }
    }

    fun close() = db.close()
}
