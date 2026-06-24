"""
Tests exécutés réellement dans ce sandbox (pas juste une relecture de code).
Couvre road_lookup.py et danger_lookup.py avec des cas concrets.
"""
import json
import math
import sys

sys.path.insert(0, "/home/claude/bbs-moove-android/app/src/main/python")

from road_lookup import haversine_m, nearest_segment, _point_to_segment_distance_m
from danger_lookup import nearest_danger_zone

passed = 0
failed = 0


def check(label, condition):
    global passed, failed
    if condition:
        passed += 1
        print(f"OK   - {label}")
    else:
        failed += 1
        print(f"FAIL - {label}")


# ---------- haversine_m ----------
# Chalon-sur-Saône (~46.7806, 4.8528) -> Mâcon (~46.3069, 4.8327), ~53 km à vol d'oiseau
d = haversine_m(46.7806, 4.8528, 46.3069, 4.8327)
check(f"haversine Chalon->Mâcon ≈ 53km (obtenu {d/1000:.1f}km)", 50_000 < d < 56_000)

d0 = haversine_m(46.7806, 4.8528, 46.7806, 4.8528)
check("haversine distance nulle pour point identique", d0 < 0.01)

# ---------- _point_to_segment_distance_m ----------
# Segment nord-sud le long d'une longitude fixe, point décalé de ~10m à l'est
lat1, lon1 = 46.7800, 4.8500
lat2, lon2 = 46.7820, 4.8500
# point au milieu du segment, décalé de ~0.0001° en longitude (~7-8m à cette latitude)
dist_offset = _point_to_segment_distance_m(46.7810, 4.8501, lat1, lon1, lat2, lon2)
check(f"distance point->segment perpendiculaire raisonnable (obtenu {dist_offset:.1f}m)", 3 < dist_offset < 15)

# point exactement sur le segment -> distance ~0
dist_on_seg = _point_to_segment_distance_m(46.7810, 4.8500, lat1, lon1, lat2, lon2)
check(f"distance point->segment sur la ligne ≈ 0 (obtenu {dist_on_seg:.2f}m)", dist_on_seg < 1)

# point bien au-delà de l'extrémité du segment (hors troncon) -> distance vers l'extrémité
dist_far = _point_to_segment_distance_m(46.7900, 4.8500, lat1, lon1, lat2, lon2)
check(f"distance point->segment hors troncon (obtenu {dist_far:.1f}m)", dist_far > 800)

# ---------- nearest_segment ----------
segments = [
    {
        "points": [[46.7800, 4.8500], [46.7820, 4.8500]],
        "maxspeed": 50,
        "junction": None,
        "is_agglomeration": True,
    },
    {
        "points": [[46.7700, 4.8400], [46.7720, 4.8400]],
        "maxspeed": None,  # pas de tag -> doit retomber sur le défaut hors agglo
        "junction": None,
        "is_agglomeration": False,
    },
    {
        "points": [[46.7810, 4.8600], [46.7812, 4.8602]],
        "maxspeed": 30,
        "junction": "roundabout",
        "is_agglomeration": True,
    },
]
segments_json = json.dumps(segments)

# Position très proche du 1er segment (50 km/h)
res1 = json.loads(nearest_segment(46.7810, 4.8500, segments_json))
check(f"nearest_segment trouve le bon tronçon (limit=50, obtenu {res1['limit']})", res1["limit"] == 50)
check("nearest_segment pas de junction sur ce tronçon", res1["junction"] is None)

# Position proche du segment sans maxspeed -> fallback hors agglo (80)
res2 = json.loads(nearest_segment(46.7710, 4.8400, segments_json))
check(f"nearest_segment fallback hors agglo = 80 (obtenu {res2['limit']})", res2["limit"] == 80)

# Position proche du rond-point
res3 = json.loads(nearest_segment(46.7811, 4.8601, segments_json))
check(f"nearest_segment détecte le rond-point (obtenu {res3['junction']})", res3["junction"] == "roundabout")

# Position loin de tout -> None
res4 = nearest_segment(10.0, 10.0, segments_json, max_radius_m=80.0)
check("nearest_segment renvoie None si rien dans le rayon", res4 is None)

# ---------- nearest_danger_zone ----------
zones = [
    {"lat": 46.7810, "lon": 4.8500, "limit": 50},
    {"lat": 46.9000, "lon": 5.0000, "limit": 90},
]
zones_json = json.dumps(zones)

# Très proche du radar (devrait déclencher l'alerte, < 900m)
near = json.loads(nearest_danger_zone(46.7811, 4.8500, zones_json))
check(f"danger zone proche -> should_alert True (dist={near['distance_m']}m)", near["should_alert"] is True)

# A ~1000m -> dans le rayon de recherche mais sous le seuil d'alerte
# (~0.009° de latitude ≈ 1000m)
mid = json.loads(nearest_danger_zone(46.7900, 4.8500, zones_json, radius_m=1200))
check(
    f"danger zone à ~1000m -> détectée mais should_alert False (dist={mid['distance_m']}m)",
    mid is not None and mid["should_alert"] is False,
)

# Loin de tout -> None
far = nearest_danger_zone(10.0, 10.0, zones_json, radius_m=1200)
check("danger zone hors rayon -> None", far is None)

print(f"\n{passed} OK, {failed} FAIL")
sys.exit(1 if failed else 0)
