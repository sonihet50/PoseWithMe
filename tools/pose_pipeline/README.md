# Pose curation pipeline

Offline tools that build PoseWithMe's built-in pose library from openly licensed
photos. Nothing here ships in the app except the JSON files written to
`app/src/main/assets/poses/`. Those files hold body-relative coordinates and
attribution, not images.

## Setup

```
python -m venv .venv
.venv\Scripts\python -m pip install mediapipe==1.1.0 requests pillow numpy
```

Download the heavy pose model (about 29 MB, used only here) to
`models/pose_landmarker_heavy.task` from
`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task`.

## Steps

1. `python -I fetch_openverse.py [--query "..."] [--any-aspect]` searches
   Openverse for CC0, Public Domain Mark and CC BY photos and downloads them to
   `cache/images/`. Anonymous use allows 20 requests per minute and 200 per day.
2. `python extract_poses.py` runs MediaPipe in still-image mode and keeps
   images that show exactly one upright, front-facing person with visible
   shoulders, hips and arms. It removes near-duplicates and writes
   `cache/candidates.json` plus review sheets in `cache/sheets/`. Each sheet
   shows the photo next to its silhouette.
3. Review the sheets. Add the poses you want to `curation.json`, with a name,
   category, instructions and scenes.
4. `python -I export_poses.py` writes `app/src/main/assets/poses/<id>.json`.
5. Delete `cache/images/` and `cache/sheets/` once you're done.

**Content warning:** Openverse's `mature=false` filter is not reliable. The
first run returned several explicit images. The fetch script also drops
results Openverse marks as sensitive, but review the sheets in private and
expect to discard some.

## Pose file format (version 1)

```json
{
  "formatVersion": 1,
  "id": "one-hand-on-hip",
  "name": "One hand on hip",
  "description": "…",
  "category": "casual_standing",
  "difficulty": "easy | medium | hard",
  "framing": "full_body | upper_body",
  "mirrorAllowed": true,
  "instructions": ["…"],
  "scenes": ["street", "park"],
  "ignoredParts": ["left_arm"],
  "source": {"type": "web | recorded | user", "title": "…", "author": "…",
             "url": "…", "license": "CC BY 2.0", "licenseUrl": "…"},
  "landmarks": [[x, y, z, visibility], … 33 rows in MediaPipe order]
}
```

Landmarks use the body frame defined in `BodyPose.java` and `posekit.normalize()`:

- **Origin:** the hip midpoint.
- **Unit length:** the distance from the hip midpoint to the shoulder midpoint.
- **Axes:** +x points to the image's right and +y points down.
- **Sides:** the image is unmirrored, so the person's own left appears at +x.

`ignoredParts` uses the names `head`, `torso`, `left_arm`, `right_arm`,
`left_leg` and `right_leg`.
