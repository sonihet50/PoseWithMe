"""Write the poses chosen in curation.json into the app as assets/poses/<id>.json.

Each entry names a reviewed candidate by the first characters of its Openverse id and adds
the human-written metadata. The pose file format is documented in README.md.

Usage: python -I export_poses.py [--cache cache]
"""

import argparse
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
ASSETS = HERE.parent.parent / "app" / "src" / "main" / "assets" / "poses"

LICENSE_NAMES = {
    "cc0": "CC0 {v}",
    "pdm": "Public Domain Mark {v}",
    "by": "CC BY {v}",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=str(HERE / "cache"))
    args = parser.parse_args()

    candidates = json.loads((pathlib.Path(args.cache) / "candidates.json").read_text(encoding="utf-8"))
    curation = json.loads((HERE / "curation.json").read_text(encoding="utf-8"))
    ASSETS.mkdir(parents=True, exist_ok=True)

    for entry in curation:
        matches = [c for c in candidates if c["openverse_id"].startswith(entry["openverse"])]
        if len(matches) != 1:
            raise SystemExit(f"{entry['id']}: expected one candidate for {entry['openverse']}, "
                             f"found {len(matches)}")
        c = matches[0]
        if c["license"] not in LICENSE_NAMES:
            raise SystemExit(f"{entry['id']}: license {c['license']} not allowed")
        pose = {
            "formatVersion": 1,
            "id": entry["id"],
            "name": entry["name"],
            "description": entry["description"],
            "category": entry["category"],
            "difficulty": entry["difficulty"],
            "framing": entry.get("framing", c["framing"]),
            "mirrorAllowed": entry.get("mirrorAllowed", True),
            "instructions": entry["instructions"],
            "scenes": entry["scenes"],
            "ignoredParts": entry.get("ignoredParts", []),
            "source": {
                "type": "web",
                "title": c["title"],
                "author": c["creator"],
                "url": c["landing_url"],
                "license": LICENSE_NAMES[c["license"]].format(v=c["license_version"]),
                "licenseUrl": c["license_url"],
            },
            "landmarks": c["landmarks"],
        }
        out = ASSETS / f"{entry['id']}.json"
        out.write_text(json.dumps(pose, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"wrote {out.relative_to(HERE.parent.parent)}")


if __name__ == "__main__":
    main()
