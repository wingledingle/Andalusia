# Silpa: Plan

A professional drawing app for Android (phones, tablets, foldables and ChromeOS), Android 10 and up.

## Decisions
- **Business model:** one free app, with ads. A one-time **Silpa Pro** in-app purchase removes ads and unlocks Pro extras.
- **Gallery comes last.** No accounts or server setup until everything else is built.
- **Icon:** your image, used exactly as provided.
- **Minimum Android version:** Android 10 (API 29). Silpa targets the latest Android and uses the newest features where the device supports them.
- **Licenses:** Silpa's code and all its dependencies are Apache-2.0 or MIT, checked automatically on every build. Google Play Billing and Google's ad SDK are the only exceptions, because Play requires them.

---

## 1. Design rules
Sources: Android Accessibility guide, Android window size classes, Nielsen Norman Group usability heuristics, Laws of UX, and teardowns of competitors' interfaces.

- **Canvas first.** Controls use at most 15–20% of the screen. A four-finger tap gives a completely clean canvas.
- **Neutral grey UI** so it does not distort how you see your colors. The icon's cobalt `#1F3FD1` and sky blue `#4CB4E6` appear only as accents for selected items.
- **No more than 7 tools on the main bar.** Everything else is one tap away. Controls sit at the screen edges, where your hand rests.
- **Touch targets of at least 48dp.** Text contrast of at least 4.5:1, and icon contrast of at least 3:1.
- **Every gesture also has a visible button** (two-finger tap undo, three-finger tap redo, pinch to zoom and rotate, long-press for the eyedropper).
- **Speed targets:** ink appears in under 20ms and every UI action responds in under 100ms.
- **Nothing is lost:** unlimited undo, a visual history panel, autosave, crash recovery, and a trash folder you can restore from.
- **Layouts:**

  | Screen | Layout |
  |---|---|
  | Phone | Bottom tool dock; panels open as sheets from the bottom |
  | Tablet | Tool rails on both sides; floating panels you can pin |
  | Large tablet or ChromeOS | Docked panel columns, keyboard shortcuts, saved workspaces |

  The layout updates live when you fold the device, resize the window or use split-screen. A left-handed mode mirrors everything.
- **Visual style:** built on Material 3 with a 4dp/8dp spacing grid, 12dp rounded cards and 150–250ms animations. Dark theme by default, with a light theme option.

---

## 2. Competitors and their features

| Feature | Procreate | Clip Studio | ibis Paint X | Infinite Painter | Krita | MediBang | **Silpa** |
|---|---|---|---|---|---|---|---|
| Pressure/tilt brushes and a brush editor | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Photoshop brush (.abr) import | ✓ | ✓ | – | – | ✓ | – | ✓ |
| Layers, masks, clipping, blend modes | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Adjustment layers | – | ✓ | – | – | ✓ | – | ✓ |
| Vector layers | – | ✓ | – | partial | ✓ | – | ✓ |
| Selections, transform, warp, mesh, liquify | ✓ | ✓ | ✓ | ✓ | ✓ | basic | ✓ |
| Perspective guides and rulers | ✓ | ✓ | ✓ | ✓ (6 grids) | ✓ | ✓ | ✓ |
| Symmetry and kaleidoscope | ✓ | ✓ | ✓ | ✓ (32 planes) | ✓ | ✓ | ✓ |
| Line smoothing and shape snapping | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Filters | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Text | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Comic panels, speech balloons, screentones | – | ✓ | ✓ | – | partial | ✓ | ✓ |
| Frame-by-frame animation and onion skin | ✓ | ✓ | ✓ | ✓ | ✓ | – | ✓ |
| Reference window | ✓ | ✓ | ✓ | ✓ | ✓ | – | ✓ |
| Timelapse recording | ✓ | ✓ | ✓ | ✓ | – | – | ✓ |
| PSD import/export | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Asset bank that organizes itself** | – | manual | manual | – | manual | manual | **✓ automatic** |
| Community gallery | forum | ✓ | ✓ | – | – | ✓ | ✓ |
| Price model | paid | subscription | ads + removal / subscription | freemium | free | ads | free + Pro |

**Where Silpa wins:**
- Procreate-level polish on Android, where Procreate isn't available.
- An image bank that organizes itself.
- Ads that never appear on the canvas. Heavy ads are a common complaint about ibis Paint.

---

## 3. Features

### Studio
- **Engine:** GPU rendering with low-latency front-buffer inking and pen motion prediction. Uses stylus pressure, tilt and orientation. Large tiled canvases up to 16K, limited only by device memory.
- **Brushes:** 300+ bundled brushes, including pencils, inks, markers, paints, watercolor, airbrush, textures, effects, and comic tones and lines. Paint brushes mix color wet. The brush editor controls shape, grain, spacing, jitter and dual brush, plus pressure, tilt and speed curves. You can import Photoshop brushes (.abr) and import and export Silpa brush packs.
- **Layers:**
  - unlimited, up to device memory;
  - folders, opacity and 25+ blend modes;
  - clipping, alpha lock, masks and reference layers;
  - adjustment layers (hue/saturation, curves, levels, color balance, gradient map);
  - vector layers with editable strokes.
