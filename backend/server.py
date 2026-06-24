from pathlib import Path
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from .models import DspState, GainRequest, FilterRequest
from .device import DeviceController

app = FastAPI(title="AudioControl")
device = DeviceController()


@app.get("/api/health")
def health():
    return {"status": "ok", "device": device.device_type}


@app.get("/api/state", response_model=DspState)
def get_state():
    return device.get_state()


@app.post("/api/master-gain", response_model=DspState)
def set_master_gain(req: GainRequest):
    return device.set_master_gain(req.value)


@app.post("/api/sub-gain", response_model=DspState)
def set_sub_gain(req: GainRequest):
    return device.set_sub_gain(req.value)


@app.post("/api/hpf", response_model=DspState)
def set_hpf(req: FilterRequest):
    return device.set_hpf(freq=req.freq, bypass=req.bypass)


@app.post("/api/lpf", response_model=DspState)
def set_lpf(req: FilterRequest):
    return device.set_lpf(freq=req.freq, bypass=req.bypass)


@app.post("/api/reset", response_model=DspState)
def reset():
    return device.reset()


# Serve frontend — only mount if the directory exists (it's built in Task 3)
frontend_dir = Path(__file__).parent.parent / "frontend"

if frontend_dir.exists():
    app.mount("/static", StaticFiles(directory=str(frontend_dir)), name="static")

    @app.get("/")
    def serve_index():
        return FileResponse(frontend_dir / "index.html")
