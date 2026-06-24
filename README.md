# AudioControl

Touch-first web control panel for the miniDSP Flex HTx subwoofer chain.
Runs on the Windows laptop (where the HTx is USB-connected), accessed from
a tablet or phone on the tailnet.

## Quick start

    pip install -r backend/requirements.txt
    python run.py --mock          # no hardware needed
    python run.py                 # real device (miniDSP CLI must be installed)

Open http://<tailnet-hostname>:8080 on your tablet.

## Controls

- **Master Volume** — slider + ±1 dB steppers
- **Sub Gain** — slider + ±0.5 dB steppers (both subs)
- **High-Pass** — ±5 Hz steppers, bypass switch
- **Low-Pass** — ±5 Hz steppers, bypass switch
- **Bandpass curve** — live-computed, shows actual sub passband
- **Reset** — two-tap to restore defaults (Sub +4.0, HPF 45, LPF 200)

## Tests

    python -m pytest tests/ -v

## Architecture

    run.py              → entry point (uvicorn)
    backend/server.py   → FastAPI routes + static file serving
    backend/device.py   → device abstraction (mock)
    backend/device_minidsp.py → real miniDSP CLI integration
    backend/models.py   → Pydantic API models
    frontend/index.html → single-file control surface
