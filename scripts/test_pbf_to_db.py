"""Test du pipeline pbf_to_db sur un échantillon (exécuté réellement)."""
import json, sqlite3, subprocess, sys, tempfile, os
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE.parent / "app/src/main/python"))
from road_lookup import nearest_segment

def run():
    with tempfile.TemporaryDirectory() as tmp:
        db = Path(tmp) / "test.db"
        r = subprocess.run([sys.executable, str(HERE/"pbf_to_db.py"),
                            str(HERE/"test_sample.osm"), str(db)],
                           capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        conn = sqlite3.connect(db)
        rows = list(conn.execute("SELECT maxspeed, junction, is_agglomeration FROM road_segments ORDER BY id"))
        assert len(rows) == 3, f"attendu 3 segments, obtenu {len(rows)}"
        assert rows[0][0] == 80, "route secondaire doit être à 80"
        assert rows[2][1] == "roundabout", "segment 3 doit être un rond-point"
        conn.close()
        print("OK - pipeline pbf_to_db validé (3 segments, rond-point détecté)")

if __name__ == "__main__":
    run()
