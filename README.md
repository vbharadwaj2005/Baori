# Baori

*the well remembers*

**Baori** is a short, wordless narrative puzzle game for Android. A girl carries a clay pot through a drought-stricken village toward her grandmother's stepwell — the last place she remembers water. The stepwell's geometry only makes sense from certain angles: you rotate a symmetrical well (drag anywhere) and paths that were impossible become real, aligned in the projection. In the final levels, rotating the well also tilts remembered water back into it, drowning some stairs and revealing others.

Built with Kotlin and Jetpack Compose — one original mechanic (perspective-lock), one twist (water-tilt), no game engine, no ads, no third-party IP.

|---|---|
| **Platform** | Android (minSdk 26, targetSdk 36) |
| **Language / UI** | Kotlin, Jetpack Compose (custom `Canvas` rendering) |
| **Monetization** | RevenueCat — single non-consumable, entitlement `full_journey` |
| **Persistence** | DataStore (progress, purchase cache, settings) |
| **License** | MIT |

---

## Why I built it

I grew up hearing about stepwells — *baoris* — the inverted temples carved into the dry earth of western India, built by generations who understood that water is memory. Many of them stand empty now. Baori is my way of putting a hand on that stone: a game about a girl, a pot, and a well that only gives its paths back when you look at it the way her grandmother did. The perspective-lock mechanic is not a gimmick bolted onto that story — it *is* the story: geometry that only connects when remembered from the right angle.

— Bharadwaj

---

## What's in the game

- **5 levels**, each a variation on one mechanic:
  1. Rotation reveals a path (free)
  2. Two independent alignments must be found (free)
  3. A path piece that aligns only briefly (free — paywall shown at its end)
  4. Water-tilt introduced: rotation raises and lowers the water plane (paid)
  5. Finale: rotation + water-tilt combined (paid)
- **Wordless murals** between levels — original flat-color scenes drawn in code, no text, no dialogue.
- **Accessibility** treated as requirements, not polish: reduce-motion toggle, ambient mute, light/dark theme override, shape + color goal markers (never color alone), TalkBack labels on every control, haptics on completion, easing (never linear motion) on every camera rotation and character move.

### How to play

1. From the menu, tap a level number.
2. **Drag horizontally** anywhere on the scene to rotate the stepwell.
3. When edges align, paths light up (sunlit stone vs. dormant stone) and the **Walk** button becomes active — the button state itself teaches the mechanic.
4. From level 2 on, walks are **chunks**: Walk carries the girl as far as the currently aligned geometry reaches, then you rotate again to make the next stretch real. Rotating between walks is the intended rhythm, not a mistake.
5. From level 4 on, rotation also moves the **water**: stairs under the surface show as water-tinted (aligned, but not walkable) until you rotate the water away — and stairs that were "underwater" are revealed as it drains.
6. The marigold **ring + diamond** marker is the goal. Reach it to finish the level.

---

## Requirements

- **Android Studio** (any recent version; its embedded JDK works) **or** a local **JDK 17** — the committed Gradle wrapper supplies everything else
- Android SDK **36** (installed automatically by Android Studio on first sync)
- A device or emulator on Android 8.0 (API 26) or newer
- No RevenueCat key required to run — see below

---

## Build & run

### Android Studio (recommended)

1. **File → Open** and select this repository's root folder.
2. Let Gradle sync finish (it will download the wrapper distribution declared in `gradle/wrapper/gradle-wrapper.properties`).
3. Pick a device/emulator and press **Run**.

> The Gradle wrapper (`gradlew` / `gradlew.bat`) is committed, so a clean clone needs only a JDK — no local Gradle install. If your tooling ever strips the wrapper jar, regenerate it once with a local Gradle install:
> ```bash
> gradle wrapper --gradle-version 8.14.3
> ```

### Command line

```bash
# Debug build
./gradlew assembleDebug

# Install on a connected device/emulator
./gradlew installDebug

# Unit tests (engine + data layer — no device needed)
./gradlew test
# or, scoped:
./gradlew :app:testDebugUnitTest
```

The APK lands in `app/build/outputs/apk/debug/`.

### Run without any setup

A clean clone runs out of the box. Without a RevenueCat key the app automatically uses a **fake billing service** that mirrors the real purchase flow (success / cancel / error), so every screen — including the paywall at the end of Level 3 — is fully demoable with zero configuration.

---

## Supplying your own RevenueCat key

The key is injected at **build time** and is never committed.

### Development — Test Store (recommended)

RevenueCat's Test Store needs **no App Store Connect or Play Console setup** and produces real-shaped `CustomerInfo`/entitlement objects.

