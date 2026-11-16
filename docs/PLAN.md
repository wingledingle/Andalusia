# Silpa: product and build plan

A professional drawing app for Android phones, tablets, foldables and ChromeOS.
It has three parts: a drawing studio, a self-organizing **Image Bank** of references and parts, and a public **Gallery**.

---

## 1. Research summary

### 1.1 UI design sources and what we take from each

| Source | What it says | How Silpa applies it |
|---|---|---|
| **Android Accessibility design guide** (developer.android.com) | Touch targets of at least 48dp. Text contrast of at least 4.5:1, and 3:1 for icons and other non-text elements. Body text no smaller than 12sp. Never require a gesture to complete an action; offer a visible alternative too. Add haptic feedback. | Every tool button gets a 48dp hit area, even where the icon is drawn at 24dp. Gestures such as two-finger undo always have an on-screen button as well. Short haptic ticks confirm undo, snapping and the color picker. |
| **Window size classes** (developer.android.com, Compose adaptive) | Width classes: Compact under 600dp, Medium 600–840dp, Expanded 840–1200dp, Large 1200–1600dp, XL from 1600dp. Height classes: Compact under 480dp. The class changes at runtime (fold, split-screen, resize). Design the Expanded layout first. | We design three studio layouts (below). The UI reacts live to folding and split-screen, and the tablet layout is designed before the phone layout. |
| **Nielsen Norman Group: 10 Usability Heuristics** | Show system status. Give users control and freedom (undo, redo, a way out). Keep things consistent. Prevent errors. Favor recognition over recall. Offer accelerators for experts. Keep the design minimal. Let users recover from errors in plain language. | Unlimited undo backed by a visual History panel. Autosave plus crash recovery. Brush previews show a real sample stroke instead of only a name. Hidden expert gestures and keyboard shortcuts. An on-screen indicator shows the active brush, size and opacity. |
| **Laws of UX** (lawsofux.com) | Fitts's law (big, near targets are faster). Hick's law and choice overload (fewer choices means faster decisions). Miller's law (people hold about 7±2 items in mind). Doherty threshold (respond within 400ms). Jakob's law (match the apps users already know). Aesthetic-usability effect. Tesler's law (complexity can be moved but not removed). Peak-end rule. | The main toolbar holds at most 7 tools. Advanced tools sit one level deeper, the way Procreate and Infinite Painter do it. Controls sit at screen edges where the hand rests. Every UI action responds in under 100ms and ink appears in under 20ms. Gesture conventions follow the competitors. Finishing and exporting a drawing ends with a satisfying moment, such as a timelapse preview. |
| **Infinite Painter interface docs** (competitor teardown) | Long press reveals secondary functions. Virtual sliders work by dragging on a button with no visible track. Mini-toolbars appear only while their tool is active. | Size and opacity are edge sliders you drag directly. Tool options appear in a contextual strip only while that tool is active. |

### 1.2 Silpa design principles

1. **The canvas comes first.** Chrome covers at most 15% of the screen on phones and 20% on tablets, and full-screen mode removes it entirely (tap with four fingers to toggle).
2. **The UI is neutral and the art provides the color.** The interface uses mid-dark neutral greys so they do not skew color perception. The brand blue appears only as an accent on selected and active states.
3. **Progressive disclosure.** The essentials are one tap away, power tools two taps away, and nothing is buried three levels deep.
4. **Everything can be undone and nothing is lost.** That covers autosave, the history panel, crash recovery and trash with restore.
5. **It looks like the icon.** The palette comes from the icon: cobalt `#1F3FD1`, sky `#4CB4E6`, a warm coral highlight `#F28B6B` and soft white.

### 1.3 Studio layouts by window size

| Window | Layout |
|---|---|
| **Compact (phones)** | Slim top bar with Gallery, Undo/Redo and Layers. Bottom tool dock with 5–7 tools. A floating size/opacity slider on the left edge. Panels open as bottom sheets. |
| **Medium (tablet portrait, unfolded foldable)** | Left rail for tools and right rail for color and layers. Panels float as cards that can be pinned. |
| **Expanded and larger (tablet landscape, ChromeOS)** | The panels for Layers, Color, Brushes and Image Bank references dock as columns that can be resized and collapsed. Keyboard shortcuts are supported. Workspaces can be saved. |
| **Compact height (phone landscape)** | Only the side rails remain and there is no top bar. |

