# Spike results: can the miniDSP CLI set crossover frequencies?

**Date:** 2026-06-28
**Hardware:** miniDSP Flex HTx (fw 1.55, hw_id 0x20/32, dsp_version 115),
USB-connected, active and confirmed by ear.
**Tooling:** minidsp-rs (mrene) **v0.1.12**, portable build at
`C:\Users\Brian\tools\minidsp\minidsp.exe`

## TL;DR — GO. Live crossover control works.

The CLI fully controls the Flex HTx. Crossovers are **not** set by frequency;
they take **raw biquad coefficients**. The app computes Linkwitz-Riley
coefficients in the backend and writes them via `crossover set`. This
preserves the live ±5 Hz stepper UX — no preset fallback needed.

Two hard requirements discovered:
1. **`--force-kind flexhtx` on every call.** Auto-detect sees hw_id 32 as
   "Generic" (v0.1.12 predates HTx auto-mapping), and the generic profile
   has no channel map → per-channel commands fail "out of range". Forcing
   the profile loads the correct map. (Verified: `flexhtx`, `flex`, `shd`,
   `m2x4hd`, `ddrc24` are all valid `--force-kind` values; `flexhtx` is the
   correct one.)
2. **The subs are outputs 2 and 3, not 0 and 1.** Confirmed from the Device
   Console Matrix Mixer: `0=Main L, 1=Main R, 2=Sub L, 3=Sub R`. Our first
   test drove outputs 0/1 and audibly thinned the *main speakers*; repeating
   on 2/3 correctly dropped the *subs*. The plan/README assumption of subs on
   0/1 was wrong for this unit.

## Environment notes

- The **official miniDSP Device Console** (GUI) is installed
  (`C:\Program Files\miniDSP Ltd`) but ships **no scriptable CLI** — only the
  USB audio driver. The scripting tool is the open-source **minidsp-rs**.
- `minidsp --force-kind flexhtx probe` → `Found FlexHtx ... [hw_id: 32,
  dsp_version: 115]`. Profile shows 8 inputs / 8 outputs.

## Verified on hardware

| Action | Command (all prefixed `--force-kind flexhtx`) | Result |
|---|---|---|
| Probe | `probe` | ✅ FlexHtx found |
| Read status | `status` | ✅ `MasterStatus { preset, source, volume, mute, dirac }` |
| Master volume | `gain -- <dB>` | ✅ |
| Sub gain | `output 2 gain -- <dB>` / `output 3 gain -- <dB>` | ✅ heard +2 dB boost |
| Crossover write | `output <N> crossover <G> <I> set -- <b0 b1 b2 a1 a2>` | ✅ heard 30 Hz LP drop the subs |
| Crossover restore | `output <N> crossover <G> <I> clear` | ✅ restores flat default + un-bypass |

**Gotcha:** negative coefficients (e.g. `a2`) are parsed as flags unless you
put `--` before the coefficient list: `... set -- 1.0 0 0 ...`.

## Crossover command structure

`output <N> crossover <GROUP 0|1> <INDEX 0|1|2|3> <set|bypass|clear|import>`

- **2 groups × 4 biquads** per output channel.
- `set -- <5 coeffs>` = raw biquad `b0 b1 b2 a1 a2`. **No** frequency form.
- `bypass <on|off>`, `clear` (→ flat default + un-bypass).
- No clean coefficient read-back (only `debug dump-float <addr>` by raw addr).

## How the app realizes HPF/LPF (Path C — implemented)

- **Filter type:** LR4 (24 dB/oct Linkwitz-Riley), matching the frontend
  curve/label. LR4 = **two cascaded 2nd-order Butterworth (Q=0.7071)**
  sections → 2 biquads per filter.
- **Bank layout:** both filters live in **crossover group 0** (the bank
  proven to be in the signal path during the spike), 4 biquads total:
  - biquads **0,1** = LR4 **high-pass**
  - biquads **2,3** = LR4 **low-pass**
  - group 1 left unused (avoids depending on its routing).
- **Bypass:** `clear` the relevant biquads (HPF→0,1; LPF→2,3).
- **Coefficient convention:** standard RBJ biquad, normalized by a0, with
  **a1/a2 sign-flipped** (miniDSP uses `y = b0x0+b1x1+b2x2 + a1y1 + a2y2`).
- **Sample rate:** `Fs = 96000` (Flex HTx internal DSP rate) — used for the
  coefficient math. If measured corner frequencies are off by a constant
  factor, this is the first constant to re-check.

## Integration requirements baked into `MinidspController`

- `--force-kind flexhtx` on every invocation.
- `SUB_OUTPUTS = (2, 3)`.
- minidsp binary must be resolvable (on PATH or via
  `AUDIOCONTROL_MINIDSP_BIN`). Installed here at
  `C:\Users\Brian\tools\minidsp\minidsp.exe`.

## Still worth a real-world check

- Confirm the **Fs=96000** assumption yields accurate corner frequencies
  (measurement mic or sweep).
- Confirm **true LR4 (-6 dB at corner)** is desired vs the frontend curve's
  approximation (4th-order Butterworth, -3 dB at corner). Cosmetic; the audio
  path uses true LR4.
