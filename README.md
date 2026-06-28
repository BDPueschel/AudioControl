# AudioControl

Touch-first web control panel for the miniDSP Flex HTx subwoofer chain.
Runs on the Windows laptop (where the HTx is USB-connected), accessed from
a tablet or phone on the tailnet.

## Quick start

```bash
pip install -r backend/requirements.txt
python run.py --mock          # no hardware needed
python run.py                 # real device (miniDSP CLI must be installed)
```

Open `http://<tailnet-hostname>:8080` on your tablet.

## Current status

The UI and backend API are **complete and working in mock mode**. The app
is not yet wired to real hardware. The gating task is the **spike test**
below.

### Spike test: can the miniDSP CLI set crossover frequencies?

This must be run on the Windows laptop with the Flex HTx connected via USB.
Install [miniDSP Device Console](https://docs.minidsp.com/product-manuals/flex-htx/device-console.html)
(v1.1.15+), then try these in PowerShell:

```powershell
# 1. Confirm CLI sees the device
minidsp probe

# 2. Test master gain (listen for the change)
minidsp gain -- -10

# 3. Test per-channel gain (sub outputs)
minidsp output 0 gain -- 4.0
minidsp output 1 gain -- 4.0

# 4. THE KEY TEST: try setting crossover frequency
minidsp output 0 crossover 0 freq 120
# or:
minidsp output 0 filter 0 lowpass lr24 120
minidsp output 0 filter 0 highpass lr24 50
# Check: minidsp --help  and  minidsp output --help  for available subcommands
```

**Record what works and what doesn't.** Write findings to
`docs/spike-crossover-results.md`. The outcome determines how Task 4
(real device integration) gets built:

- **Crossover freq works** -> live +-5 Hz steppers as designed
- **Crossover freq doesn't work** -> preset-based fallback (pre-bake
  Device Console configs, steppers switch between them)

There is also a community control shim (Python, Feb 2026) that wraps the
CLI and exposes HTTP+WebSocket. See the design spec for details.

## Controls

- **Master Volume** -- slider + +-1 dB steppers (-60 to 0 dB)
- **Sub Gain** -- slider + +-0.5 dB steppers (-24 to +12 dB, both subs)
- **High-Pass** -- +-5 Hz steppers, bypass switch (default 45 Hz)
- **Low-Pass** -- +-5 Hz steppers, bypass switch (default 200 Hz)
- **Bandpass curve** -- live-computed SVG, shows actual sub passband
- **Reset** -- two-tap to restore defaults (Sub +4.0, HPF 45, LPF 200;
  master volume preserved)

## API

All endpoints return the full `DspState` on mutation:

    GET  /api/health        -> {"status": "ok", "device": "mock"|"connected"}
    GET  /api/state         -> DspState
    POST /api/master-gain   body: {"value": float}
    POST /api/sub-gain      body: {"value": float}
    POST /api/hpf           body: {"freq": int?, "bypass": bool?}
    POST /api/lpf           body: {"freq": int?, "bypass": bool?}
    POST /api/reset         (no body)

## Tests

```bash
python -m pytest tests/ -v    # 13 tests, all passing
```

## Architecture

```
run.py                  -> entry point (uvicorn on 0.0.0.0:8080)
backend/
  server.py             -> FastAPI routes + static file serving
  device.py             -> device abstraction (mock implementation)
  device_minidsp.py     -> real miniDSP CLI integration (Task 4, post-spike)
  models.py             -> Pydantic API models + defaults
frontend/
  index.html            -> single-file control surface (no build step)
docs/
  superpowers/specs/    -> design spec
  superpowers/plans/    -> implementation plan
```

## Key design decisions

- **Device abstraction**: `DeviceController` base class (mock) with
  `MinidspController` subclass (real). Set `AUDIOCONTROL_MOCK=1` env var
  or pass `--mock` to use the mock.
- **Overlap guard**: HPF and LPF maintain a 10 Hz minimum gap. Enforced
  both server-side and client-side.
- **Reset preserves master volume**: only resets sub gain, HPF, and LPF
  to defaults. Resetting volume mid-session could blast or mute the room.
- **Single-file frontend**: no npm, no build step. One HTML file with
  embedded CSS/JS.
- **Serve on 0.0.0.0**: accessible over the tailnet from tablet/phone.
