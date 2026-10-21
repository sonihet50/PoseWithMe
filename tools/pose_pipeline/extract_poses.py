"""Run MediaPipe Pose (heavy model) on downloaded photos, keep clean single-person
front-facing poses, and render contact sheets for manual review.

Usage: python -I extract_poses.py [--cache cache]
Outputs: cache/candidates.json and cache/sheets/sheet_NN.png
"""

import argparse
import json
import math
import pathlib

import mediapipe as mp
import numpy as np
from mediapipe.tasks.python import BaseOptions, vision
from PIL import Image, ImageDraw, ImageOps

import posekit as pk

MODEL = pathlib.Path(__file__).with_name("models") / "pose_landmarker_heavy.task"
MAX_SIDE = 1280
Image.MAX_IMAGE_PIXELS = 60_000_000

# Quality gates; tuned by eye on the contact sheets.
MIN_CORE_VISIBILITY = 0.8      # shoulders and hips
MIN_LIMB_VISIBILITY = 0.65     # joints needed for the chosen framing
MIN_TORSO_FRACTION = 0.12      # torso length / image height
MIN_FRONTAL_RATIO = 0.45       # shoulder width / torso length; side views fall below
MAX_TORSO_TILT_DEG = 35
DUPLICATE_DISTANCE = 0.12      # mean joint distance in torso units


def create_landmarker(groups=False):
    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL)),
        running_mode=vision.RunningMode.IMAGE,
        num_poses=7 if groups else 3,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
    )
    return vision.PoseLandmarker.create_from_options(options)


def load_image(path):
    img = Image.open(path)
    img = ImageOps.exif_transpose(img).convert("RGB")
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    return img


def in_frame(lm, margin=0.02):
    return -margin <= lm.x <= 1 + margin and -margin <= lm.y <= 1 + margin


def assess(landmarks, width, height, group=False):
    """Returns (framing, metrics) or (None, reason)."""
    vis = lambda i: landmarks[i].visibility or 0.0
    core = (pk.LEFT_SHOULDER, pk.RIGHT_SHOULDER, pk.LEFT_HIP, pk.RIGHT_HIP)
    if min(vis(i) for i in core) < (0.5 if group else MIN_CORE_VISIBILITY):
        return None, "core not visible"

    px = lambda i: (landmarks[i].x * width, landmarks[i].y * height)
    ls, rs, lh, rh = (px(i) for i in core)
    sh = ((ls[0] + rs[0]) / 2, (ls[1] + rs[1]) / 2)
    hp = ((lh[0] + rh[0]) / 2, (lh[1] + rh[1]) / 2)
    torso = math.hypot(sh[0] - hp[0], sh[1] - hp[1])
    if torso / height < (0.07 if group else MIN_TORSO_FRACTION):
        return None, "person too small"
    tilt = math.degrees(math.atan2(abs(sh[0] - hp[0]), hp[1] - sh[1]))
    if sh[1] >= hp[1] or tilt > MAX_TORSO_TILT_DEG:
        return None, "torso not upright"
    frontal = math.hypot(ls[0] - rs[0], ls[1] - rs[1]) / torso
    if frontal < (0.35 if group else MIN_FRONTAL_RATIO):
        return None, "not front-facing"

    arms = (pk.LEFT_ELBOW, pk.RIGHT_ELBOW)
    legs = (pk.LEFT_KNEE, pk.RIGHT_KNEE, pk.LEFT_ANKLE, pk.RIGHT_ANKLE)
    if min(vis(i) for i in arms) < (0.4 if group else MIN_LIMB_VISIBILITY):
        return None, "arms not visible"
    if all(vis(i) >= MIN_LIMB_VISIBILITY and in_frame(landmarks[i]) for i in legs):
        framing = "full_body"
    else:
        framing = "upper_body"
    return framing, {
        "torso_fraction": round(torso / height, 3),
        "frontal_ratio": round(frontal, 3),
        "tilt_deg": round(tilt, 1),
    }


