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

## Groups

`extract_poses.py --groups --cache cache/groups` keeps 2–6 distinct people, rejects repeated
detections of the same person, and preserves their formation in a shared frame. Review the
whole photo next to the group silhouette: every person needs usable shoulders/hips, and the
pose is upper-body if any member's legs are not clear. Horizontal position defines each role.

If Openverse is unavailable, `fetch_commons.py --cache cache/groups --category "Standing couples"
--category "Groups posing"` downloads CC BY/CC0/public-domain references with author and license
metadata. Category membership is not a quality guarantee; manually review candidates before export.
Only pose coordinates and credits ship. The photo used by the Android import test lives under
`src/androidTest/assets`, not in the application APK.

Export selected photographic groups with:

```
python export_poses.py --cache cache/groups --curation group_curation.json
```

`create_group_poses.py` regenerates six original, hand-authored group arrangements. These are
explicitly credited as designed rather than traced from a photo. `--check` verifies that the
bundled assets match the generator. Run the small normalization/duplicate checks with:

```
python -m unittest discover -s tools/pose_pipeline -p test_group_pipeline.py
```

Version 2 group JSON uses `"formatVersion": 2` and `"people": [landmarkRows, ...]` instead
of `landmarks`. Each member still has 33 `[x,y,z,visibility]` rows. Coordinates for all members
share an origin at the mean hip midpoint and a unit of the mean 2D torso length, preserving
spacing and relative sizes. Both Android (`PoseGroup`) and Python (`normalize_group`) use this
frame. Version 1 single-person files remain supported. Group templates use `mirrorAllowed=false`;
the selfie preview mirrors the entire formation instead of choosing different sides per person.

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