- **Tools:** brush, eraser, smudge, fill (with gap closing), gradient, eyedropper, selections (lasso, rectangle, ellipse, magic wand, selection pen), transform (free, distort, perspective, warp, mesh), liquify, clone, text, shapes.
- **Drawing aids:**
  - line smoothing and shape snapping;
  - rulers;
  - perspective guides (1/2/3-point, curved, isometric);
  - symmetry (vertical, horizontal, radial, kaleidoscope up to 32 planes).
- **Filters:** blurs (Gaussian, motion, radial), sharpen, noise, glow, chromatic aberration, halftone, posterize.
- **Color:** wheel, square and sliders; color harmonies; palettes, including palettes generated from an image and imported from Adobe swatch (.ase) files; recent colors; a color-blindness preview.
- **Comics:** panel layouts, speech balloons, screentones, speed lines and focus lines, and multi-page projects.
- **Animation:** frame-by-frame timeline, onion skin and frame rate control. Export to GIF, MP4 or a PNG sequence.
- **Files:** native `.silpa` format. Imports and exports PSD, PNG, JPG and WebP, exports PDF, and records timelapses.

### Image Bank (organizes itself)
- **Adding images:** share to Silpa from any app, the photo picker, the camera, or save a selection from the canvas.
- **Automatic sorting, done on the device so it is private and works offline.** Categories:
  - face parts: faces, eyes, noses, mouths, ears;
  - bodies and characters: hands, hair, characters, poses;
  - places: houses and buildings, interiors, landscapes, plants;
  - things: animals, vehicles, props;
  - surfaces: textures, patterns.
- **Face splitting:** when you add a face, Silpa cuts out the eyes, nose and mouth automatically and files each piece in its own category.
- **Automatic tags:** colors, style (line art, painted, photo), orientation, and whether the image is a cutout.
- **Finding images:** "find similar" search, duplicate detection, and smart folders that update themselves.
- **Learns from you:** if you move an item to a different category, future sorting adjusts.
- **Use while drawing:** drag an item onto the canvas as a layer, a floating reference, a stamp brush or a color palette.

### Free and Pro
- **Free:** the full drawing studio and the Image Bank, with light ads.
- **Pro (one-time purchase through Google Play):** no ads, plus premium brush packs, Pro filters and export options.
- **Ad rules:**
  - never shown in the studio;
  - no full-screen, video or pop-up ads;
  - one small banner on the Home and Gallery screens;
  - privacy consent through Google's UMP SDK.

### Gallery (built last)
- **Browsing:** Featured, New, Following and tag feeds, with timelapse replays.
- **Posting and social:** posting takes one tap from the studio. Profiles, follows, likes, comments and collections.
- **Licenses:** the artist chooses all rights reserved, CC-BY or CC0, and whether others can save the work to their Image Bank.
- **Required by Google Play:**
  - users accept the terms before posting;
  - in-app report and block;
  - automatic screening plus human review;
  - adult content hidden by default, with an age check.
- **Server:** Supabase (Apache-2.0), set up only when this phase starts.

---

## 4. Technology
- **App:** Kotlin, Jetpack Compose and Material 3. MVVM architecture, Hilt for dependency injection, Coroutines.
- **Drawing engine:** OpenGL ES 3, plus C++ through the NDK for heavy operations (compositing, fills, PSD).
- **Image sorting:** MediaPipe and LiteRT (Apache-2.0) running on the device.
- **Storage:** a Room database plus app-private files.
- **Modules:**

  | Group | Modules |
  |---|---|
  | App | `app` |
  | Core | `core:design`, `core:data` |
  | Features | `feature:home`, `feature:studio`, `feature:bank`, `feature:gallery` |
  | Engine and ML | `engine`, `ml` |
  | Monetization | `billing`, `ads` |

- **Quality checks:**
  - engine unit tests;
  - UI screenshot tests;
  - stroke latency benchmarks;
  - license check;
  - continuous integration (CI) on GitHub Actions.

---

## 5. Build order

| Step | Contents |
|---|---|
| 1 | **Foundation:** project setup, design system, your icon, navigation, Home screen |
| 2 | **Core studio:** fast inking, brushes, layers, undo/history, color, zoom/rotate, autosave, export |
| 3 | **Pro studio:** brush editor, blend modes, masks, adjustment layers, selections, transform/liquify, filters, guides, symmetry, text, PSD, timelapse |
| 4 | **Image Bank:** auto-sorting, face splitting, tags, similar search, smart folders, studio panel |
| 5 | **Comics, vector layers and animation** |
| 6 | **Free and Pro:** Play Billing, ads with the rules above |
| 7 | **Gallery:** accounts, server, feed, posting, moderation |
