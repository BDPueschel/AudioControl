# Audio Control Center — native Android client (design)

**Date:** 2026-06-29
**Status:** Approved design, ready for implementation plan
**Supersedes (for the native path):** `docs/android-apk-plan.md` recommendation #2
(WebView wrapper). We are going native (approach #3) deliberately, for
consistency with the other Kotlin/Compose apps in the workspace and for a
polished, native-feeling control surface.

## 1. Goal

A native Kotlin + Jetpack Compose Android app, **Audio Control Center**, that is
a faithful port of the live AudioControl DSP control surface. It talks to the
existing FastAPI backend over plain HTTP on the tailnet. The backend is the
single source of truth; the app is a thin client over the REST API, with two
deliberate extensions beyond a pure port (accent theming, filter-type
selection) called out below.

## 2. Scope

**In:**
- Master volume, master mute.
- Per-group (Mains / Subs) gain, high-pass, low-pass, bypass switches.
- Live passband curve (Compose Canvas).
- Two-tap reset.
- Connection state + manual reconnect.
- Accent theming (hue slider + preset chips).
- Filter-type / slope selection per filter (group-0 budget set).
- Adaptive portrait (phone) and landscape (tablet) layouts.
- Server host:port configuration.

**Out (v1):**
- Music playback stack (Now Playing / Quick Play / playlists). It is dormant in
  the live web panel (disabled on the move to Qobuz); endpoints exist but the UI
  is commented out. We match the live panel and exclude it.
- 48 dB/oct filters (requires crossover group-1 hardware verification — see §11).
- Any redesign beyond the gain/crossover card split in §6.

## 3. Stack

- Kotlin, Jetpack Compose, Material3. Single module.
- Networking: **Retrofit + kotlinx.serialization**. `DspState` maps cleanly to a
  serializable data class; mutations return the full new state.
- State: one `AudioRepository` + one `ControlViewModel` exposing
  `StateFlow<UiState>`. No Hilt, no Room.
- Persistence: **DataStore (Preferences)** for the three local settings — server
  host:port, accent hue, last-active group (mains/subs).
- Min SDK aligned with the workspace Android env (Platform 35, JDK 17). Release
  builds only, consistent with the other apps.

## 4. Backend API consumed

DSP endpoints only (music endpoints ignored). All mutations return `DspState`.

```
GET  /api/health              -> {"status": "ok"|..., "device": "mock"|"connected"}
GET  /api/state               -> DspState
POST /api/master-gain         {"value": float}
POST /api/mute                {"value": bool}
POST /api/{group}/gain        {"value": float}        group = mains|subs
POST /api/{group}/hpf         {"freq": int?, "bypass": bool?}
POST /api/{group}/lpf         {"freq": int?, "bypass": bool?}
POST /api/reset
```

**Filter-type extension (§11):** `hpf`/`lpf` request and `FilterState` gain a
`type` field (family+slope). This is the one change that touches the backend
model, not just the client.

## 5. Data model (client mirror)

```kotlin
@Serializable data class FilterState(
    val freq: Int,
    val bypass: Boolean,
    val type: FilterType = FilterType.LR4,   // §11; backend default preserves current behavior
)
@Serializable data class ChannelState(val gain: Double, val hpf: FilterState, val lpf: FilterState)
@Serializable data class DspState(
    val master_gain: Double, val mute: Boolean,
    val mains: ChannelState, val subs: ChannelState,
)
```

Ranges and steps (mirrored from backend, enforced client-side too):
- master_gain: −60 .. −20 dB, step 1. (−20 is the server safety cap.)
- gain: −24 .. +12 dB, step 0.5.
- hpf.freq: 20 .. 400 Hz, step 5.
- lpf.freq: 40 .. 500 Hz, step 5.
- **HPF/LPF minimum gap: 10 Hz** (a nudge that would violate it is clamped),
  matching the server.

## 6. Screen layout

Single screen. Top bar + a vertical card stack. **Crossover is split out of Gain
into its own card** (this was crammed into one tall card in the web portrait
view).

**Top bar:** app title, **Mute** toggle (fills the error color when muted),
**Reset** (two-tap), connection indicator (animated dot + label).

**Card stack (per active group):**
1. **Master** — gain control (stepper ±1, tappable value, level rail in
   landscape / number-drag in portrait).
2. **Mains / Subs segmented toggle** — only the active group's cards show; last
   choice persisted.
3. **Gain** — that group's gain (same control idiom as master, step 0.5).
4. **Crossover** — High-Pass + Low-Pass (each: bypass switch, ±5 Hz stepper,
   tappable value, **filter-type dropdown**) and the **passband curve** beneath,
   since the curve visualizes exactly these two filters.

**Adaptive layout (`WindowSizeClass`):**
- **Compact / portrait (phone):** single column, cards stacked. Slider rails
  hidden; the value number itself is draggable (preserve the web behavior).
- **Expanded / landscape (tablet):** Master spans the top; **Gain and Crossover
  sit side by side** as two panes. Level rails shown inline. This is a dedicated
  arrangement, not a stretched portrait.

## 7. Controls and interaction

- **Steppers:** ± buttons with the step labeled (1 dB / 0.5 / 5 Hz).
- **Tappable value:** tap the center value to type an exact number (clamped to
  range/step on commit).
- **Drag:** landscape level rail or portrait number-drag, both at the web's
  half-sensitivity relative feel.
- **Bypass switch:** toggles a filter; the bypassed filter's row dims and its
  curve marker hides.
- **Live commit while dragging:** commit during the drag, not only on
  release. Throttle by ~2 steps of value change AND coalesce in-flight — never
  more than one POST outstanding; on completion, send the latest pending value;
  a final authoritative commit fires on release. Gain/master are single CLI
  calls (cheap); crossover writes are multiple biquad writes, so coalescing
  matters most there.

## 8. Passband curve (Compose Canvas)

Direct port of the web math, redrawn on any freq/bypass/type change.

- x: log frequency, 20 Hz → 640 Hz across the width.
  `x(f) = ln(f/20)/ln(32)`
- y: 0 dB at top, −30 dB at bottom (clamped).
  `y(dB) = clamp(-dB, 0, 30) / 30`
- Per-filter rolloff, **generalized by slope** (the web hardcodes `^8` = 24
  dB/oct; the exponent becomes a parameter):
  `dB(f) = -10·log10(1 + (fc/f)^E_hpf) - 10·log10(1 + (f/fc)^E_lpf)`
  where exponent `E = slope_dB_per_oct / 3` (E=2→6, 4→12, 6→18, 8→24).
- Filled accent path under the curve; dashed HPF/LPF marker lines with dots that
  hide when that filter is bypassed. Axis labels 20/40/80/160/320/640 Hz.

The curve is a pure function of `ChannelState`, so it is **unit-tested**
independently of Compose.

## 9. Theming (accent only)

The app is monochrome greys/blacks with a single accent (default cyan `#5EC8D8`),
matching the workspace design language and the web panel.

- A theme sheet exposes an **accent hue slider** (HSL; S and L pinned near the
  cyan's values so every hue stays tasteful and on-language) and **4-5 preset
  chips** (cyan default + a few alternates).
- Stored in DataStore, applied live. Every accent use (toggles, knob, curve,
  positive-value text, connection dot) derives from one `accent` token, so a hue
  change recolors the whole app at once.

## 10. Connection and error UX (validated patterns)

- **Animated Status Indicator** — connection dot pulses in the accent when
  connected, solid error color when disconnected. `/api/health` polled every 5s.
- **Two-Tap Morph-to-Confirm** — Reset arms to "Tap again", auto-reverts after
  3s, shows a brief "Reset ✓".
- **Error banner** — when the backend is unreachable, show a clear
  "Can't reach panel — pull to retry" bar and keep the last-good values on
  screen (never a blank app). Phrasing follows the "Couldn't … — <recovery>"
  convention.
- **Pull-to-refresh** — `PullToRefreshBox` around the stack; pull-down
  re-pings `/api/health` and re-GETs `/api/state`.
- No optimistic UI in v1: render from the server response. Round trips are
  local-tailnet fast; honesty over perceived speed. Easy to add later.

## 11. Filter types (group-0 budget set)

**Hardware reality (from `docs/spike-crossover-results.md`):** crossovers on the
Flex HTx are realized as **raw biquad coefficients computed in Python**,
currently hardcoded to **LR4 (24 dB/oct)**. Crossover **group 0 holds 4 biquads
per output**: 2 for HPF (indices 0,1) + 2 for LPF (2,3). With both filters
engaged, each filter gets **at most 2 biquads = 4th order**.

**v1 menu (everything that fits group 0):**
- Linkwitz-Riley: LR2 (12), **LR4 (24, default)**.
- Butterworth: 6 / 12 / 18 / 24 dB/oct.
- Bessel: 12 / 24 dB/oct.
- Presented as one dropdown of concrete combos (e.g. "LR4 · 24 dB/oct"), with a
  **Live Caption beneath** the selection explaining the choice.

**Deferred:** 48 dB/oct (LR8 / 8th order) needs 4 biquads per filter → crossover
group 1, whose routing the spike explicitly left **unverified**. Out of v1;
tracked as a hardware-verification follow-up.

**Split of work:**
- **This repo / mock (built and verified on the Mac Mini):** add `type` to
  `FilterState` (request + model), default `LR4` to preserve current behavior;
  mock stores it; the native curve and dropdown are fully functional against the
  mock.
- **Real hardware (later, Windows side):** `MinidspController` computes the
  correct coefficients per family/order (Butterworth/Bessel/LR at the chosen
  slope), respecting the biquad budget. The client is built so this can land
  without further client changes.

## 12. Server configuration

A native client cannot just be "opened at a URL", so the host is configurable:
- Default to the laptop host `poolroom-syn.taildbeee4.ts.net:8080`.
- A small settings affordance edits host:port (so it can point at the Mac Mini
  mock during dev). Stored in DataStore.

## 13. Testing

- **Unit:** curve math (incl. generalized slope), HPF/LPF gap clamp, range/step
  clamping, dB/Hz formatting. Pure functions, high value.
- **Repository:** thin tests against the mock backend
  (`run.py --mock`) for the request/response round trip.
- No heavy UI-test harness in v1.

## 14. Dev/build notes

- Build against the existing backend in mock mode on the Mac Mini:
  `python run.py --port <p> --mock` (binds 0.0.0.0). Point the app's host
  setting at the Mac Mini's tailnet host:port.
- Release builds only; JDK 17; Android SDK Platform 35; Gradle wrapper.

## 15. Open follow-ups (post-v1)

- 48 dB/oct after verifying crossover group-1 routing on hardware.
- Real-device coefficient generation for the non-LR4 filter types.
- Optional optimistic UI if any control ever feels laggy on real hardware.
- Keep-awake / orientation-lock for a wall-mounted tablet (from
  `docs/android-apk-plan.md`).
