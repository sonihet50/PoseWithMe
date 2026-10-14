# PoseWithMe

A location-aware photography assistant for Android. It suggests a pose for where
you are and shows that pose as a translucent mannequin "ghost" over the live
camera. It tracks your body on-device with MediaPipe, scores how closely you
match, gives one correction at a time ("Raise your left arm a little"), and takes
the photo for you once you hold the pose.

Built for the Android Programming course: Java, XML layouts, CameraX, SQLite and
SharedPreferences. There is no backend.

## Features

| Area | What it does |
|---|---|
| Live camera | CameraX preview in a 3:4 viewfinder, front and back cameras, rule-of-thirds grid, performance overlay |
| Pose detection | MediaPipe Pose Landmarker Lite, on-device, live-stream mode, 33 body landmarks |
| Guidance | Target-pose ghost anchored to your hips and scaled to your torso; live match %; per-body-part status (head, torso, arms, legs); one smoothed correction hint |
| Pose library | 12 built-in poses (full body and waist-up) shown as mannequin silhouettes, with steps and photo credits |
| Record a pose | Countdown, then "hold still" detection; the held frames are averaged into a reusable pose (full body or half body) |
| Photo capture | Manual shutter, self-timer (off/3/5/10 s), optional auto-capture when the pose is held (fires once per pose), review with Retake/Share/Keep |
| Gallery | Photos saved to `Pictures/PoseWithMe`; metadata in SQLite (pose, score, time); view, share, delete |
| Favorites | Heart any pose; stored in SQLite |
| Recommendations | Location, then a hand-picked landmark (offline) or an OpenStreetMap lookup, then a scene (beach, monument, park, café, scenic…), then ranked poses; manual scene picker as a fallback |
| Settings | Auto-capture and its threshold, self-timer, selfie mirroring, hints/ghost/skeleton toggles, preferred pose styles |

## Architecture

```
com.s23cp5054.posewithme
├── MainActivity                 home screen
├── camera/                      PreviewTransform (view mapping), FrameConverter (RGBA → upright bitmap)
├── pose/
│   ├── detection/               PoseDetector (MediaPipe wrapper), PoseFrame
│   ├── model/                   Landmark, BodyPose (body-relative frame), PoseTemplate (+ JSON)
│   ├── matching/                PoseMatcher, MatchConfig, MatchResult, GuidanceSmoother, AutoCaptureTrigger
│   ├── recording/               PoseRecorder (countdown → hold still → average)
│   └── recommendation/          Scene, SceneMapper, CuratedPlace, PoseRecommender, GeoUtils
├── location/                    LocationHelper (platform LocationManager), PlaceFinder (Overpass API)
├── data/
│   ├── database/                AppDatabase (SQLiteOpenHelper: photos, favorites)
│   ├── model/                   Photo
│   ├── repository/              PoseRepository, PhotoRepository, FavoritesRepository, PlacesRepository
│   └── settings/                AppSettings (SharedPreferences)
├── storage/                     PhotoStorage (MediaStore output, share intent)
├── ui/
│   ├── camera/                  CameraActivity, PoseOverlayView, CompositionGridView, GuidanceText
│   ├── pose/                    SilhouetteRenderer, PoseThumbnailView
│   ├── poses/                   PoseLibraryActivity, PoseDetailSheet, PoseCardAdapter, PoseLabels
│   ├── gallery/                 GalleryActivity, PhotoActivity
│   ├── nearby/                  NearbyActivity
│   └── settings/                SettingsActivity
└── utils/                       ImageLoader (downscaled decode + LRU cache), PerfStats
```

### Frame pipeline

CameraX `ImageAnalysis` (640×480, RGBA, keep-only-latest) runs on one analysis
thread. Each frame is converted to an upright bitmap that is allocated once and
reused. MediaPipe `detectAsync` then runs in live-stream mode, with only one
frame in flight; frames that arrive while it's busy are dropped, not queued.
The result callback converts the landmarks to the body frame and runs the
matcher, which is just arithmetic. The UI thread then draws the overlay and
updates the hints.