1. Create a free project at [app.revenuecat.com](https://app.revenuecat.com).
2. **Project Settings → API Keys** → copy a **Test Store API key** (a public "goog…" style key works for dev builds; never use a secret key in an app).
3. Copy the example file and paste your key:

   ```bash
   cp local.properties.example local.properties
   ```

   then in `local.properties`:

   ```properties
   REVENUECAT_API_KEY=your_test_store_api_key_here
   ```

   `local.properties` is already gitignored — **never commit it.**

4. Build and run. The app now uses the real SDK against the Test Store.

Alternatively, export it as an environment variable instead of editing `local.properties`:

```bash
export REVENUECAT_API_KEY=your_test_store_api_key_here
./gradlew assembleDebug
```

Resolution order: `local.properties` → `REVENUECAT_API_KEY` env var → placeholder fallback (fake billing).

### Product setup on the RevenueCat dashboard

The app expects exactly this configuration (ids are defined in one place, `data/billing/BillingService`):

| Thing | Value |
|---|---|
| Entitlement | `full_journey` |
| Product (non-consumable) | `baori_full_journey` |
| Offering | `default` — containing **one** package |

If the `default` offering id was renamed in the dashboard, the code falls back to the *current* offering.

### Production — swapping in a real Android API key

For a real store release:

1. In the RevenueCat dashboard, create an **Android** app entry and copy its **public Android SDK key** (Play Console linked, products configured as real in-app products).
2. Put that key in `local.properties` as `REVENUECAT_API_KEY` (or the env var) — **no code changes are needed**; the same build-time injection path is used.
3. Never ship or commit a Test key in a release path, and never commit any key at all. The git history must stay key-free.
4. Release builds run R8 minification; `app/proguard-rules.pro` already keeps the RevenueCat reflection surface.

---

## Architecture

One Activity, Compose all the way down, no game engine. Everything the player sees is drawn in a single `Canvas` from an orthographic projection.

📖 **Full module-by-module walkthrough, data flows, and design decisions: [ARCHITECTURE.md](ARCHITECTURE.md)**

```
┌─────────────────────────────────────────────────────────────┐
│ MainActivity (Compose root, hand-rolled 4-screen backstack) │
│   MainMenuScreen ─ GameScreen ─ MuralScreen ─ PaywallScreen │
└───────────────┬─────────────────────────────────────────────┘
                │ StateFlow<UiState> / events
┌───────────────▼─────────────────────────────────────────────┐
│ viewmodel/   GameViewModel · MenuViewModel ·                │
│              PaywallViewModel · SettingsViewModel           │
└───────┬────────────────────────────────────────┬────────────┘
        │ pure engine calls                      │ app-owned interfaces
┌───────▼───────────────────────┐   ┌────────────▼─────────────────┐
│ engine/ (pure Kotlin, no      │   │ data/                        │
│ Android deps, unit-tested)    │   │  LevelRepository (JSON)      │
│  ProjectionMath   rotate +    │   │  BaoriPreferences (DataStore)│
│                   project     │   │  billing/BillingService      │
│                   (ortho)     │   │    ├ RevenueCatBillingService│
│  RotationController  camera   │   │    └ FakeBillingService      │
│  PathAligner      which paths │   │  model/Level (serializable)  │
│                   are real now│   └──────────────────────────────┘
│  StepwellGeometry walk graph  │
│  WaterPlane       angle→height│
│  Easings          house curves│
└───────┬───────────────────────┘
        │
┌───────▼────────────────────────┐
│ assets/levels/level1..5.json   │  ← level design is data, not code
└────────────────────────────────┘
```

### Key components

| Component | Responsibility |
|---|---|
| `ProjectionMath` | Rotates the well around Y and projects it orthographically: `canvasX = cx + x'·s`, `canvasY = cy − (y·cos θ + z'·sin θ)·s`. Height and depth both fold into screen-y — this is what makes edges "click" into alignment only at certain angles. |
| `RotationController` | Owns the camera angle as a `StateFlow<Float>`. Drag is continuous; release snaps to the nearest goal angle within capture range, else to the free-rotation grid (15°). Always travels the shortest arc. |
| `PathAligner` | The perspective-lock contract: a segment is walkable **iff** the camera angle sits inside its tolerance window (compared on the shortest arc, so 359° and 1° are neighbors). |
| `StepwellGeometry` | Derives nodes from segment endpoints and runs BFS over *currently aligned* segments — the girl can never step on stone that isn't real right now. |
| `WaterPlane` | A raised-cosine bump mapping rotation angle → water height; deeper stairs drown first, so revealing hidden stairs reads spatially, not as a switch. |
| `BillingService` | The only monetization surface. UI/ViewModels never touch the RevenueCat SDK; the interface gives judges a clean seam, gives tests a fake, and makes swapping the Test key for production a config change, not a refactor. |

### The perspective-lock illusion — why no 3D engine

This is a deliberate, documented technical choice. The "impossible geometry" look is reproduced with 2D math: an axis-aligned stepwell rotated around Y and projected orthographically. A 3D engine (Unity, SceneView, Filament) would add setup cost, APK weight, and a debugging surface that a 3–5 day build window in Kotlin/Compose cannot afford — while making the code *less* inspectable. The whole illusion lives in ~100 lines of pure Kotlin that runs identically on the JVM in unit tests. That inspectability is the point.

### Level format (assets/levels/)

Levels are JSON so level design is text editing and judges can read the puzzles as plain data — no geometry is hardcoded in Composables:

```json
{
  "id": "level1", "order": 1, "free": true,
  "goalAnglesDeg": [90],          // the puzzle's "answers"
  "toleranceDeg": 4,              // alignment window half-width
  "snapStepDeg": 15,
  "startAngleDeg": 0,
  "water": null,                  // { "fullWaterAngleDeg": 60, ... } on L4/5
  "startNode": "village", "goalNode": "landing",
  "muralAfter": "mural_1",
  "segments": [
    { "id": "s_gate_landing", "a": "gate", "b": "landing",
      "alignsAtDeg": 90, "toleranceDeg": 4,
      "start": { "x": -1.0, "y": 0.0, "z": 0.0 },
      "end":   { "x":  0.0, "y": -1.0, "z": 0.0 } }
  ]
}
```

Murals are original flat-color vector scenes composed directly in `MuralScreen` (token-driven colors, light/dark aware) — an asset pipeline is deliberately not needed.

---

## Testing

The engine is pure Kotlin with zero Android imports, so the heart of the game is covered by fast JVM tests — **8 suites, 69 tests, all green** — no emulator, no device:

```bash
./gradlew test
```

| Suite | Pins down |
|---|---|
| `ProjectionMathTest` | rotation invariants (radius preserved, invertible), projection formula, shortest-arc, wrap-around |
| `RotationControllerTest` | snap-to-goal vs snap-to-grid, capture range, 360° wrap, shortest-arc interpolation |
| `PathAlignerTest` | the perspective-lock contract: window edges, per-segment tolerance, wrap-around |
| `StepwellGeometryTest` | node derivation, BFS reachability over aligned segments, the chunk-walk contract, water-gated walkability |
| `WaterPlaneTest` | peak/span/symmetry of the water curve, deep-stairs-drown-first |
| `EasingsTest` | curve endpoints, monotonicity, clamping |
| `LevelRepositoryTest` | JSON parsing, ordering, forward-compatible schema |
| `LevelSolvabilityTest` | **every bundled level is simulated start to finish through the real engine** — alignment, water, and the greedy walk contract — so a JSON edit that makes a level unwinnable fails the build instead of shipping |

---

## Monetization design

- **One** non-consumable product, **one** offering, **one** paywall moment: at the end of Level 3 — a natural story cliffhanger, and never before.
- **No dark patterns**: no fake urgency, no countdowns, no dismiss-blocking, cancel is never shown as an error, and **Restore purchases** is always visible and functional.
- A `CustomerInfo` update listener keeps unlock state reactive: a restored purchase reflects in the UI without a restart.
- The paywall screen uses the pot/well art motif drawn from the game's own tokens — not a generic system paywall.

## Post-hackathon roadmap

- **Chapter packs** as additional RevenueCat offerings: e.g. *"The Monsoon Letters"* (levels 6–10) and *"What the Water Kept"* — the same well, new geometries, water mechanics deepened. The single-purchase structure extends to per-chapter entitlements without touching UI code; `BillingService` already returns the package abstractly.
- Bundled display typeface (a one-file swap in `ui/theme/GameTypography.kt`), asset-pipeline murals, ambient audio loop + snap SFX (self-recorded or CC0, cited here), and a playtest-driven pass on tolerance/snap tuning.

---

## Third-party credits

All game art, level geometry, and murals are **original** and generated in-code; nothing is traced or borrowed. No third-party trademarks or music are used.

| Library | License |
|---|---|
| AndroidX / Jetpack Compose / Material Icons | Apache-2.0 |
| Kotlin & kotlinx.serialization / coroutines | Apache-2.0 |
| DataStore Preferences | Apache-2.0 |
| RevenueCat `purchases-android` | MIT |
| JUnit 4 | EPL-1.0 |

## License

MIT — see [LICENSE](LICENSE).
