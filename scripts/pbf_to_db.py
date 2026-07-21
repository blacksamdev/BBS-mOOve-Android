#!/usr/bin/env python3
"""
pbf_to_db.py — convertit un extrait OSM (.osm.pbf ou .osm) en une base
SQLite légère contenant UNIQUEMENT les routes carrossables avec leur
limite de vitesse et leur géométrie.

Pensé pour tourner côté CI (GitHub Action), PAS sur le téléphone : le
résultat .db est ensuite téléchargé tel quel par l'app.

Schéma de sortie (identique à ce qu'attend RoadDb.kt dans l'app) :
    road_segments(
        id, min_lat, max_lat, min_lon, max_lon,
        points_json, maxspeed, junction, is_agglomeration
    )

Usage :
    python3 pbf_to_db.py saone-et-loire-latest.osm.pbf 71.db
"""

import gzip
import json
import os
import shutil
import sqlite3
import sys

import osmium

# Types de voies qu'on garde (carrossables). On ignore pistes cyclables,
# chemins piétons, sentiers, etc. -- inutiles pour un HUD de vitesse auto.
DRIVABLE_HIGHWAYS = {
    "motorway", "motorway_link",
    "trunk", "trunk_link",
    "primary", "primary_link",
    "secondary", "secondary_link",
    "tertiary", "tertiary_link",
    "unclassified",
    "residential",
    "living_street",
    # "service" retiré : voies de parking / allées privées, sans limite
    # réglementaire pertinente pour un HUD de vitesse, et très nombreuses
    # (gros contributeur au poids de la base pour zéro intérêt).
}


def parse_maxspeed(raw):
    """
    Convertit un tag maxspeed OSM en entier km/h, ou None si non exploitable.
    Gère les cas courants : "50", "50 km/h", "FR:urban", "none", "walk".
    """
    if raw is None:
        return None
    raw = raw.strip().lower()

    # Valeurs spéciales françaises implicites -> on laisse None pour
    # retomber sur le défaut agglo/hors-agglo côté lookup
    if raw in ("none", "signals", "variable", "fr:urban", "fr:rural", "fr:motorway"):
        return None
    if raw == "walk":
        return 6

    # "50 km/h" / "50 kmh" / "50"
    token = raw.replace("km/h", "").replace("kmh", "").strip()
    try:
        return int(float(token))
    except ValueError:
        return None


class RoadHandler:
    """Collecte les segments routiers via l'API FileProcessor de pyosmium 4.x."""

    def __init__(self):
        self.rows = []
        self.skipped_no_geom = 0

    def process_way(self, w):
        tags = w.tags
        highway = tags.get("highway")
        if highway not in DRIVABLE_HIGHWAYS:
            return

        # Géométrie : on a besoin des coordonnées de chaque node de la way.
        # FileProcessor avec .with_locations() remplit w.nodes[i].location.
        # 5 décimales = ~1 m de résolution, largement suffisant pour savoir
        # sur quel tronçon on est (6 décimales = 10 cm = précision inutile
        # ici, qui ne ferait qu'alourdir la base).
        points = []
        for n in w.nodes:
            if not n.location.valid():
                continue
            points.append([round(n.location.lat, 5), round(n.location.lon, 5)])

        if len(points) < 2:
            self.skipped_no_geom += 1
            return

        lats = [p[0] for p in points]
        lons = [p[1] for p in points]

        maxspeed = parse_maxspeed(tags.get("maxspeed"))
        junction = tags.get("junction")  # "roundabout" notamment, sinon None
        if junction not in ("roundabout",):
            junction = None

        # Heuristique agglomération : en France, residential/living_street
        # sont quasi toujours en agglo (et donc 50 par défaut si pas de tag).
        is_agglo = 1 if highway in ("residential", "living_street") else 0

        self.rows.append(
            (
                w.id,  # id OSM de la way : permet de pointer l'objet à corriger
                min(lats), max(lats), min(lons), max(lons),
                json.dumps(points, separators=(",", ":")),
                maxspeed,
                junction,
                is_agglo,
            )
        )


def convert(input_path, output_path):
    handler = RoadHandler()

    # FileProcessor avec localisation des nodes activée (nécessaire pour
    # récupérer la géométrie des ways). On ne traite que les ways.
    fp = osmium.FileProcessor(input_path).with_locations().with_filter(
        osmium.filter.KeyFilter("highway")
    )
    for obj in fp:
        if obj.is_way():
            handler.process_way(obj)

    # Écriture SQLite
    conn = sqlite3.connect(output_path)
    conn.execute("DROP TABLE IF EXISTS road_segments")
    conn.execute(
        """
        CREATE TABLE road_segments (
            id INTEGER PRIMARY KEY,
            osm_id INTEGER,
            min_lat REAL, max_lat REAL,
            min_lon REAL, max_lon REAL,
            points_json TEXT,
            maxspeed INTEGER,
            junction TEXT,
            is_agglomeration INTEGER
        )
        """
    )
    conn.executemany(
        """
        INSERT INTO road_segments
            (osm_id, min_lat, max_lat, min_lon, max_lon, points_json, maxspeed, junction, is_agglomeration)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        handler.rows,
    )
    conn.execute(
        "CREATE INDEX idx_bbox ON road_segments(min_lat, max_lat, min_lon, max_lon)"
    )
    conn.commit()
    # VACUUM : récupère l'espace libre et compacte le fichier SQLite
    # (ne supprime aucune donnée, réorganise juste le stockage).
    conn.execute("VACUUM")
    conn.commit()
    conn.close()

    raw_size = os.path.getsize(output_path)

    # Compression gzip : un SQLite plein de géométrie JSON se compresse très
    # bien. L'app téléchargera le .db.gz et le décompressera à l'arrivée ;
    # la base finale est identique au bit près, on n'allège que le transfert
    # et le stockage du téléchargement.
    gz_path = str(output_path) + ".gz"
    with open(output_path, "rb") as f_in, gzip.open(gz_path, "wb", compresslevel=9) as f_out:
        shutil.copyfileobj(f_in, f_out)
    gz_size = os.path.getsize(gz_path)

    print(f"OK -> {output_path}")
    print(f"   segments écrits : {len(handler.rows)}")
    print(f"   ignorés (géométrie insuffisante) : {handler.skipped_no_geom}")
    print(f"   taille .db    : {raw_size / 1e6:.1f} Mo")
    print(f"   taille .db.gz : {gz_size / 1e6:.1f} Mo ({100 * gz_size / raw_size:.0f}% de l'original)")
    return len(handler.rows)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 pbf_to_db.py <input.osm.pbf> <output.db>")
        sys.exit(1)
    convert(sys.argv[1], sys.argv[2])
