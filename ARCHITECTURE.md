# Baori — Architecture

*the well remembers*

This document walks through the whole system module by module: how the perspective-lock illusion is computed, how a drag becomes a walked path, how the RevenueCat unlock state reaches the UI, and why each significant choice was made. Pair it with [README.md](README.md) for build/run instructions.

---

## 1. Layer map

```
┌──────────────────────────────────────────────────────────────────┐
│  UI (Compose)                                                    │
│  MainActivity → BaoriRoot (hand-rolled backstack, 4 destinations)│
│    MainMenuScreen · GameScreen · MuralScreen · PaywallScreen     │
│    StepwellScene (single Canvas, painter's-algorithm layers)     │
└───────────────┬──────────────────────────────────────────────────┘
                │  StateFlow<UiState> down, event lambdas up
┌───────────────▼──────────────────────────────────────────────────┐
│  viewmodel/                                                      │
│  GameViewModel · MenuViewModel · PaywallViewModel ·              │
│  SettingsViewModel   (one per screen, StateFlow only)            │
└───────┬──────────────────────────────────┬───────────────────────┘
        │ pure calls                       │ app-owned interfaces
┌───────▼───────────────────┐   ┌──────────▼─────────────────────┐
│  engine/  (pure Kotlin,   │   │  data/                         │
│  zero Android imports,    │   │  LevelRepository  ← JSON       │
│  fully unit-testable)     │   │  BaoriPreferences ← DataStore  │
│                           │   │  billing/BillingService        │
│  ProjectionMath           │   │    ├ RevenueCatBillingService  │
│  RotationController       │   │    └ FakeBillingService        │
│  PathAligner              │   │  model/Level (kotlinx.serial.) │
│  StepwellGeometry         │   └────────────────────────────────┘
│  WaterPlane · Easings     │
└───────────────────────────┘
        ▲
        │ reads
┌───────┴───────────────────────────┐
│  assets/levels/index.json         │
│  assets/levels/level1..5.json     │
└───────────────────────────────────┘
```

Dependency rule: **UI → viewmodel → (engine | data)**. Nothing in `engine/` imports Android; nothing in `viewmodel/` imports Compose; the RevenueCat SDK appears in exactly one class.

---

## 2. The perspective-lock illusion

The entire "impossible geometry" effect is two pure functions in `ProjectionMath`:

**1. Rotation around the vertical axis** — the player's only verb:

```
x' =  x·cos θ + z·sin θ
y' =  y
z' = −x·sin θ + z·cos θ
```

**2. Orthographic projection** with a fixed camera tilt (35°, "standing at the rim, looking down"):

```
canvasX = centerX + x'·scale
canvasY = centerY − (y·cos tilt + z'·sin tilt)·scale
```

The key trick: **depth leaks into screen-y**. Because `z'` is weighted into the vertical position by `sin tilt`, rotating the well doesn't just spin it — it visibly *rearranges* the geometry on screen. Edges that were far apart slide together and, at specific angles, land exactly on top of each other. Those angles are the puzzle's answers.

No perspective divide, no depth buffer, no engine. Draw order is painter's algorithm (back-to-front by `z'`), and the renderer fits the whole well by computing the worst-case rotation radius of every node.

### Where each layer of the scene comes from

`StepwellScene` draws in this fixed order:

1. **Shaft backdrop** — the deep throat of the well, from the projected bounding walls, translucent
2. **Dormant segments** — paths that exist in the level but aren't aligned now (`pathDormant`)
3. **Walkable segments** — paths whose window contains the current angle (`pathLit`, thicker)
4. **Goal marker** — marigold ring + diamond outline (shape + color, colorblind-safe)
5. **Water plane** — surface line + translucent body, only on levels with `water` config, only when level > 0
6. **The girl** — sari-colored dot with halo, lerped between nodes while walking

Every color above is a semantic token from `ui/theme/GameColors.kt` — no raw hex at any call site, light and dark palettes both define the full set.

---

## 3. One drag, end to end

A single gesture, traced through every layer:

```
finger drag
  → StepwellScene.pointerInput(detectDragGestures)
      degrees = Δpx × 0.35 (GameDimensions.DRAG_DEGREES_PER_PIXEL)
  → GameViewModel.dragBy(Δdeg)
  → RotationController.dragBy(Δdeg)      angle normalized to [0,360)
  → PathAligner.evaluate(segments, angle)
        for each segment: |shortestArcDeg(angle, alignsAt)| ≤ tolerance
        → Alignment(walkableIds, activeGoalDeg)
  → StepwellGeometry.findPath(walkable, heroNode, goalNode)
  → GameUiState updated (single StateFlow emission)
  → GameScreen recomposes; Canvas redraws; Walk button enables/disables
