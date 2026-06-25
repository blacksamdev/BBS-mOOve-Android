#!/usr/bin/env python3
"""
build_all_departments.py — pour chaque département listé dans
departments.json : télécharge l'extrait Geofabrik puis le convertit en
.db SQLite léger (via pbf_to_db.py).

Tourne dans la GitHub Action. Les .db produits sont ensuite publiés dans
une Release par le workflow.

Geofabrik range les départements français sous leur région, ex :
  https://download.geofabrik.de/europe/france/bourgogne-franche-comte/saone-et-loire-latest.osm.pbf
"""

import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

GEOFABRIK_BASE = "https://download.geofabrik.de/europe/france"
SCRIPT_DIR = Path(__file__).parent
OUT_DIR = Path(os.environ.get("OUTPUT_DIR", "dist"))


def geofabrik_url(region, slug):
    return f"{GEOFABRIK_BASE}/{region}/{slug}-latest.osm.pbf"


def download(url, dest):
    print(f"  téléchargement : {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "BBS-mOOve-build/1.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as f:
        total = 0
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            total += len(chunk)
    print(f"  téléchargé : {total / 1e6:.1f} Mo")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    departments = json.loads((SCRIPT_DIR / "departments.json").read_text())

    summary = []
    for code, info in departments.items():
        print(f"\n=== Département {code} ({info['name']}) ===")
        pbf_path = OUT_DIR / f"{code}.osm.pbf"
        db_path = OUT_DIR / f"{code}.db"

        try:
            download(geofabrik_url(info["region"], info["slug"]), pbf_path)
        except Exception as e:
            print(f"  ÉCHEC téléchargement : {e}")
            summary.append((code, "download_failed", 0))
            continue

        # Conversion via pbf_to_db.py (réutilise exactement le même code testé)
        result = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "pbf_to_db.py"), str(pbf_path), str(db_path)],
            capture_output=True, text=True,
        )
        print(result.stdout)
        if result.returncode != 0:
            print(f"  ÉCHEC conversion : {result.stderr}")
            summary.append((code, "convert_failed", 0))
            continue

        # On supprime le .pbf intermédiaire (volumineux), on ne garde que le .db
        pbf_path.unlink(missing_ok=True)

        size_mb = db_path.stat().st_size / 1e6
        summary.append((code, "ok", size_mb))
        print(f"  .db final : {size_mb:.1f} Mo")

    print("\n=== RÉSUMÉ ===")
    for code, status, size in summary:
        print(f"  {code}: {status} ({size:.1f} Mo)" if status == "ok" else f"  {code}: {status}")

    # Échoue le job si AUCUN département n'a réussi
    if not any(s == "ok" for _, s, _ in summary):
        sys.exit(1)


if __name__ == "__main__":
    main()
