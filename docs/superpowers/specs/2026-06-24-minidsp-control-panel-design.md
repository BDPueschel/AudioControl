# AudioControl — miniDSP Control Panel (Design Spec)

**Date:** 2026-06-24
**Status:** Design approved (UI), backend has one open risk to resolve first
**Device:** miniDSP Flex HTx
**Host:** Windows laptop (same make/model as the Mac Mini user's other laptop, less RAM), on the tailnet
**Scope:** Subwoofer DSP control panel only. The Apple Music remote is parked (see Out of Scope).

---

## 1. Goal

A touch-first control surface for the subwoofer chain on a miniDSP Flex HTx, served as a web app from the host Windows laptop (where the HTx is USB-connected) and used from the tablet already living in the listening space. It replaces the fiddly Device Console UI and the physical metal remote for day-to-day adjustments: master volume, sub gain, and the sub's high-pass / low-pass crossover, with a live crossover curve and instant A/B bypass.

Design language: monochrome (greys / blacks) with a single cyan accent (`#5EC8D8`), used sparingly for active values, fills, and interactive accents.

---

## 2. Why a web app (not hardware, not a native app)

Earlier ideas in the thread, and why we landed here:

- **DIY BLE knob box (ESP32 + encoders):** fun, cheap, but single-purpose and still needs a software bridge. Parked as a possible future toy, not the primary.
- **MIDI controller (Korg nanoKONTROL etc.):** off-the-shelf but needs the same bridge and gives controls we will not use.
- **Web app served to the existing tablet (chosen):** zero new hardware, no app store, iterate by refreshing the page, reachable from phone too. The tablet is already in the room mirroring the laptop.

---

## 3. Architecture

```
Windows laptop (Flex HTx plugged in via USB)
├── Control backend (Python / FastAPI)
│   ├── wraps the Flex HTx control path (see §7)
│   ├── REST endpoints for gain / mute / crossover / preset
│   └── (future) WebSocket passthrough for live output meters
└── Static web UI (single HTML/CSS/JS surface)
        served on the tailnet (0.0.0.0 + tailscale host), NOT localhost-only

Tablet browser → http://<laptop-tailnet-hostname>:PORT
```

- **Host is a Windows laptop**, not the Mac Mini. The miniDSP CLI, Device Console, and Python all run on Windows. The community shim is pure Python and cross-platform. No Mac-specific dependencies in the DSP backend.
- The UI is a single self-contained screen (no routing). It reads current state on load and pushes changes as the user interacts.
- Serve on `0.0.0.0` and hand out the Tailscale URL so the tablet (and phone) on the tailnet can reach it.
- The control backend's *writes* are localhost-only at the shim layer (single-writer lease); the FastAPI wrapper is the one writer and brokers the tablet's requests.
- **Dev note:** code lives in this repo on the Mac Mini; deploy to the Windows laptop for runtime. The spike test (§7) runs directly on the Windows laptop.

---

## 4. UI design (approved)

Layout: tablet in landscape, two columns inside one panel. Every control shares one shape so it is muscle-memory after first use:

> **[ − ]  control / value  [ + ]** — minus always left, plus always right.

### Top bar
- Left: room label ("LISTENING ROOM").
- Right: **Reset** button (two-tap, see §5) and a live connection indicator (cyan dot + "minidsp connected"). The dot should use the Animated Status Indicator pattern when live.

### Left column — gains
1. **Master Volume**
   - Drag slider + flanking **−/+ 1 dB** steppers.
   - While dragging, a **scrub magnifier** (cyan, tabular number) floats above the thumb and tracks the finger the whole drag, then hides on release.
   - Range: −60 to 0 dB, 1 dB steps. Drag for big moves, tap ± for trim.
2. **Sub Gain (both subs)**
   - Same shape, **±0.5 dB** steps for fine level matching.
   - Range: −24 to +12 dB. Positive values render in cyan (it is a boost).

### Right column — crossover + curve
3. **High-Pass (subsonic)** — `−5 / +5 Hz` steppers, frequency readout, **bypass switch** in the header.
4. **Low-Pass (crossover)** — same shape, **bypass switch** in the header.
5. **Shared bandpass curve** beneath both filters:
   - One graph shows the sub's actual passband (high-pass knee on the left, low-pass knee on the right, flat plateau between). This is truer than two mirrored graphs.
   - A corner dot + label sits on each knee (`HPF 45`, `LPF 200`).
   - Log frequency axis 20 → 640 Hz; octave labels (20/40/80/160/320/640) land exactly on the gridlines.
   - Both knees move live as the steppers are tapped. When a filter is bypassed, its knee flattens out of the curve and its dot/label dim.

### Defaults (daily-driver values)
- High-Pass: **45 Hz**, engaged
- Low-Pass: **200 Hz**, engaged
- Sub Gain: **+4.0 dB**
- Master Volume: live listening level, **not** a tuning default (see §5)

---

## 5. Reset and bypass behavior

### Reset to defaults (two-tap morph-to-confirm)
- Restores tuning only: Sub Gain +4.0, HPF 45 (engaged), LPF 200 (engaged).
- **Deliberately does NOT touch Master Volume** — resetting the listening level mid-session would blast or mute the room unexpectedly.
- First tap arms the button (turns red, "Tap again to reset"); auto-disarms after 3 seconds; second tap applies. Uses the Two-Tap Morph-to-Confirm validated pattern so a stray tap cannot wipe a tuning session.

### Bypass toggles (A/B)
- Each filter (HPF, LPF) has its own bypass switch in its header.
- Flipping bypass: the control row dims, the state label flips to "bypassed," and that filter's knee flattens out of the live curve. Lets you hear and see the filter's contribution instantly.

### Overlap guard
- The HPF cannot cross the LPF. A minimum 10 Hz gap is enforced (HPF max = LPF − 10, LPF min = HPF + 10) so the passband can never collapse to silence.

---

## 6. Crossover curve math (for accurate rendering)

The curve is computed, not hand-drawn, so it stays honest as values change.

- Filter slope assumed **24 dB/oct, Linkwitz-Riley (LR4)** style (fixed; not user-editable in this version).
- Per-filter magnitude approximated with a Butterworth-form rolloff:
  - High-pass: `dB = −10 · log10(1 + (fc_hp / f)^8)`
  - Low-pass:  `dB = −10 · log10(1 + (f / fc_lp)^8)`
- Combined response = sum of engaged filters' dB (cascade adds in dB).
- X axis: `x = log(f / 20) / log(32)` over 20 → 640 Hz.
- Y axis: 0 dB at top, clamped at roughly −30 dB at the floor.
- Sample ~160 points across the band to build the SVG path; redraw on any change.

Note: this is a display approximation tuned to read correctly, not a bit-exact model of the HTx filters. If the device reports its actual filter type/slope, match the formula to it.

---

## 7. Backend / device control (OPEN RISK — resolve first)

**Key finding:** the Flex HTx is not the same as older miniDSP units for programmatic control.

- It runs miniDSP's **newer Device Console**, not the legacy plugin.
- **`minidsp-rs` does not support the Flex HTx** (it supports the original Flex and Flex DL only). The original backend plan is invalid for this device.
- There is **no official API** for the HTx.

### Available control paths
1. **Community reference shim (recommended starting point).** A developer published (Feb 2026) a Flex HTx control shim: `ws_shim.py` (service) + `flex_htx.py` (client example). It wraps the **miniDSP CLI** (which the author found more reliable than minidsp-rs), exposes an HTTP control API, streams real-time input/output levels over **WebSocket**, keeps a stateful internal model, and uses a single-writer automation lease (localhost-only writes). Confirmed controls: preset, source, **master gain**, **mute**, **per-channel gain**. The WebSocket level stream is a direct enabler for the future output meter.
2. **miniDSP CLI directly.** Wrap the official CLI ourselves in FastAPI. Same underlying mechanism the shim uses.
3. **Preset-based fallback.** If live crossover-frequency setting is not reachable (see risk below), pre-bake a small set of Device Console configurations (each with specific HPF/LPF/gain) and have the steppers switch presets. Coarser than live ±5 Hz but guaranteed to work.

### The open risk
The shim's confirmed parameters are gain / mute / preset / source / channel gain. **Live crossover HPF/LPF frequency control is unconfirmed.** Our two star controls depend on it.

**First implementation task, before any UI wiring:** determine whether the miniDSP CLI (and/or the shim) can set crossover high-pass and low-pass frequencies on the HTx at runtime.
- If **yes** → live steppers as designed.
- If **no** → preset-based crossover (fallback above), and the UI steppers snap between pre-baked configs instead of nudging a live value. UI is unchanged; only the wiring differs.

### Recommendation
Robust path: stand up our own FastAPI control backend, starting from the community shim (adapt endpoint URLs, device IDs, deployment) so we own the contract the tablet talks to and inherit the live-level WebSocket. Leaner alternative: run the community shim as-is and point a thin static UI at it directly. Recommend the FastAPI wrapper because it gives us a stable, documented contract, room for the meter, and a clean place to implement the preset fallback if needed.

---

## 8. Validated UX patterns used

- **Two-Tap Morph-to-Confirm** → Reset to defaults.
- **Live Caption Beneath Selection** (inverted to sit above the thumb) → scrub magnifier on the gain sliders.
- **Animated Status Indicator** → live connection dot (and future meter activity).
- **Reset-to-defaults** recovery pattern → §5.
- **Optimistic UI with Rejection Replacement** → apply control changes immediately, reconcile if the device/shim rejects (relevant once wired to hardware).

---

## 9. Out of scope (this spec) / future

- **Apple Music remote.** Parked behind a Parsec / Chrome Remote Desktop test. Since the host is Windows (not Mac), the earlier `osascript` automation approach is dead; if Parsec doesn't solve the mirroring crash, the Windows automation path would use iTunes/Apple Music for Windows COM automation or a different mechanism. Separate spec either way.
- **Live output meters.** The shim's WebSocket level stream makes this cheap. Park under the curve in the right column. Future slice.
- **Mute control.** Dropped from the panel for now (was with the deleted presets). Easy to re-add as a top-bar icon if wanted.
- **BLE knob box / MIDI controller.** Possible future physical companion; not part of the web app.
- **User-editable filter slope/type.** Fixed at LR4 24 dB/oct for now.

---

## 10. Open questions

1. **Crossover frequency control** on the HTx via CLI/shim — the gating unknown (§7).
2. **Master vs. sub routing:** confirm whether "master gain" on the HTx rides a bus that also moves the subs, against the actual device config, so the Master and Sub Gain controls behave as labeled.
3. ~~**Host machine**~~ — RESOLVED: Windows laptop on the tailnet (need its tailnet hostname for the serving URL).
4. **Mute:** include a top-bar mute toggle, or genuinely not needed?

---

## Sources
- miniDSP Flex HTx product page and manual (Device Console is the control software; earliest supporting version 1.1.15)
- `minidsp-rs` supported-devices list (Flex / Flex DL supported; Flex HT / HTx not listed)
- Community "Flex HTx WebSocket telemetry + REST control shim" reference implementation (miniDSP forum / AudioScienceReview, Feb 2026)
