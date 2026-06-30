# Scenes — design (self-approved, full-rein build)

**Date:** 2026-06-30
**Status:** Approved (user delegated full authority), building.

## What & why
A listening-room controller is used in modes: a loud movie setup, a flat music
setup, a quiet late-night setup. **Scenes** capture the entire DSP state under a
name and recall it in one tap. It's the feature you reach for daily, and it's
**deployable now** — scenes live client-side and apply through the existing REST
endpoints, so it needs no backend change (works before the backend redeploy).

The distinctive, on-brand touch: each scene chip shows a **mini passband-curve
thumbnail** (read the shape, not just the name), and on recall the main curve
**pulses/animates** to the new shape. Reuses the curve renderer + pulse language.

## Scope (v1)
- Capture current full state as a named Scene.
- Recall a Scene (one tap) → replays its state through the endpoints.
- Mini-curve thumbnail on each scene chip (subs passband).
- Manage: long-press a chip → Overwrite / Rename / Delete.
- Client-side persistence (DataStore, JSON). No backend dependency.

Out of v1: time/auto recall, backend-side scene storage, cross-device sync,
per-channel partial scenes.

## Data
- `@Serializable data class Scene(val name: String, val dsp: DspState)` —
  `DspState` is already `@Serializable`.
- `ScenesStore(context)`: a DataStore Preferences string key holding
  `Json.encodeToString(List<Scene>)`. Exposes `scenes: Flow<List<Scene>>` and
  suspend `save(scene)` (insert or overwrite by name), `delete(name)`,
  `rename(old, new)`. Pure JSON (de)serialization is unit-tested.

## ViewModel
- `captureScene(name: String)`: build `Scene(name, currentDsp)` from `ui.value.dsp`
  (skip if null), persist via `ScenesStore`.
- `applyScene(scene: Scene)`: replay the scene's values through the repo so the
  device + server state converge, in order: master-gain, mute, then per group
  (mains, subs) gain, hpf(freq+bypass+type), lpf(freq+bypass+type). Set the
  filter-type overrides to the scene's types so the curve reflects them
  immediately (consistent with the existing overlay). Each call goes through the
  normal `applyMutation`/coalesced path; the last response leaves the UI on the
  scene. Fire the curve pulse on the new shape.
- These read/write through `AppContainer` (ScenesStore wired like SettingsStore).

## UI
- A **Scenes** strip (a horizontal scrollable `Row` of chips) placed under the
  Mains/Subs toggle (or its own card). Each chip: the scene name + a small
  passband-curve thumbnail (the existing `PassbandCurve` rendered tiny, subs
  group, non-interactive) + accent border when it matches the current state.
- A trailing **"+ Save"** chip → name dialog → `captureScene`.
- `combinedClickable`: tap = `applyScene`; long-press = a dropdown/sheet with
  Overwrite (re-capture current into this name), Rename, Delete.
- Monochrome + accent; OLED-aware like the rest.

## Delight (recall animation)
On `applyScene`, the curve animates toward the scene's shape. v1: drive the
existing per-node pulse + let the replayed state updates land; if a smooth tween
is cheap, interpolate the displayed `FilterCurveSpec` freqs over ~400ms. Keep it
subtle and skip-on-first-frame like the existing pulse.

## Testing
- `ScenesStore` JSON round-trip (save/list/overwrite/delete/rename) — pure, unit.
- VM `captureScene` builds a Scene equal to current dsp; `applyScene` replays the
  expected endpoint calls (fake api records them) and leaves the UI on the scene.

## Build order
1. **S1** — Scene model + ScenesStore + VM capture/apply + AppContainer wiring + tests.
2. **S2** — Scenes UI (chips + mini-curve thumbnails + save/rename/delete + recall).
3. **S3** — recall morph polish (optional, time-boxed).