def contact_sheets(candidates, images_dir, out_dir, per_sheet=12, cols=4):
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("sheet_*.png"):
        old.unlink()
    tile_w, tile_h, label_h = 256, 320, 22
    for s in range(0, len(candidates), per_sheet):
        chunk = candidates[s:s + per_sheet]
        rows = math.ceil(len(chunk) / cols)
        sheet = Image.new("RGB", (cols * tile_w * 2, rows * (tile_h + label_h)), (0, 0, 0))
        draw = ImageDraw.Draw(sheet)
        for k, c in enumerate(chunk):
            x0 = (k % cols) * tile_w * 2
            y0 = (k // cols) * (tile_h + label_h)
            photo = load_image(images_dir / c["file"])
            photo.thumbnail((tile_w, tile_h))
            sheet.paste(photo, (x0 + (tile_w - photo.width) // 2, y0 + (tile_h - photo.height) // 2))
            guide = pk.render_group(c["people"]) if "people" in c else pk.render_silhouette(c["landmarks"])
            sheet.paste(guide, (x0 + tile_w, y0))
            draw.text((x0 + 4, y0 + tile_h + 4),
                      f"{c['key']}  {c['openverse_id'][:8]}  {c['framing']}",
                      fill=(255, 255, 255))
        sheet.save(out_dir / f"sheet_{s // per_sheet + 1:02d}.png")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default="cache")
    parser.add_argument("--groups", action="store_true", help="extract 2 to 6 people as one formation")
    args = parser.parse_args()
    cache = pathlib.Path(args.cache)
    images_dir = cache / "images"
    rows = [json.loads(l) for l in (cache / "metadata.jsonl").read_text(encoding="utf-8").splitlines()]

    landmarker = create_landmarker(args.groups)
    candidates, rejected = [], {}
    for row in rows:
        path = images_dir / f"{row['id']}.img"
        if not path.exists():
            continue
        try:
            img = load_image(path)
        except Exception as e:  # corrupt or unsupported downloads
            rejected["unreadable"] = rejected.get("unreadable", 0) + 1
            continue
        result = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.asarray(img)))
        detected = pk.unique_people(result.pose_landmarks, img.width, img.height)
        count = len(detected)
        if (args.groups and not 2 <= count <= 6) or (not args.groups and count != 1):
            reason = "no person" if not result.pose_landmarks else "several people"
            rejected[reason] = rejected.get(reason, 0) + 1
            continue
        landmarks = detected[0]
        framing, info = assess(landmarks, img.width, img.height, args.groups)
        if args.groups:
            assessments = [assess(person, img.width, img.height, True)
                           for person in detected]
            if any(f is None for f, _ in assessments):
                framing, info = None, "group member not usable"
            else:
                framing = "full_body" if all(f == "full_body" for f, _ in assessments) else "upper_body"
        if framing is None:
            rejected[info] = rejected.get(info, 0) + 1
            continue
        normalized = pk.normalize(landmarks, img.width, img.height)
        candidates.append({
            "file": path.name,
            "openverse_id": row["id"],
            "query": row["query"],
            "title": row["title"],
            "creator": row["creator"],
            "license": row["license"],
            "license_version": row["license_version"],
            "license_url": row["license_url"],
            "landing_url": row["landing_url"],
            "framing": framing,
            "metrics": info,
            "pixels": img.width * img.height,
            "landmarks": normalized,
        })
        if args.groups:
            candidates[-1]["people"] = pk.normalize_group(detected, img.width, img.height)
            candidates[-1]["person_count"] = count
            candidates[-1].pop("landmarks")

    # Drop near-duplicates, keeping the larger image.
    candidates.sort(key=lambda c: -c["pixels"])
    unique = []
    for c in candidates:
        if all((sum(pk.mean_joint_distance(a, b) for a, b in zip(c["people"], u["people"]))
                / len(c["people"]) if args.groups else pk.mean_joint_distance(c["landmarks"], u["landmarks"]))
               > DUPLICATE_DISTANCE for u in unique if u["framing"] == c["framing"]
               and u.get("person_count", 1) == c.get("person_count", 1)):
            unique.append(c)
        else:
            rejected["duplicate"] = rejected.get("duplicate", 0) + 1
    unique.sort(key=lambda c: (c["framing"], c["query"]))
    for i, c in enumerate(unique, 1):
        c["key"] = f"c{i:03d}"

    (cache / "candidates.json").write_text(json.dumps(unique, indent=1), encoding="utf-8")
    contact_sheets(unique, images_dir, cache / "sheets")
    landmarker.close()
    print(f"{len(unique)} candidates from {len(rows)} images; rejected: {rejected}")


if __name__ == "__main__":
    main()
