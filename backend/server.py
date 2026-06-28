import os
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from .models import DspState, GainRequest, FilterRequest, MuteRequest, GROUPS
from .device import DeviceController


def create_device() -> DeviceController:
    """Pick the real miniDSP controller, falling back to the mock.

    AUDIOCONTROL_MOCK=1 forces the in-memory mock (no persistence) — used by
    tests. Otherwise we persist state to AUDIOCONTROL_STATE_FILE (default:
    repo/state.json) and try the real device, falling back to a persisting
    mock if the CLI or device is unavailable.
    """
    if os.environ.get("AUDIOCONTROL_MOCK", "").lower() in ("1", "true", "yes"):
        return DeviceController()
    state_path = os.environ.get("AUDIOCONTROL_STATE_FILE") or str(
        Path(__file__).parent.parent / "state.json"
    )
    try:
        from .device_minidsp import MinidspController
        return MinidspController(state_path=state_path)
    except Exception as exc:  # CLI missing, device unplugged, etc.
        print(f"[AudioControl] real device unavailable ({exc}); using mock.")
        return DeviceController(state_path=state_path)


app = FastAPI(title="AudioControl")
device = create_device()


def _check(group: str):
    if group not in GROUPS:
        raise HTTPException(status_code=404, detail=f"unknown group '{group}'")


@app.get("/api/health")
def health():
    return {"status": "ok", "device": device.device_type}


@app.get("/api/state", response_model=DspState)
def get_state():
    return device.get_state()


@app.post("/api/master-gain", response_model=DspState)
def set_master_gain(req: GainRequest):
    return device.set_master_gain(req.value)


@app.post("/api/mute", response_model=DspState)
def set_mute(req: MuteRequest):
    return device.set_mute(req.value)


@app.post("/api/{group}/gain", response_model=DspState)
def set_gain(group: str, req: GainRequest):
    _check(group)
    return device.set_gain(group, req.value)


@app.post("/api/{group}/hpf", response_model=DspState)
def set_hpf(group: str, req: FilterRequest):
    _check(group)
    return device.set_hpf(group, freq=req.freq, bypass=req.bypass)


@app.post("/api/{group}/lpf", response_model=DspState)
def set_lpf(group: str, req: FilterRequest):
    _check(group)
    return device.set_lpf(group, freq=req.freq, bypass=req.bypass)


@app.post("/api/reset", response_model=DspState)
def reset():
    return device.reset()


# Serve frontend — only mount if the directory exists.
frontend_dir = Path(__file__).parent.parent / "frontend"

if frontend_dir.exists():
    app.mount("/static", StaticFiles(directory=str(frontend_dir)), name="static")

    @app.get("/")
    def serve_index():
        return FileResponse(frontend_dir / "index.html")