The whole studio has a left-handed mode that mirrors it.

### 1.4 Visual system

- Built on Material 3 / Material You components, themed with a custom "Silpa" color scheme. The dynamic wallpaper color is not used in the studio, because UI color must stay neutral there. It can be enabled outside the studio.
- Spacing follows a 4dp/8dp grid. Corner radii are 12dp for cards and 28dp for sheets. Floating panels get a soft elevation shadow.
- Type is Plus Jakarta Sans for headings and Inter for UI text, in sp units so it scales with the system.
- Motion lasts 150–250ms with standard easing, and is reduced when the system "remove animations" setting is on.
- Dark theme is the default. A light theme is available.

---

## 2. Competitor feature inventory

| Feature | Procreate (iPad only; the benchmark) | Clip Studio Paint | ibis Paint X | Infinite Painter | Krita | MediBang | **Silpa target** |
|---|---|---|---|---|---|---|---|
| Pressure/tilt brush engine | ✓ Valkyrie engine, Brush Studio | ✓ realistic mixing, dual brush, .abr import | ✓ large library | ✓ natural-media brushes | ✓ many brush engines | ✓ 180+ brushes | ✓ GPU engine, Brush Studio, .abr import |
| Layers, masks, clipping, blend modes | ✓ | ✓ around 35 blend modes, adjustment layers | ✓ | ✓ Photoshop-style | ✓ | ✓ | ✓ including adjustment layers |
| Vector layers | – | ✓ | – | partial | ✓ | – | phase 3 |
| Selections and transform (warp, mesh, liquify) | ✓ | ✓ mesh, puppet warp, liquify | ✓ | ✓ | ✓ | basic | ✓ |
| Perspective and drawing guides | ✓ 2D grid, isometric, perspective, symmetry | ✓ perspective, fisheye and curve rulers | ✓ | ✓ 6 grid types (1/2/3-point, curvilinear, isometric) | ✓ assistants | ✓ | ✓ all of these |
| Symmetry / kaleidoscope | ✓ | ✓ | ✓ | ✓ up to 32 planes | ✓ | ✓ | ✓ |
| Line smoothing / stabilizer | ✓ StreamLine | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ with predictive inking |
| Shape snapping | ✓ QuickShape | ✓ Smart Shape | ✓ | ✓ | – | – | ✓ |
| Filters and adjustments | ✓ | ✓ very extensive | ✓ 80+ | ✓ | ✓ | ✓ | ✓ |
| Text | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Comics (panels, balloons, tones) | – | ✓ excellent | ✓ | – | partial | ✓ 1000 tones | phase 3 |
| Animation | ✓ Animation Assist; Dreams is a separate app | ✓ timeline, onion skin | ✓ basic | ✓ basic | ✓ | – | phase 4 |
| 3D models / poses | ✓ 3D painting | ✓ 3D figures, pose scanner | – | – | – | – | later; Image Bank covers pose references |
| Reference image window | ✓ | ✓ sub view | ✓ | ✓ | ✓ | – | ✓ fed by the Image Bank |
| **Organized asset bank** | – | Material palette (manual) | materials (manual) | – | resource tags (manual) | materials | **✓ auto-organized (key differentiator)** |
| Timelapse recording | ✓ | ✓ | ✓ | ✓ | – | – | ✓ |
| PSD import/export | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Community gallery | forum only | Clip Studio Share/Assets | ✓ online gallery | – | – | ✓ | ✓ |
| Business model | one-time payment | subscription tiers (DEBUT/PRO/EX) | free with ads, plus ad removal and Prime subscription | freemium with Pro unlock | free (open source) | free with ads | free with light ads, plus one-time "Silpa Pro" |

**Where Silpa can win:** No Android competitor offers Procreate-level polish together with an asset library that **organizes itself**. ibis Paint's ads are a frequent complaint, so "ads never appear on the canvas" is a selling point. Procreate does not exist on Android at all.

---

## 3. Feature specification

