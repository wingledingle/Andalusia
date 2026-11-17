# Silpa

A professional drawing app for Android 10+ (phones, tablets, foldables, ChromeOS). See `docs/PLAN.md` for the full plan.

## What's built
- **Drawing engine** (`engine/`): sparse 256px tiled canvases up to 16384 px, 67 brushes in 15 sets (pressure, tilt and speed dynamics, grain, wet mixing, smudge, glow), StreamLine smoothing, QuickShape (hold at the end of a stroke), 17 blend modes, clipping masks, alpha lock, reference layers, selections (lasso, rectangle, ellipse, magic wand; add/subtract), transform (uniform/free/distort, flip, rotate), flood fill and gradients, 16 filters, text, comic panels / speed & focus lines / screentone / speech balloons, symmetry (up to 32 planes), grid, isometric and 1/2/3-point perspective with drawing assist, frame-by-frame animation with onion skins, memory-budgeted undo, incremental autosave, timelapse recording.
- **Files**: PSD import/export (layered), PNG, JPEG, WebP, PDF, `.silpa` packages, animated GIF, MP4 (animation and timelapse), Photoshop `.abr` brush import (sampled brushes).
- **Image Bank** (`ml/`, `feature/bank/`): on-device sorting into 18 categories (MediaPipe classifier, embeddings and face landmarks), automatic eye/nose/mouth cut-outs from faces, colour and style tags, duplicate detection, find-similar, smart folders, learning from your corrections. Seeded with 28 CC0/public-domain illustrations (`ART_CREDITS.md`).
- **UI**: Jetpack Compose + Material 3 with SkeuoCompose (Apache-2.0) hardware-style controls and Tabler icons (MIT). Adaptive layouts for phones and tablets; left-handed mode.
- **Free + Pro**: one free app with a one-time Pro purchase that removes ads. A single banner on Home and Image Bank only, shown after privacy consent; never in the studio.

## Before release
1. Create an in-app product with ID `silpa_pro` in Play Console.
2. Replace the AdMob **test** IDs: `APPLICATION_ID` in `app/src/main/AndroidManifest.xml` and `BANNER_UNIT_ID` in `monetization/.../Ads.kt`.
3. Set up release signing, then build an App Bundle: `./gradlew bundleRelease`.

## Build & test
```
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true   # engine tests + screenshots in app/build/screens
```
