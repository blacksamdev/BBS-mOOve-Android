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


def _segment_bearing_deg(lat1, lon1, lat2, lon2):
    """Cap (azimut) d'un segment orienté, en degrés 0-360 (0 = nord)."""
    lat0 = math.radians((lat1 + lat2) / 2)
    dx = (lon2 - lon1) * math.cos(lat0)
    dy = (lat2 - lat1)
    ang = math.degrees(math.atan2(dx, dy))
    return ang % 360.0


def _bearing_diff(a, b):
    """
    Écart angulaire minimal entre deux caps, en tenant compte du fait qu'une
    route se parcourt dans les DEUX sens : un tronçon orienté à 90° "colle"
    aussi bien à un cap de 90° que de 270°. Renvoie 0-90.
    """
    d = abs(a - b) % 360.0
    if d > 180.0:
        d = 360.0 - d
    # Sens inverse aussi valable
    if d > 90.0:
        d = 180.0 - d
    return d


def nearest_segment(
    lat, lon, segments_json,
    max_radius_m=80.0,
    heading=-1.0,            # cap GPS courant (degrés) ; <0 = indispo
    accuracy_m=-1.0,         # précision GPS (mètres) ; <0 = indispo
    prev_segment_id=-1,      # id du tronçon retenu juste avant ; <0 = aucun
):
    """
    Map-matching léger : au lieu de prendre bêtement le tronçon le plus
    proche (qui peut être une route parallèle/croisée), on score chaque
    candidat en combinant :
      - la distance perpendiculaire (plus c'est proche, mieux c'est)
      - l'alignement du cap GPS avec l'orientation du tronçon (si on file
        plein nord, on n'est pas sur une route est-ouest)
      - un bonus de continuité (rester sur le tronçon précédent plutôt que
        sauter sur un voisin à cause d'une mesure bruitée)
    Le tout pondéré par la précision GPS : quand le GPS est mauvais, on fait
    moins confiance à la distance brute et plus à la continuité.

    Les paramètres optionnels utilisent des sentinelles numériques (valeur
    négative = "non fourni") plutôt que None, pour éviter les soucis de
    conversion null Java<->Python via le pont Chaquopy.

    Renvoie un dict JSON sérialisé :
        {"limit": int, "junction": str|None, "distance_m": float, "id": int|None}
    """
    # Normalisation des sentinelles -> None interne
    heading = None if heading is None or heading < 0 else float(heading)
    accuracy_m = None if accuracy_m is None or accuracy_m < 0 else float(accuracy_m)
    prev_segment_id = None if prev_segment_id is None or prev_segment_id < 0 else int(prev_segment_id)

    segments = json.loads(segments_json) if isinstance(segments_json, str) else segments_json

    # Rayon de recherche élargi si le GPS est imprécis (sinon on risque de
    # ne RIEN trouver alors qu'on est sur une route).
    radius = max_radius_m
    if accuracy_m is not None and accuracy_m > max_radius_m:
        radius = min(accuracy_m * 1.5, 200.0)

    best = None
    best_score = float("inf")
    best_dist = radius

    for seg in segments:
        pts = seg.get("points", [])
        # Distance mini et cap du sous-segment le plus proche
        seg_min_dist = float("inf")
        seg_bearing = None
        for i in range(len(pts) - 1):
            lat1, lon1 = pts[i]
            lat2, lon2 = pts[i + 1]
            d = _point_to_segment_distance_m(lat, lon, lat1, lon1, lat2, lon2)
            if d < seg_min_dist:
                seg_min_dist = d
                seg_bearing = _segment_bearing_deg(lat1, lon1, lat2, lon2)

        if seg_min_dist > radius:
            continue

        # --- Score : on part de la distance (en mètres) ---
        score = seg_min_dist

        # --- Pénalité de désalignement du cap ---
        # On n'applique le cap que si on bouge assez vite pour qu'il soit
        # fiable (heading fourni). Un écart de 90° ajoute une grosse pénalité.
        if heading is not None and seg_bearing is not None:
            diff = _bearing_diff(heading, seg_bearing)  # 0-90
            # 0° -> +0, 90° -> +60 m de pénalité équivalente
            score += (diff / 90.0) * 60.0

        # --- Bonus de continuité ---
        # Rester sur le tronçon précédent vaut une "remise" de 25 m : il faut
        # qu'un autre tronçon soit nettement meilleur pour qu'on saute.
        if prev_segment_id is not None and seg.get("id") == prev_segment_id:
            score -= 25.0

        if score < best_score:
            best_score = score
            best = seg
            best_dist = seg_min_dist

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
            "id": best.get("id"),
        }
    )