### 3.1 Studio (drawing)
- **Engine:** OpenGL ES 3.x canvas on a `SurfaceView`, with low-latency **front-buffered rendering** (`androidx.graphics:graphics-core`) and **motion prediction** (`androidx.input`). The target is under 20ms from pen to pixel on supported devices. Strokes use historical (batched) touch points, pressure, tilt and orientation. Tiled canvases of up to 8K, with the tile cache kept within the device's memory budget.
- **Brushes:** stamp brushes and smudge/blend brushes, plus wet mixing later. **Brush Studio** controls shape, grain, spacing, jitter, and the curves for pressure, tilt and velocity. Import of `.abr` (Photoshop) brush packs. About 120 bundled brushes in 12 sets.
- **Layers:** up to 200, limited by device. Folders, opacity, 25+ blend modes, clipping, alpha lock, masks, reference layers, and adjustment layers (HSL, curves, levels, gradient map).
- **Tools:** brush, eraser, smudge, fill (with gap closing), gradient, eyedropper (long press anywhere), selection (lasso, rectangle, ellipse, magic wand, selection pen), transform (free, distort, perspective, warp, mesh), liquify, clone, text and shapes.
- **Assists:** StreamLine-style smoothing, QuickShape-style snapping, rulers, and perspective guides (1/2/3-point, curvilinear, isometric). Symmetry modes: vertical, horizontal, radial and kaleidoscope (up to 32 planes).
- **Color:** wheel, square, HSV/RGB sliders and harmonies. Palettes with import from image or `.ase`. Recent colors. Color-blindness preview.
- **Gestures:** pinch to zoom and rotate. Two-finger tap undoes and three-finger tap redoes. Four-finger tap toggles full screen. Palm rejection plus a "stylus-only drawing" mode. Each gesture also has a visible button.
- **Files:** autosave every stroke batch and keep crash recovery. Native `.silpa` format (a zip of layer tiles plus a JSON manifest). Import and export of PSD, PNG, JPG and WebP, export of PDF, and an image-sequence or MP4 timelapse.
- **Later phases:** vector layers, comic panels and tones, frame-by-frame animation with onion skin.

### 3.2 Image Bank (self-organizing references)
Goal: drop images in and never organize them by hand.
- **Sources:** share-to-Silpa from any app, the photo picker, the camera, the canvas or a selection ("save to bank"), and the public Gallery (respecting licenses).
- **Automatic organization, all on-device, so it is private and works offline:**
  1. **Category classifier** (a small TFLite model plus ML Kit Image Labeling) sorts items into Faces, Eyes, Noses, Mouths, Ears, Hands, Hair, Full characters, Poses, Houses/Buildings, Interiors, Landscapes, Plants, Animals, Vehicles, Props, Textures and Patterns.
  2. **Face-part extraction:** when a face is detected (ML Kit Face Detection with landmarks and contours), Silpa offers to split it into eye, nose and mouth crops automatically, each filed in its own category.
  3. **Auto-tags:** dominant color palette, style (line art, painted, photo), orientation, and transparency (cut-out versus full image).
  4. **Similarity:** image embeddings drive search such as "show me eyes like this one" and group near-duplicates using a perceptual hash.
  5. **Smart collections** are rule-based, for example "Houses + blue + line art", and update themselves.
  6. **Manual override:** moving or retagging an item teaches the user's personal classifier through on-device fine-tuning of the final layer.
- **Use in the studio:** a dockable Bank panel. Drag an item onto the canvas to place it as a layer, a reference window, a stamp brush or a color palette.
- **Storage:** Room database plus app-private files. Optional cloud backup for signed-in users.

### 3.3 Gallery (online community)
- Browse Featured, New, Following and Tags. Each artwork page shows the timelapse replay if the artist shared it.
- Posting from the studio takes one tap. The artist chooses visibility, a license (all rights reserved, CC-BY or CC0) and "allow saving to Image Bank".
- Profiles, follows, likes, comments, collections.
- **Required by Google Play's user-generated-content policy:** users accept the terms before posting, in-app **report** for content and users, user **blocking**, ongoing moderation (an automated pre-screen plus a human review queue), and sensitive content hidden by default behind a filter that takes two or more actions to enable, with an age gate.
- **Backend recommendation:** Firebase (Auth, Firestore, Storage, Cloud Functions, plus Cloud Vision SafeSearch for pre-screening). It is the fastest to ship and scales without a dedicated ops team. Alternative: Supabase if you prefer Postgres and open source.

