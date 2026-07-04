package com.blacksamdev.bbsmoove.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import com.blacksamdev.bbsmoove.model.DangerCategory
import com.blacksamdev.bbsmoove.model.DangerZoneInfo
import java.io.File
import java.io.FileOutputStream

/**
 * Schéma SQLite attendu (table danger_zones, importé depuis le CSV
 * data.gouv.fr) :
 *   id INTEGER PRIMARY KEY
 *   lat, lon REAL
 *   limit_kmh INTEGER NULL
 *
 * Index recommandé : CREATE INDEX idx_latlon ON danger_zones(lat, lon);
 */
class DangerZoneRepository(
    private val context: Context,
    private val searchRadiusM: Double = 1200.0,
) {
    @Volatile
    private var db: SQLiteDatabase = openBest(context)
    private val python: PyObject = Python.getInstance().getModule("danger_lookup")

    /** Base radars réellement ouverte (diagnostic HUD). */
    @Volatile
    var activeSource: String = "?"
        private set

    /**
     * Ouvre la meilleure base radars disponible :
     *  1. la base téléchargée (France entière) dans filesDir/regions/radars.db
     *  2. à défaut, l'asset radars.db embarqué (2 zones de test bidon).
     */
    private fun openBest(context: Context): SQLiteDatabase {
        val downloaded = File(File(context.filesDir, "regions"), "radars.db")
        if (downloaded.exists() && downloaded.length() > 1_000) {
            activeSource = "radars.db(dl)"
            return SQLiteDatabase.openDatabase(downloaded.path, null, SQLiteDatabase.OPEN_READONLY)
        }
        activeSource = "radars(bidon)"
        return openFromAssets(context, "radars.db")
    }

    /** Recharge après téléchargement de la base radars réelle. */
    @Synchronized
    fun reload() {
        val old = db
        db = openBest(context)
        if (old !== db) old.close()
    }

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

    /** À appeler depuis un contexte coroutine (Dispatchers.Default), pas le thread UI. */
    fun lookup(lat: Double, lon: Double): DangerZoneInfo? {
        val marginDeg = 0.012 // ~1.3km, un peu plus large que le rayon de recherche fin
        val candidates = zonesNear(lat, lon, marginDeg)
        if (candidates.isEmpty()) return null

        val zonesJson = JSONArray().apply {
            candidates.forEach { z ->
                put(
                    JSONObject().apply {
                        put("lat", z.lat)
                        put("lon", z.lon)
                        put("limit", z.limitKmh ?: JSONObject.NULL)
                        put("category", z.category)
                    }
                )
            }
        }

        val resultStr = python
            .callAttr("nearest_danger_zone", lat, lon, zonesJson.toString(), searchRadiusM)
            ?.toString()
            ?: return null

        if (resultStr == "None") return null

        val obj = JSONObject(resultStr)
        return DangerZoneInfo(
            distanceM = obj.getDouble("distance_m"),
            limitKmh = if (obj.isNull("limit")) null else obj.getInt("limit"),
            shouldAlert = obj.getBoolean("should_alert"),
            category = DangerCategory.fromTag(
                obj.optString("category", "danger")
            ),
        )
    }

    private data class RawZone(
        val lat: Double,
        val lon: Double,
        val limitKmh: Int?,
        val category: String,
    )

    private fun zonesNear(lat: Double, lon: Double, marginDeg: Double): List<RawZone> {
        // La colonne category peut être absente d'anciennes bases -> on la
        // lit défensivement (fallback "danger" si la colonne manque).
        val hasCategory = tableHasColumn("danger_zones", "category")
        val columns = if (hasCategory) {
            arrayOf("lat", "lon", "limit_kmh", "category")
        } else {
            arrayOf("lat", "lon", "limit_kmh")
        }
        val cursor = db.query(
            "danger_zones",
            columns,
            "lat >= ? AND lat <= ? AND lon >= ? AND lon <= ?",
            arrayOf(
                (lat - marginDeg).toString(),
                (lat + marginDeg).toString(),
                (lon - marginDeg).toString(),
                (lon + marginDeg).toString(),
            ),
            null, null, null,
        )
        val results = mutableListOf<RawZone>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    RawZone(
                        lat = it.getDouble(0),
                        lon = it.getDouble(1),
                        limitKmh = if (it.isNull(2)) null else it.getInt(2),
                        category = if (hasCategory && !it.isNull(3)) it.getString(3) else "danger",
                    )
                )
            }
        }
        return results
    }

    private fun tableHasColumn(table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == column) return true
            }
        }
        return false
    }

    fun close() = db.close()
}
