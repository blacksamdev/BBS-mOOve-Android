"""
Génère des bases SQLite d'exemple pour permettre un premier lancement de
l'app sans avoir encore importé le vrai extrait OSM / CSV radars.

A REMPLACER par le vrai pipeline d'import avant publication -- ce sont des
données factices (les mêmes que celles utilisées dans test_road_danger_lookup.py)
juste pour que assets/db/*.db existent et que le schema soit correct.
"""
import json
import sqlite3
from pathlib import Path

OUT_DIR = Path("/home/claude/bbs-moove-android/app/src/main/assets/db")
OUT_DIR.mkdir(parents=True, exist_ok=True)


def build_osm_speed_db():
    path = OUT_DIR / "osm_speed.db"
    path.unlink(missing_ok=True)
    conn = sqlite3.connect(path)
    conn.execute(
        """
        CREATE TABLE road_segments (
            id INTEGER PRIMARY KEY,
            min_lat REAL, max_lat REAL,
            min_lon REAL, max_lon REAL,
            points_json TEXT,
            maxspeed INTEGER,
            junction TEXT,
            is_agglomeration INTEGER
        )
        """
    )
    conn.execute(
        "CREATE INDEX idx_bbox ON road_segments(min_lat, max_lat, min_lon, max_lon)"
    )

    sample_segments = [
        {
            "points": [[46.7800, 4.8500], [46.7820, 4.8500]],
            "maxspeed": 50,
            "junction": None,
            "is_agglomeration": 1,
        },
        {
            "points": [[46.7700, 4.8400], [46.7720, 4.8400]],
            "maxspeed": None,
            "junction": None,
            "is_agglomeration": 0,
        },
        {
            "points": [[46.7810, 4.8600], [46.7812, 4.8602]],
            "maxspeed": 30,
            "junction": "roundabout",
            "is_agglomeration": 1,
        },
    ]

    for seg in sample_segments:
        lats = [p[0] for p in seg["points"]]
        lons = [p[1] for p in seg["points"]]
        conn.execute(
            """
            INSERT INTO road_segments
                (min_lat, max_lat, min_lon, max_lon, points_json, maxspeed, junction, is_agglomeration)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                min(lats), max(lats), min(lons), max(lons),
                json.dumps(seg["points"]),
                seg["maxspeed"],
                seg["junction"],
                seg["is_agglomeration"],
            ),
        )
    conn.commit()
    conn.close()
    print(f"OK -> {path} ({len(sample_segments)} segments)")


def build_radars_db():
    path = OUT_DIR / "radars.db"
    path.unlink(missing_ok=True)
    conn = sqlite3.connect(path)
    conn.execute(
        """
        CREATE TABLE danger_zones (
            id INTEGER PRIMARY KEY,
            lat REAL,
            lon REAL,
            limit_kmh INTEGER,
            category TEXT
        )
        """
    )
    conn.execute("CREATE INDEX idx_latlon ON danger_zones(lat, lon)")

    # Coordonnées volontairement en plein Atlantique (0,0 est dans le golfe
    # de Guinée) : la base bidon ne doit JAMAIS déclencher une fausse alerte
    # sur une route réelle si jamais elle est encore utilisée par erreur.
    # Les vraies zones viennent de radars.db téléchargé.
    sample_zones = [
        (0.0, 0.0, 50, "danger"),
        (0.001, 0.001, 90, "danger"),
    ]
    conn.executemany(
        "INSERT INTO danger_zones (lat, lon, limit_kmh, category) VALUES (?, ?, ?, ?)",
        sample_zones,
    )
    conn.commit()
    conn.close()
    print(f"OK -> {path} ({len(sample_zones)} zones)")


if __name__ == "__main__":
    build_osm_speed_db()
    build_radars_db()