```

**On release:**

```
onDragEnd
  → GameViewModel.endDrag(reduceMotion)
      reduceMotion? → snapTo(nearest target) instantly
      else → RotationController.endDrag() returns the snap target:
               nearest goal angle if within 10° capture range,
               else nearest multiple of snapStepDeg (15°)
             animate 260ms with Easings.easeOutCubic, re-evaluating
             alignment on every frame
```

**On Walk (the chunk-walk contract):** no single angle aligns a whole route on levels 2–5, so `tryWalk()` walks the girl as far as the currently aligned graph makes progress toward the goal — [StepwellGeometry.chunkWalkTarget] picks the reachable node closest to the goal in the well's real geography ([distancesTo] BFS over **all** segments, ignoring alignment; decoy dead ends have no distance, so they can never win). If the goal itself is reachable, she walks straight to it and the level completes. During the walk, alignment is frozen — rotating mid-step cannot dissolve the stone beneath her. Each step animates 420ms with `easeInOutCubic`; completion fires a haptic pulse and persists progress.

The result surfaces to the UI as a three-tier affordance (`GameUiState.AdvanceTier`): **REACHABLE** (goal on the aligned graph — Walk finishes the level), **PROGRESS** (a chunk walk moves her closer — rotate-and-walk rhythm), **STUCK** (Walk disabled — only rotation helps). One computation in `reevaluate()` feeds both the button and the next `tryWalk`, so the affordance can never disagree with the contract.

### The design intent

The tolerance window (default 4°) and the snap capture range (10°) are the two knobs that make the game feel generous without giving the answer away. Wide enough to stumble into while exploring; narrow enough that the "aha" is earned. All of this is pinned by unit tests (`PathAlignerTest`, `RotationControllerTest`) so tuning can't silently break the contract.

---

## 4. Water-tilt (levels 4–5)

`WaterPlane` maps the same rotation angle to a water height, so the player's single verb now does two things at once:

```
level(angle)  = raised-cosine bump, 0 outside span, 1 at fullWaterAngle
surfaceY      = shaftBottomY + level × (maxHeightY − shaftBottomY)
submerged(y)  = surfaceY > stairPlatformY
```

The engine shipped with tests on Day 1; the level JSON only consumes it:

```json
"water": { "fullWaterAngleDeg": 60, "spanDeg": 60, "maxHeightUnits": 1.2 }
```

Because the surface sweeps continuously from the shaft floor, **deeper stairs drown first** — the reveal reads spatially, not as a switch. The ViewModel computes the surface on every angle change (`GameViewModel.reevaluate`), exposes `waterSurfaceY` for the renderer, and folds submersion into walkability via `StepwellGeometry.walkableSegmentIds`: a segment is walkable only when **aligned AND dry**. A segment's platform height is its shallower endpoint (`platformYOf`), so a stair drowns only when the whole run is under water. (Heights are absolute well units — `maxHeightY` is the peak *surface* height, and `shaftBottomY` mirrors it below zero.)

The renderer tells the three states apart: dormant (dim), **drowned-but-aligned (water-tinted** — "this exists, rotate the water away"), and walkable (sunlit). The surface line is drawn at the exact height the engine used, so the picture can never lie about what is walkable.

```
"water": { "fullWaterAngleDeg": 90, "spanDeg": 75, "maxHeightUnits": 1.5 }  // level5
```

Levels 4–5 are designed around the curve's algebra: at the peak angle `surfaceY = maxHeightY`, outside the span `surfaceY = −maxHeightUnits` (the drained shaft floor), and `platformYOf` uses the higher endpoint — so required stairs sit in dry windows at their snap angles while decoys sit aligned *inside* the flood band. The finale (level 5) opens at the flood peak with nothing walkable, drains at the drought angle where the hidden stair reveals, and finishes at the water itself, dry.

**Levels ship with a solvability proof:** `LevelSolvabilityTest` simulates every bundled level start to finish — `PathAligner` windows, `WaterPlane` submersion, the greedy chunk-walk contract, the snap angles a player actually lands on — so an edit that makes a level unwinnable fails the build.

---

## 5. Content pipeline: levels as data

No level geometry lives in code. `LevelRepository` parses `assets/levels/` through a tiny `AssetReader` interface — the seam that lets the whole pipeline run on the JVM in `LevelRepositoryTest` with an in-memory fake.

```
index.json ──► loadIndex() ──► [LevelRef(id, file, free)]
                                   │ for each
                                   ▼
             loadLevel("levels/<file>") ──► Level (kotlinx.serialization)
                                   │
                                   ▼
             loadAll() ──► sorted by `order` ──► ViewModels
