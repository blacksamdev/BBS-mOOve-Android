#!/usr/bin/env python3
"""
radars_to_db.py — convertit le CSV officiel des radars (data.gouv.fr) en
une base SQLite légère utilisée par l'app pour les alertes "zone de danger"
et "zone d'attention".

Source : https://www.data.gouv.fr/datasets/radars-automatiques
Colonnes du CSV :
    date_heure_dernier_changement, date_heure_creation, departement,
    latitude, longitude, id, direction, equipement, date_installation,
    type, emplacement, route, longueur_troncon_km,
    vitesse_poids_lourds_kmh, vitesse_vehicules_legers_kmh

Catégorisation (choix produit, jamais le mot "radar" côté app) :
    - "danger"    : radars de VITESSE (fixe, discriminant, tronçon, urbain…)
                    -> alerte rouge "Zone de danger"
    - "attention" : dispositifs de FRANCHISSEMENT (feu rouge, passage à niveau)
                    -> alerte orange "Zone d'attention"

Le champ "type" du CSV est un libellé texte ("Radar fixe", "Radar feu rouge",
"Radar passage à niveau", "Radar tronçon", etc.). On catégorise dessus.

Usage :
    python3 radars_to_db.py radars.csv radars.db
"""

import csv
import gzip
import os
import shutil
import sqlite3
import sys

# Sous-chaînes (minuscules) identifiant les dispositifs de FRANCHISSEMENT.
# Tout le reste est considéré comme radar de vitesse -> "danger".
FRANCHISSEMENT_HINTS = ("feu", "passage a niveau", "passage à niveau", "niveau")


def categorize(type_label):
    """Renvoie 'attention' pour les franchissements, 'danger' sinon."""
    if not type_label:
        return "danger"
    t = type_label.strip().lower()
    for hint in FRANCHISSEMENT_HINTS:
        if hint in t:
            return "attention"
    return "danger"


def parse_speed(raw):
    """Vitesse VL en int, ou None si vide/non numérique."""
    if raw is None:
        return None
    raw = raw.strip()
    if not raw:
        return None
    try:
        return int(float(raw))
    except ValueError:
        return None


def convert(csv_path, db_path):
    rows = []
    skipped = 0

    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            try:
                lat = float(r["latitude"])
                lon = float(r["longitude"])
            except (ValueError, KeyError, TypeError):
                skipped += 1
                continue

            # Coordonnées manifestement invalides
            if not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
                skipped += 1
                continue

            category = categorize(r.get("type"))
            speed = parse_speed(r.get("vitesse_vehicules_legers_kmh"))

            rows.append((round(lat, 5), round(lon, 5), speed, category))

    conn = sqlite3.connect(db_path)
    conn.execute("DROP TABLE IF EXISTS danger_zones")
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
    conn.executemany(
        "INSERT INTO danger_zones (lat, lon, limit_kmh, category) VALUES (?, ?, ?, ?)",
        rows,
    )
    conn.execute("CREATE INDEX idx_latlon ON danger_zones(lat, lon)")
    conn.commit()
    conn.execute("VACUUM")
    conn.commit()
    conn.close()

    raw_size = os.path.getsize(db_path)

    gz_path = str(db_path) + ".gz"
    with open(db_path, "rb") as f_in, gzip.open(gz_path, "wb", compresslevel=9) as f_out:
        shutil.copyfileobj(f_in, f_out)
    gz_size = os.path.getsize(gz_path)

    n_danger = sum(1 for _, _, _, c in rows if c == "danger")
    n_attention = sum(1 for _, _, _, c in rows if c == "attention")

    print(f"OK -> {db_path}")
    print(f"   zones écrites : {len(rows)} (danger={n_danger}, attention={n_attention})")
    print(f"   ignorées (coords invalides) : {skipped}")
    print(f"   taille .db    : {raw_size / 1e6:.2f} Mo")
    print(f"   taille .db.gz : {gz_size / 1e6:.2f} Mo")
    return len(rows)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 radars_to_db.py <radars.csv> <radars.db>")
        sys.exit(1)
    convert(sys.argv[1], sys.argv[2])