### 3.4 Monetization: Free (with ads) and Pro (no ads)
- **Recommended:** one Play listing, free with a one-time in-app purchase of "Silpa Pro" through Play Billing. Pro removes ads and unlocks extras such as unlimited layers above 20, the premium brush packs, 8K canvases and cloud backup of the Image Bank. A single listing keeps reviews and installs together and lets users upgrade without reinstalling. The codebase also uses Gradle `free`/`pro` product flavors, so a separate paid "Silpa Pro" listing can be built from the same code if you want one.
- **Rules for unobtrusive ads:**
  - **Never** in the studio or on the canvas, and never while drawing.
  - **No interstitials, no video ads and no pop-ups.**
  - One small adaptive banner at the bottom of the Home and Gallery screens, plus at most one clearly labelled native ad card per 12 Gallery items.
  - Consent through Google's UMP SDK (GDPR/CCPA), and no personalized ads without consent.

---

## 4. Architecture

- **Language and UI:** Kotlin, Jetpack Compose, Material 3 and the Compose adaptive layouts library.
- **Canvas:** OpenGL ES 3.x renderer (Kotlin with C++ via the NDK for hot paths such as compositing, PSD encoding and flood fill), front-buffered rendering and motion prediction.
- **Modules:**
  - `app`
  - `core:design` (theme and components)
  - `core:data` (Room, DataStore)
  - `feature:studio`, `feature:bank`, `feature:gallery`, `feature:home`
  - `engine` (rendering and brushes)
  - `ml` (classifiers)
  - `billing`, `ads` (stubbed out in the `pro` flavor)
- **Architecture pattern:** MVVM with unidirectional data flow, Hilt for dependency injection, and Coroutines/Flow.
- **Minimum SDK:** 29 (Android 10). Target SDK is the latest.
- **Quality:**
  - Unit tests for the engine math.
  - Screenshot tests for the UI.
  - Benchmark tests for stroke latency.
  - CI on GitHub Actions.

---

## 5. Icon
- The source image is in `branding/icon-source.jpg`. Android needs an **adaptive icon**, made of a foreground and a background layer in a 108dp square with a 66dp safe zone. We will use the sky-blue gradient as the background and the character as the foreground, cropped to the face and hair so it reads at 48dp, plus a monochrome themed-icon variant for Android 13 and later.
- ⚠️ **Store-policy note:** Google Play's sexual-content policy lists "sexually suggestive poses in which the subject is … minimally clothed" as a violation, and store listings are reviewed. The lower part of the image (chest, droplets) is risky as a launcher or store icon. Cropping to head and shoulders fixes this and also reads much better at small sizes.

---

## 6. Roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| **0. Foundation** | Gradle multi-module project, design system, adaptive icon, navigation shell, CI | The app builds and installs, and theme and icon are done |
| **1. Studio MVP** | Low-latency inking, pressure, 10 brushes, layers (basic), undo/redo, color picker, zoom/rotate, autosave, PNG/JPG export | Drawing a full illustration end-to-end feels smooth on a mid-range tablet |
| **2. Pro studio** | Brush Studio, blend modes, masks, selections, transform/liquify, filters and adjustments, guides, perspective, symmetry, text, PSD, timelapse | Feature parity with the core of Infinite Painter and ibis Paint |
| **3. Image Bank** | Import, auto-categorize, face-part extraction, tags, similarity search, smart collections, studio panel | 95% of test images land in the right category, or are fixed with one tap |
| **4. Gallery** | Accounts, posting, feed, profiles, moderation, report/block | Passes the Play user-generated-content requirements |
| **5. Monetization** | Billing, Pro unlock, ad rules, UMP consent | Ads never render in the studio, which is verified by a test |
| **6. Later** | Vector layers, comics tools, animation, cloud sync | — |

Honest scope note: an app at the level of Clip Studio or Procreate is a multi-year effort for a team. The phases above deliver a shippable, good-looking app first (phases 0–1), and grow it from there.
