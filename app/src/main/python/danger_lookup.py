"""
danger_lookup.py

Trouve la "zone de danger" (terme volontairement générique, cf. décret
2012 sur la signalisation des contrôles routiers) la plus proche dans un
rayon donné, à partir du référentiel officiel data.gouv.fr.

Appelé depuis Kotlin via Chaquopy :
    val py = Python.getInstance().getModule("danger_lookup")
    val result = py.callAttr("nearest_danger_zone", lat, lon, zonesJson)
"""

import json
import math

from road_lookup import haversine_m

DEFAULT_SEARCH_RADIUS_M = 1200.0
ALERT_TRIGGER_DISTANCE_M = 900.0


def nearest_danger_zone(lat, lon, zones_json, radius_m=DEFAULT_SEARCH_RADIUS_M):
    """
    zones_json : liste de dicts JSON, chacun avec :
        - "lat": float
        - "lon": float
        - "limit": int (vitesse associée à la zone, pour affichage si besoin)
        - "category": "danger" | "attention"

    Renvoie un dict JSON sérialisé :
        {"distance_m": float, "limit": int|None, "should_alert": bool,
         "category": "danger"|"attention"}
    ou None si rien dans le rayon de recherche.
    """
    zones = json.loads(zones_json) if isinstance(zones_json, str) else zones_json

    best = None
    best_dist = radius_m

    for zone in zones:
        d = haversine_m(lat, lon, zone["lat"], zone["lon"])
        if d < best_dist:
            best_dist = d
            best = zone

    if best is None:
        return None

    return json.dumps(
        {
            "distance_m": round(best_dist, 1),
            "limit": best.get("limit"),
            "should_alert": best_dist <= ALERT_TRIGGER_DISTANCE_M,
            "category": best.get("category", "danger"),
        }
    )
