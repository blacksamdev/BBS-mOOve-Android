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


def geofabrik_url(path):
    # path est le chemin Geofabrik sous .../europe/france/, ex "bourgogne"
    # (Geofabrik découpe la France par ANCIENNE région, pas par département).
    return f"{GEOFABRIK_BASE}/{path}-latest.osm.pbf"


def download(url, dest):
    print(f"  téléchargement : {url}")

    # curl en premier : il gère correctement les redirections de Geofabrik
    # (qui renvoie vers un miroir), là où urllib se faisait piéger et
    # écrivait une petite page HTML/redirection au lieu du vrai .pbf.
    last_err = None
    for attempt, method in enumerate((_download_curl, _download_urllib), start=1):
        try:
            method(url, dest)
            _validate_pbf(dest)
            size = os.path.getsize(dest)
            print(f"  téléchargé : {size / 1e6:.1f} Mo (via {method.__name__})")
            return
        except Exception as e:
            last_err = e
            print(f"  tentative {attempt} ({method.__name__}) échouée : {e}")
            if os.path.exists(dest):
                os.remove(dest)

    raise RuntimeError(f"téléchargement impossible après curl ET urllib : {last_err}")


def _validate_pbf(path):
    """
    Vérifie que le fichier est bien un OSM PBF, pas une page d'erreur HTML
    ou une redirection. Un .osm.pbf valide :
      - pèse au moins quelques centaines de Ko (un département complet)
      - commence par un BlobHeader (octets 0x00 0x00 0x00 ... pas '<' de HTML)
    """
    if not os.path.exists(path):
        raise RuntimeError("fichier absent après téléchargement")

    size = os.path.getsize(path)
    if size < 100_000:  # un département fait des dizaines de Mo, jamais <100Ko
        # Lire le début pour donner un message utile (souvent du HTML d'erreur)
        with open(path, "rb") as f:
            head = f.read(200)
        snippet = head.decode("utf-8", errors="replace").strip()[:160]
        raise RuntimeError(
            f"fichier trop petit ({size} octets), probablement une erreur. "
            f"Début : {snippet!r}"
        )

    # Un PBF commence par 4 octets de taille de BlobHeader (big-endian),
    # suivis du type "OSMHeader". Le tout premier octet n'est jamais '<'
    # (qui signalerait du HTML).
    with open(path, "rb") as f:
        head = f.read(16)
    if head[:1] == b"<":
        raise RuntimeError("le fichier reçu est du HTML, pas un PBF")
    if b"OSMHeader" not in head and head[0:4] == b"\x00\x00\x00\x00":
        raise RuntimeError("en-tête PBF invalide")


def _download_urllib(url, dest):
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (compatible; BBS-mOOve-build/1.0; +https://github.com/blacksamdev/BBS-mOOve-Android)",
            "Accept": "*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        status = getattr(resp, "status", None) or resp.getcode()
        if status != 200:
            raise RuntimeError(f"HTTP {status} (attendu 200)")
        total = 0
        with open(dest, "wb") as f:
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
                total += len(chunk)
    if total == 0:
        raise RuntimeError("réponse vide")


def _download_curl(url, dest):
    # curl est toujours présent sur les runners GitHub ; -L suit les
    # redirections, -f échoue proprement sur erreur HTTP, --retry gère
    # les coupures réseau transitoires.
    result = subprocess.run(
        ["curl", "-fSL", "--retry", "3", "--retry-delay", "5",
         "-A", "Mozilla/5.0 (compatible; BBS-mOOve-build/1.0)",
         "-o", str(dest), url],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        # Diagnostic : on refait une requête en mode entêtes seules pour
        # voir ce que le serveur répond vraiment (code, redirection, etc.)
        print("  --- diagnostic curl (entêtes) ---")
        diag = subprocess.run(
            ["curl", "-sIL", "-A", "Mozilla/5.0 (compatible; BBS-mOOve-build/1.0)", url],
            capture_output=True, text=True,
        )
        print(diag.stdout[:2000] or "(aucune réponse)")
        print("  --- fin diagnostic ---")
        raise RuntimeError(f"curl a échoué (code {result.returncode}): {result.stderr.strip()}")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    departments = json.loads((SCRIPT_DIR / "departments.json").read_text())

    summary = []
    for code, info in departments.items():
        print(f"\n=== Extrait {code} ({info['name']}) ===")
        pbf_path = OUT_DIR / f"{code}.osm.pbf"
        db_path = OUT_DIR / f"{code}.db"

        try:
            download(geofabrik_url(info["path"]), pbf_path)
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
