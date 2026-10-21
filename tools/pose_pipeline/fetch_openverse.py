"""Search Openverse for openly licensed photos of people posing and download them.

Only CC0, Public Domain Mark and CC BY images are requested. Images are kept in
cache/ (gitignored) for local processing; the app only ever ships the derived
pose coordinates plus attribution.

Anonymous Openverse limits: 20 requests/minute, 200/day.

Usage: python -I fetch_openverse.py [--pages 2] [--cache cache]
"""

import argparse
import json
import pathlib
import time

import requests

API = "https://api.openverse.org/v1/images/"
USER_AGENT = "PoseWithMe-student-project/0.1 (offline pose curation; contact via repo owner)"
LICENSES = "cc0,pdm,by"
MAX_IMAGE_BYTES = 15 * 1024 * 1024

QUERIES = [
    "full body portrait standing",
    "man standing portrait street",
    "woman standing portrait outdoor",
    "hand on hip pose",
    "arms crossed portrait",
    "hands in pockets portrait",
    "walking street portrait",
    "tourist posing monument",
    "sitting on bench portrait",
    "leaning against wall portrait",
    "arms outstretched beach",
    "pointing portrait outdoor",
    "fashion model pose full length",
]


def search(session, query, page, tall_only):
    params = {
        "q": query,
        "license": LICENSES,
        "category": "photograph",
        "mature": "false",
        "page_size": 20,
        "page": page,
    }
    if tall_only:
        params["aspect_ratio"] = "tall"
    response = session.get(API, params=params, timeout=30)
    response.raise_for_status()
    return response.json().get("results", [])


def download(session, url, dest):
    with session.get(url, timeout=30, stream=True) as response:
        response.raise_for_status()
        data = b""
        for chunk in response.iter_content(64 * 1024):
            data += chunk
            if len(data) > MAX_IMAGE_BYTES:
                raise ValueError("image too large")
    dest.write_bytes(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pages", type=int, default=2)
    parser.add_argument("--cache", default="cache")
    parser.add_argument("--query", action="append", help="search terms (repeatable); default: QUERIES")
    parser.add_argument("--any-aspect", action="store_true", help="don't restrict to tall images")
    args = parser.parse_args()

    cache = pathlib.Path(args.cache)
    images = cache / "images"
    images.mkdir(parents=True, exist_ok=True)
    meta_path = cache / "metadata.jsonl"
    known = {}
    if meta_path.exists():
        for line in meta_path.read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            known[row["id"]] = row

    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    new_rows = []
    for query in args.query or QUERIES:
        for page in range(1, args.pages + 1):
            try:
                results = search(session, query, page, not args.any_aspect)
            except requests.RequestException as e:
                print(f"search failed for {query!r} page {page}: {e}")
                break
            finally:
                time.sleep(3.2)  # stay under 20 requests/minute
            for r in results:
                # mature=false alone let explicit images through; also drop anything
                # Openverse marks as sensitive. Results still need a human review.
                if r["id"] in known or r.get("mature") or r.get("unstable__sensitivity"):
                    continue
                row = {
                    "id": r["id"],
                    "query": query,
                    "title": r.get("title"),
                    "creator": r.get("creator"),
                    "license": r.get("license"),
                    "license_version": r.get("license_version"),
                    "license_url": r.get("license_url"),
                    "landing_url": r.get("foreign_landing_url"),
                    "image_url": r.get("url"),
                    "provider": r.get("provider"),
                    "attribution": r.get("attribution"),
                }
                known[r["id"]] = row
                new_rows.append(row)
            print(f"{query!r} page {page}: {len(results)} results, {len(known)} known")
            if len(results) < 20:
                break

    with meta_path.open("a", encoding="utf-8") as f:
        for row in new_rows:
            f.write(json.dumps(row) + "\n")

    ok = 0
    for row in new_rows:
        dest = images / f"{row['id']}.img"
        if dest.exists() or not row.get("image_url"):
            continue
        try:
            download(session, row["image_url"], dest)
            ok += 1
        except (requests.RequestException, ValueError) as e:
            print(f"download failed {row['id']}: {e}")
    print(f"downloaded {ok} new images into {images}")


if __name__ == "__main__":
    main()