```

Design consequences worth calling out:

- **Level design is text editing.** Adding a level = one JSON file + one index line.
- **The JSON contract is flat and small** (`Level.kt` documents every field). `ignoreUnknownKeys = true` makes old builds tolerant of future fields — forward compatibility for free.
- **`free` in two places** (index + level) is deliberate: the index gates the menu cheaply, the level object gates the runtime.
- Judges can read every puzzle as plain data in the repo.

---

## 6. Billing: the one-wrapper rule

The RevenueCat SDK appears in exactly one class (`RevenueCatBillingService`). Everything else — ViewModels, screens, tests — sees only `BillingService`:

```
                 ┌────────────────────────── logic-free ─────────────────┐
   PaywallViewModel ──► BillingService ◄── MenuViewModel (gating)       │
        │                        │                                       │
        │               ┌────────┴─────────┐                             │
        │               ▼                  ▼                             │
        │    RevenueCatBillingService   FakeBillingService               │
        │    (real SDK, Test Store)     (no key? clean clones, CI)       │
        │               │                  │                             │
        └───────────────┴──── writes ──────┴──► BaoriPreferences          │
                                                (cached entitlement,     │
                                                 fast pre-render hint)   │
```

Flow mapping to PRD §8.2:

1. **Launch** — `BaoriApplication.onCreate()` configures `Purchases` with the build-time-injected key. Placeholder key → `FakeBillingService` instead (clean clone runs fully).
2. **`awaitOfferings`** — `loadUnlockPackage()` fetches the single package; failure returns `null`, never throws into the UI.
3. **Paywall** — shown exactly once, at the end of Level 3 (and whenever paid content is otherwise reached unlocked); the `needsPurchase` flag on menu items comes from the same interface.
4. **Purchase** — `purchase(pkg)` wraps the SDK callback in `suspendCancellableCoroutine`; cancel maps to `Cancelled` (never an error); a missing foreground Activity maps to a typed `Error`, not a crash.
5. **Restore** — always visible on the paywall; on success the entitlement flow re-emits.
6. **Reactivity** — `addCustomerInfoUpdateListener` pushes `CustomerInfo` changes into `isFullJourneyUnlocked: StateFlow<Boolean>`; the paywall observes it, so a restored purchase unlocks without a restart. After unlock, navigation hands straight over to the story beat that follows — no dead end.

Backend selection is one `if` in `BaoriApplication`; swapping Test Store for production is a key change, not a code change.

---

## 7. State, settings, and theming

- **One ViewModel per screen, `StateFlow` only.** `RootViewModel` (in `MainActivity.kt`) owns all four so cross-screen reactions (paywall ↔ game state, settings ↔ theme) don't need event buses or shared flows.
- **Navigation** is a `List<Screen>` backstack in `BaoriRoot` — four destinations don't justify a navigation library; the sealed `Screen` type keeps transitions exhaustive and the system Back key is wired to the same `pop()`.
- **Settings** (`reduceMotion`, `muted`, theme override) persist in DataStore via `SettingsViewModel`. `BaoriTheme` bridges them into CompositionLocals: `LocalBaoriTokens` (colors) and `LocalBaoriMotion` (durations + ease functions). Every animation reads `GameMotion`, which collapses durations under reduce motion — the accessibility toggle has exactly one implementation point.
- **Tokens everywhere**: both palettes define the full semantic set (surfaces, interaction, borders, plus game-specific roles: `pathLit`, `pathDormant`, `water`, `goal`, `protagonist`, `mural`…). Components pick *roles*, never hex values.

---

## 8. Why these choices (for the record)

| Decision | Rationale |
|---|---|
| No 3D engine | ~100 lines of pure Kotlin reproduce the illusion; no engine setup cost, APK weight, or debugging surface within a 3–5 day window — and the math runs identically in JVM unit tests (PRD §7.4) |
| Canvas, not custom View | Compose Canvas keeps state-driven redraw declarative; the scene is a pure function of `GameUiState` |
| Hand-rolled backstack | 4 screens; a nav library adds dependency weight and indirection with nothing to route |
| Levels as JSON | Level design becomes data editing; puzzles readable by judges as plain text; JVM-testable via `AssetReader` |
| `BillingService` interface | SDK isolated to one class: clean seam for review, fake for tests, config-level swap for production |
| Pure `engine/` package | The heart of the game (8 test suites, 69 tests) has zero Android imports — fast tests, no emulator, no flakiness |
| Token-driven theming | Two full palettes, zero call-site colors; light/dark and future themes are data changes |
| Reduce-motion as a token | PRD §9 requirement collapses every animation through one `GameMotion` object — one implementation point, no per-screen conditionals |

---

*End of ARCHITECTURE.md.*
