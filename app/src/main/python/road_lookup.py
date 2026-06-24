"""
road_lookup.py

Trouve le tronçon routier le plus proche d'une position GPS et renvoie
sa limite de vitesse (et si c'est un rond-point/croisement).

Volontairement en pur stdlib (pas de shapely) : les libs avec extensions C
sont une source classique de galères de compilation sous Chaquopy. Pour le
volume de données qu'on traite ici (un extrait régional, quelques dizaines
de milliers de segments), une recherche en pur Python est largement assez
rapide si on pré-filtre par bounding box (cf. road_db.py côté Kotlin/SQL).

Appelé depuis Kotlin via Chaquopy :
    val py = Python.getInstance().getModule("road_lookup")
    val result = py.callAttr("nearest_segment", lat, lon, segmentsJson)
"""

import json
import math

EARTH_RADIUS_M = 6371000.0

# Limite appliquée quand aucun segment OSM proche n'a de tag maxspeed renseigné.
DEFAULT_LIMIT_AGGLO = 50
DEFAULT_LIMIT_HORS_AGGLO = 80


def haversine_m(lat1, lon1, lat2, lon2):
    """Distance en mètres entre deux points lat/lon (formule de Haversine)."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = (
        math.sin(dphi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    )
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def _point_to_segment_distance_m(lat, lon, lat1, lon1, lat2, lon2):
    """
    Distance approchée d'un point à un segment de droite [p1, p2], en
    travaillant dans un repère local plan (équirectangulaire) -- largement
    suffisant pour des segments de quelques dizaines/centaines de mètres.
    """
    # Conversion approximative degrés -> mètres autour du point de référence
    lat0 = math.radians(lat1)
    mx = 111320.0 * math.cos(lat0)
    my = 111320.0

    px, py = (lon - lon1) * mx, (lat - lat1) * my
    sx, sy = (lon2 - lon1) * mx, (lat2 - lat1) * my

    seg_len_sq = sx * sx + sy * sy
    if seg_len_sq == 0:
        return math.hypot(px, py)

    t = max(0.0, min(1.0, (px * sx + py * sy) / seg_len_sq))
    proj_x, proj_y = t * sx, t * sy
    return math.hypot(px - proj_x, py - proj_y)


def nearest_segment(lat, lon, segments_json, max_radius_m=80.0):
    """
    segments_json : liste de dicts JSON, chacun avec :
        - "points": [[lat, lon], [lat, lon], ...]  (polyligne du tronçon)
        - "maxspeed": int ou None
        - "junction": "roundabout" | None
        - "is_agglomeration": bool (approx, déduit du tag OSM si dispo)

    Renvoie un dict JSON (sérialisé en str, pour le pont Chaquopy) :
        {"limit": int, "junction": str|None, "distance_m": float}
    ou None si rien dans le rayon.
    """
    segments = json.loads(segments_json) if isinstance(segments_json, str) else segments_json

    best = None
    best_dist = max_radius_m

    for seg in segments:
        pts = seg.get("points", [])
        for i in range(len(pts) - 1):
            lat1, lon1 = pts[i]
            lat2, lon2 = pts[i + 1]
            d = _point_to_segment_distance_m(lat, lon, lat1, lon1, lat2, lon2)
            if d < best_dist:
                best_dist = d
                best = seg

    if best is None:
        return None

    limit = best.get("maxspeed")
    if limit is None:
        limit = (
            DEFAULT_LIMIT_AGGLO
            if best.get("is_agglomeration")
            else DEFAULT_LIMIT_HORS_AGGLO
        )

    return json.dumps(
        {
            "limit": int(limit),
            "junction": best.get("junction"),
            "distance_m": round(best_dist, 1),
        }
    )