### How matching works

Every pose is stored in a body-relative frame: origin at the hip midpoint, one
unit equal to the hip-to-shoulder length. This removes body size and camera
distance. Then:

- **Arms:** the direction of each bone is compared relative to the person's own
  torso axis, so a slightly tilted phone doesn't count against you.
- **Legs:** compared relative to the hip line, so leaning doesn't make your legs
  look wrong.
- **Torso:** lean and turn (shoulder width), with a tighter tolerance than limbs.
- **Head:** the direction from the shoulders to the nose.
- **Unseen joints:** landmarks with low visibility, live or in the target, are
  left out rather than scored as wrong. Waist-up poses ignore the legs.
- **Mirrored poses:** where a pose allows it, the mirrored version is also tried,
  so it can be done with either side.
- **Smoothing:** scores are averaged over time, and a hint has to persist for
  0.6 s before it's shown.

All thresholds and weights live in `MatchConfig`.

## Pose library and data sources

Built-in poses in `app/src/main/assets/poses/` came from openly licensed photos
(CC0, Public Domain Mark, CC BY 2.0) found through Openverse. They went through
MediaPipe, automatic quality filters and manual review. Only body coordinates and
attribution are stored, never the photos, and each pose credits its
photographer. The offline tooling is in [`tools/pose_pipeline/`](tools/pose_pipeline/README.md).

`assets/places.json` lists landmarks with **hand-picked** poses. The app labels
these as suggestions, not as "popular": it has no visitor statistics. The only
usage data it uses is the count of photos taken with each pose on this phone.

Place names come from OpenStreetMap (© OpenStreetMap contributors, ODbL) via the
Overpass API, and are looked up only when the Nearby screen is open.

## Privacy

- Camera frames are processed on the device and are never uploaded.
- Location is used only on the Nearby screen. Only the coordinates are sent to
  the Overpass API to find the nearest place, and nothing else leaves the phone.
- Photos go to the phone's own gallery. The database holds only metadata.

## Building

Requirements: Android Studio with JDK 17 or newer (the bundled JBR works) and
Android SDK 36.

```
gradlew assembleDebug          # debug APK
gradlew testDebugUnitTest      # 42 JVM unit tests (matcher, recorder, auto-capture, recommendations…)
gradlew assembleRelease        # R8-shrunk release APK, signed with the debug key for testing only
```

The app ships native code for `arm64-v8a` only, so it runs on physical ARM
phones, not on x86 emulators.

## Measurements

Measured on a vivo V27 Pro (Android 15) unless noted.

| Metric | Value |
|---|---|
| Release APK size | 22.1 MB (MediaPipe native library 14.4 MB, pose model 5.8 MB) |
| Pose updates per second | about 20 in indoor light (limited by the camera frame rate; no frames were dropped) |
| Model inference time | about 33 ms per frame (CPU) |
| Frame-to-result latency | about 36 ms average, 39 ms max |
| Memory while the camera runs | about 104 MB native heap and 13 MB Java heap (from the in-app readout) |
| Stability | no crashes during app switching and restarts in manual testing |

Turn on the stats overlay on the camera screen (the chart icon), or filter
logcat by `PoseWithMe.Perf`, to repeat these measurements.

## Known limitations

- Matching is a hand-tuned geometric heuristic for front-facing photo poses. Side
  and back views and heavily occluded poses (for example, crossed arms in loose
  clothing) score less reliably.
- Seated poses depend on MediaPipe's leg estimates, which are noisier than
  standing ones.
- Sharing recorded poses between users is not built yet. A pose is a 2 KB JSON
  file, so file or QR-code sharing would be a natural next step without needing
  a server.
- `places.json` covers a handful of landmarks; add entries to extend it.
