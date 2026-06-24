# AudioControl — miniDSP Control Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a touch-first web app that controls a miniDSP Flex HTx subwoofer chain (master volume, sub gain, HPF/LPF crossover with live curve, bypass, reset-to-defaults), served from a Windows laptop to a tablet over the tailnet.

**Architecture:** Python FastAPI backend exposes a REST API that abstracts the miniDSP control path (CLI shim or preset-based fallback). Single-file HTML/CSS/JS frontend consumes the API. The backend owns all device communication; the frontend is purely a control surface. The two are decoupled by a well-defined JSON API contract so either side can be developed and tested independently.

**Tech Stack:** Python 3.11+, FastAPI, uvicorn, single-file HTML/CSS/JS frontend (no build step), SVG for the crossover curve.

## Global Constraints

- **Design language:** monochrome (greys/blacks) + single cyan accent `#5EC8D8`, used sparingly for active values and interactive accents.
- **Serve on `0.0.0.0`**, not localhost. Hand out the Tailscale URL.
- **Runtime host:** Windows laptop (HTx connected via USB). Code lives in this repo on the Mac Mini.
- **No build tooling** for the frontend. One HTML file, no npm/webpack/vite.
- **Python deps:** fastapi, uvicorn, httpx (for shim proxy). No heavy frameworks.
- **The crossover-frequency spike (Task 1) is a manual test on the Windows laptop.** It gates whether Task 4 uses live CLI control or preset-based fallback. Tasks 2-3 and 5-6 can proceed in parallel regardless.

---

### Task 1: Spike — Can the miniDSP CLI set crossover frequencies on the Flex HTx?

**Files:**
- Create: `docs/spike-crossover-results.md` (findings)

**Interfaces:**
- Consumes: nothing
- Produces: a go/no-go answer that determines Task 4's implementation path

> **This task is a MANUAL test run by the user on the Windows laptop with the HTx connected.** It cannot be automated from the Mac Mini.

- [ ] **Step 1: Install the miniDSP Device Console CLI on the Windows laptop**

Download and install miniDSP Device Console (v1.1.15+) from https://docs.minidsp.com/product-manuals/flex-htx/device-console.html. Confirm the CLI is accessible from a terminal (PowerShell or cmd).

- [ ] **Step 2: Confirm basic CLI connectivity**

```powershell
# List connected devices
minidsp probe

# Read current master gain
minidsp gain -- -10
```

Expected: device is detected, gain command succeeds (listen for the volume change).

- [ ] **Step 3: Test crossover frequency control**

Try setting a crossover frequency via CLI. The exact command depends on what the CLI exposes:

```powershell
# Attempt: set output channel 0 crossover low-pass to 120 Hz
minidsp output 0 crossover 0 freq 120

# Or via peq/filter index — try variations:
minidsp output 0 filter 0 lowpass lr24 120
minidsp output 0 filter 0 highpass lr24 50
```

If none of these work, check `minidsp --help` and `minidsp output --help` for available subcommands.

- [ ] **Step 4: Test per-channel gain**

```powershell
# Set output channel 0 gain to +4.0 dB
minidsp output 0 gain -- 4.0

# Set output channel 1 gain to +4.0 dB (second sub)
minidsp output 1 gain -- 4.0
```

- [ ] **Step 5: Document findings**

Write findings to `docs/spike-crossover-results.md`:
- Which commands worked
- Exact syntax for: master gain, per-channel gain, crossover HPF, crossover LPF, mute
- Any commands that returned errors
- Whether the community shim (`ws_shim.py`) is needed or if the CLI alone is sufficient
- Decision: **live crossover** (if freq commands work) or **preset fallback** (if not)

---

### Task 2: Backend — FastAPI server with mock device layer

**Files:**
- Create: `backend/server.py` (FastAPI app + routes)
- Create: `backend/device.py` (device abstraction — interface + mock impl)
- Create: `backend/models.py` (Pydantic models for API contract)
- Create: `backend/requirements.txt`
- Create: `tests/test_api.py`

**Interfaces:**
- Consumes: nothing (uses mock device layer)
- Produces:
  - `GET /api/state` → `DspState` JSON
  - `POST /api/master-gain` body `{"value": float}` → `DspState`
  - `POST /api/sub-gain` body `{"value": float}` → `DspState`
  - `POST /api/hpf` body `{"freq": int, "bypass": bool}` → `DspState`
  - `POST /api/lpf` body `{"freq": int, "bypass": bool}` → `DspState`
  - `POST /api/reset` → `DspState`
  - `GET /api/health` → `{"status": "ok", "device": "mock"|"connected"}`
  - Static file serving for `frontend/index.html` at `/`

- [ ] **Step 1: Write requirements.txt**

```
# backend/requirements.txt
fastapi==0.115.*
uvicorn[standard]==0.34.*
httpx==0.28.*
pytest==8.*
httpx  # also used by pytest for TestClient
```

- [ ] **Step 2: Write the Pydantic models (API contract)**

```python
# backend/models.py
from pydantic import BaseModel

class FilterState(BaseModel):
    freq: int
    bypass: bool

class DspState(BaseModel):
    master_gain: float    # -60.0 to 0.0, step 1.0
    sub_gain: float       # -24.0 to 12.0, step 0.5
    hpf: FilterState      # freq 20-400, step 5
    lpf: FilterState      # freq 40-500, step 5

DEFAULTS = DspState(
    master_gain=-18.0,
    sub_gain=4.0,
    hpf=FilterState(freq=45, bypass=False),
    lpf=FilterState(freq=200, bypass=False),
)

class GainRequest(BaseModel):
    value: float

class FilterRequest(BaseModel):
    freq: int | None = None
    bypass: bool | None = None
```

- [ ] **Step 3: Write the device abstraction**

```python
# backend/device.py
from models import DspState, FilterState, DEFAULTS
import copy

class DeviceController:
    """Abstract base for device communication.
    Subclass this for real CLI/shim integration (Task 4)."""

    def __init__(self):
        self._state = copy.deepcopy(DEFAULTS)

    def get_state(self) -> DspState:
        return self._state.model_copy()

    def set_master_gain(self, value: float) -> DspState:
        clamped = max(-60.0, min(0.0, round(value * 2) / 2))
        self._state.master_gain = clamped
        return self.get_state()

    def set_sub_gain(self, value: float) -> DspState:
        clamped = max(-24.0, min(12.0, round(value * 2) / 2))
        self._state.sub_gain = clamped
        return self.get_state()

    def set_hpf(self, freq: int | None = None, bypass: bool | None = None) -> DspState:
        if freq is not None:
            gap_max = self._state.lpf.freq - 10
            self._state.hpf.freq = max(20, min(gap_max, round(freq / 5) * 5))
        if bypass is not None:
            self._state.hpf.bypass = bypass
        return self.get_state()

    def set_lpf(self, freq: int | None = None, bypass: bool | None = None) -> DspState:
        if freq is not None:
            gap_min = self._state.hpf.freq + 10
            self._state.lpf.freq = max(gap_min, min(500, round(freq / 5) * 5))
        if bypass is not None:
            self._state.lpf.bypass = bypass
        return self.get_state()

    def reset(self) -> DspState:
        master = self._state.master_gain  # preserve master volume
        self._state = copy.deepcopy(DEFAULTS)
        self._state.master_gain = master
        return self.get_state()

    @property
    def device_type(self) -> str:
        return "mock"
```

- [ ] **Step 4: Write the failing tests**

```python
# tests/test_api.py
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'backend'))

from fastapi.testclient import TestClient
from server import app

client = TestClient(app)

def test_health():
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"

def test_get_state_returns_defaults():
    r = client.get("/api/state")
    assert r.status_code == 200
    s = r.json()
    assert s["sub_gain"] == 4.0
    assert s["hpf"]["freq"] == 45
    assert s["lpf"]["freq"] == 200

def test_set_master_gain():
    r = client.post("/api/master-gain", json={"value": -25.0})
    assert r.status_code == 200
    assert r.json()["master_gain"] == -25.0

def test_master_gain_clamps():
    r = client.post("/api/master-gain", json={"value": 10.0})
    assert r.json()["master_gain"] == 0.0
    r = client.post("/api/master-gain", json={"value": -100.0})
    assert r.json()["master_gain"] == -60.0

def test_set_sub_gain():
    r = client.post("/api/sub-gain", json={"value": 6.5})
    assert r.status_code == 200
    assert r.json()["sub_gain"] == 6.5

def test_sub_gain_clamps():
    r = client.post("/api/sub-gain", json={"value": 20.0})
    assert r.json()["sub_gain"] == 12.0

def test_set_hpf_freq():
    r = client.post("/api/hpf", json={"freq": 60})
    assert r.status_code == 200
    assert r.json()["hpf"]["freq"] == 60

def test_set_hpf_bypass():
    r = client.post("/api/hpf", json={"bypass": True})
    assert r.status_code == 200
    assert r.json()["hpf"]["bypass"] is True

def test_set_lpf_freq():
    r = client.post("/api/lpf", json={"freq": 150})
    assert r.status_code == 200
    assert r.json()["lpf"]["freq"] == 150

def test_overlap_guard_hpf_cant_exceed_lpf():
    client.post("/api/lpf", json={"freq": 100})
    r = client.post("/api/hpf", json={"freq": 95})
    assert r.json()["hpf"]["freq"] == 90  # clamped to lpf - 10

def test_overlap_guard_lpf_cant_go_below_hpf():
    client.post("/api/hpf", json={"freq": 80})
    r = client.post("/api/lpf", json={"freq": 85})
    assert r.json()["lpf"]["freq"] == 90  # clamped to hpf + 10

def test_reset_preserves_master_volume():
    client.post("/api/master-gain", json={"value": -30.0})
    client.post("/api/sub-gain", json={"value": 8.0})
    client.post("/api/hpf", json={"freq": 60})
    r = client.post("/api/reset")
    s = r.json()
    assert s["master_gain"] == -30.0  # preserved
    assert s["sub_gain"] == 4.0       # reset to default
    assert s["hpf"]["freq"] == 45     # reset to default
    assert s["lpf"]["freq"] == 200    # reset to default

def test_reset_clears_bypass():
    client.post("/api/hpf", json={"bypass": True})
    r = client.post("/api/reset")
    assert r.json()["hpf"]["bypass"] is False
```

- [ ] **Step 5: Run tests to verify they fail**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
python -m pytest tests/test_api.py -v
```

Expected: ImportError — `server` module doesn't exist yet.

- [ ] **Step 6: Write the FastAPI server**

```python
# backend/server.py
from pathlib import Path
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from models import DspState, GainRequest, FilterRequest
from device import DeviceController

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

# Serve frontend
frontend_dir = Path(__file__).parent.parent / "frontend"

@app.get("/")
def serve_index():
    return FileResponse(frontend_dir / "index.html")
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
python -m pytest tests/test_api.py -v
```

Expected: all 13 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/ tests/
git commit -m "feat: FastAPI backend with mock device layer and API tests"
```

---

### Task 3: Frontend — Interactive control surface (offline-capable)

**Files:**
- Create: `frontend/index.html` (single-file HTML/CSS/JS)

**Interfaces:**
- Consumes: `GET /api/state`, `POST /api/master-gain`, `POST /api/sub-gain`, `POST /api/hpf`, `POST /api/lpf`, `POST /api/reset`, `GET /api/health`
- Produces: the user-facing control surface (no downstream tasks consume this)

This is the largest task. The frontend is a single self-contained HTML file with embedded CSS and JS. It replicates the interactive prototype from the brainstorming session, wired to the real API.

- [ ] **Step 1: Create the HTML structure**

Create `frontend/index.html` with the full document structure. The file contains:

1. **CSS** (embedded `<style>`) — the monochrome + cyan theme from the prototype. All colors as CSS custom properties on `:root`.
2. **HTML body** — the two-column layout:
   - Top bar: room label, reset button, connection indicator
   - Left column: Master Volume slider+steppers, Sub Gain slider+steppers
   - Right column: HPF steppers+bypass, LPF steppers+bypass, shared SVG bandpass curve
3. **JavaScript** (embedded `<script>`) — state management, API calls, drag interaction, curve rendering.

Key JS architecture:

```javascript
// State mirror — always reflects what the server last confirmed
let state = null;

// API helpers
async function api(method, path, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const r = await fetch('/api' + path, opts);
    if (!r.ok) return null;
    state = await r.json();
    render();
    return state;
}

// On load: fetch state, start health poll
async function init() {
    await api('GET', '/state');
    setInterval(pollHealth, 5000);
}

// Health poll — update connection dot
async function pollHealth() {
    try {
        const r = await fetch('/api/health');
        const h = await r.json();
        setConnected(h.status === 'ok');
    } catch { setConnected(false); }
}
```

Slider drag with scrub magnifier:

```javascript
function attachSlider(railId, magId, range, step, onSet) {
    const rail = document.getElementById(railId);
    const mag = document.getElementById(magId);
    let dragging = false;

    function update(clientX) {
        const b = rail.getBoundingClientRect();
        const frac = Math.max(0, Math.min(1, (clientX - b.left) / b.width));
        const raw = range[0] + frac * (range[1] - range[0]);
        const val = Math.round(raw / step) * step;
        onSet(val);
    }

    rail.addEventListener('pointerdown', e => {
        dragging = true;
        rail.setPointerCapture(e.pointerId);
        mag.style.display = 'block';
        update(e.clientX);
    });
    rail.addEventListener('pointermove', e => { if (dragging) update(e.clientX); });
    const end = () => { dragging = false; mag.style.display = 'none'; };
    rail.addEventListener('pointerup', end);
    rail.addEventListener('pointercancel', end);
}
```

Crossover curve rendering (SVG path generation):

```javascript
function drawCurve() {
    const N = 160;
    const hpf = state.hpf, lpf = state.lpf;
    let line = '', fill = '';

    for (let i = 0; i <= N; i++) {
        const frac = i / N;
        const f = 20 * Math.pow(32, frac);
        let dB = 0;
        if (!hpf.bypass) dB += -10 * Math.log10(1 + Math.pow(hpf.freq / f, 8));
        if (!lpf.bypass) dB += -10 * Math.log10(1 + Math.pow(f / lpf.freq, 8));
        const x = (frac * 300).toFixed(1);
        const y = Math.max(18, Math.min(108, 18 + Math.max(0, -dB) * 3)).toFixed(1);
        line += (i === 0 ? 'M' : ' L') + x + ',' + y;
    }
    fill = line + ' L300,120 L0,120 Z';

    document.getElementById('curve').setAttribute('d', line);
    document.getElementById('curveFill').setAttribute('d', fill);
    updateCornerMarker('hpf', hpf);
    updateCornerMarker('lpf', lpf);
}
```

Reset two-tap morph-to-confirm:

```javascript
let resetArmed = false, resetTimer = null;

function handleReset() {
    const btn = document.getElementById('reset');
    if (!resetArmed) {
        resetArmed = true;
        btn.classList.add('armed');
        btn.textContent = 'Tap again to reset';
        resetTimer = setTimeout(() => {
            resetArmed = false;
            btn.classList.remove('armed');
            btn.innerHTML = '&#8634; Reset';
        }, 3000);
    } else {
        clearTimeout(resetTimer);
        resetArmed = false;
        btn.classList.remove('armed');
        api('POST', '/reset');
        btn.classList.add('done');
        btn.textContent = 'Reset ✓';
        setTimeout(() => {
            btn.classList.remove('done');
            btn.innerHTML = '&#8634; Reset';
        }, 1200);
    }
}
```

The full HTML file should match the approved interactive prototype from the brainstorming session (`onestop-interactive.html`), with these changes:
- Replace inline state management with API-backed `state` object
- All mutations go through `api()` calls that update and re-render
- Add `init()` on DOMContentLoaded
- Add health poll with animated connection dot
- Debounce slider drag API calls (fire on pointerup, not every pointermove) to avoid flooding the backend

- [ ] **Step 2: Verify the prototype reference is available**

The approved interactive prototype is at:
```
AudioControl/.superpowers/brainstorm/17678-1782308401/content/onestop-interactive.html
```

Use it as the visual reference. The new `frontend/index.html` replicates its look exactly but wires to the API.

- [ ] **Step 3: Manual smoke test — serve and interact**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
pip install -r backend/requirements.txt
python -m uvicorn backend.server:app --host 0.0.0.0 --port 8080 --reload
```

Open `http://brians-mac-mini.taildbeee4.ts.net:8080/` in a browser. Verify:
- Two-column layout renders correctly in landscape
- Drag master volume slider → magnifier appears, value updates, API call fires on release
- Tap ±1 dB steppers → master gain changes
- Tap ±0.5 dB sub gain steppers → sub gain changes, positive values in cyan
- Tap ±5 Hz HPF/LPF steppers → frequency updates, curve redraws with knee shift
- Flip HPF bypass → row dims, knee flattens from curve
- Flip LPF bypass → same
- HPF can't cross LPF (overlap guard)
- Reset: first tap arms (red), second tap resets tuning, master volume preserved
- Connection dot pulses when healthy

- [ ] **Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: interactive control surface frontend wired to API"
```

---

### Task 4: Backend — Real device integration (post-spike)

**Files:**
- Create: `backend/device_minidsp.py` (real device controller, subclass of DeviceController)
- Modify: `backend/server.py` (swap mock for real controller based on env/flag)
- Create: `tests/test_device.py` (unit tests for clamping/validation in device layer)

**Interfaces:**
- Consumes: spike findings (Task 1), `DeviceController` base class (Task 2)
- Produces: real device integration used by the same API endpoints

> **This task is blocked on Task 1.** The implementation path depends on the spike results.

#### Path A: Live crossover control works

- [ ] **Step 1: Write the real device controller**

```python
# backend/device_minidsp.py
import subprocess
from device import DeviceController
from models import DspState

class MinidspController(DeviceController):
    """Wraps the miniDSP CLI for the Flex HTx."""

    def _run(self, *args: str) -> str:
        result = subprocess.run(
            ["minidsp", *args],
            capture_output=True, text=True, timeout=5,
        )
        if result.returncode != 0:
            raise RuntimeError(f"minidsp CLI error: {result.stderr.strip()}")
        return result.stdout.strip()

    def set_master_gain(self, value: float) -> DspState:
        state = super().set_master_gain(value)
        self._run("gain", "--", str(state.master_gain))
        return state

    def set_sub_gain(self, value: float) -> DspState:
        state = super().set_sub_gain(value)
        g = str(state.sub_gain)
        self._run("output", "0", "gain", "--", g)
        self._run("output", "1", "gain", "--", g)
        return state

    def set_hpf(self, freq=None, bypass=None) -> DspState:
        state = super().set_hpf(freq, bypass)
        if not state.hpf.bypass:
            # Exact CLI syntax TBD from spike — placeholder uses likely form
            self._run("output", "0", "filter", "0", "highpass", "lr24", str(state.hpf.freq))
            self._run("output", "1", "filter", "0", "highpass", "lr24", str(state.hpf.freq))
        else:
            self._run("output", "0", "filter", "0", "bypass", "on")
            self._run("output", "1", "filter", "0", "bypass", "on")
        return state

    def set_lpf(self, freq=None, bypass=None) -> DspState:
        state = super().set_lpf(freq, bypass)
        if not state.lpf.bypass:
            self._run("output", "0", "filter", "1", "lowpass", "lr24", str(state.lpf.freq))
            self._run("output", "1", "filter", "1", "lowpass", "lr24", str(state.lpf.freq))
        else:
            self._run("output", "0", "filter", "1", "bypass", "on")
            self._run("output", "1", "filter", "1", "bypass", "on")
        return state

    def reset(self) -> DspState:
        state = super().reset()
        self._run("gain", "--", str(state.master_gain))
        self._run("output", "0", "gain", "--", str(state.sub_gain))
        self._run("output", "1", "gain", "--", str(state.sub_gain))
        # Re-apply filters
        self.set_hpf(freq=state.hpf.freq, bypass=state.hpf.bypass)
        self.set_lpf(freq=state.lpf.freq, bypass=state.lpf.bypass)
        return state

    @property
    def device_type(self) -> str:
        return "connected"
```

> **Note:** The exact CLI syntax for filter commands (`highpass`, `lowpass`, `bypass`) MUST be updated based on Task 1 spike findings. The structure above is the likely form; the actual flags may differ.

- [ ] **Step 2: Wire the real controller into the server**

```python
# Modify backend/server.py — add at top, after imports:
import os

def create_device() -> DeviceController:
    if os.environ.get("AUDIOCONTROL_MOCK", "").lower() in ("1", "true", "yes"):
        return DeviceController()  # mock
    try:
        from device_minidsp import MinidspController
        return MinidspController()
    except Exception:
        return DeviceController()  # fallback to mock

# Replace: device = DeviceController()
# With:    device = create_device()
```

- [ ] **Step 3: Write device-layer unit tests**

```python
# tests/test_device.py
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'backend'))

from device import DeviceController

def test_overlap_guard_hpf():
    d = DeviceController()
    d.set_lpf(freq=100)
    s = d.set_hpf(freq=95)
    assert s.hpf.freq == 90

def test_overlap_guard_lpf():
    d = DeviceController()
    d.set_hpf(freq=80)
    s = d.set_lpf(freq=85)
    assert s.lpf.freq == 90

def test_sub_gain_step_rounding():
    d = DeviceController()
    s = d.set_sub_gain(4.3)
    assert s.sub_gain == 4.5

def test_reset_preserves_master():
    d = DeviceController()
    d.set_master_gain(-30.0)
    d.set_sub_gain(8.0)
    s = d.reset()
    assert s.master_gain == -30.0
    assert s.sub_gain == 4.0

def test_hpf_bypass_toggle():
    d = DeviceController()
    s = d.set_hpf(bypass=True)
    assert s.hpf.bypass is True
    s = d.set_hpf(bypass=False)
    assert s.hpf.bypass is False

def test_hpf_freq_snaps_to_step():
    d = DeviceController()
    s = d.set_hpf(freq=47)
    assert s.hpf.freq == 45

def test_lpf_freq_snaps_to_step():
    d = DeviceController()
    s = d.set_lpf(freq=203)
    assert s.lpf.freq == 205
```

- [ ] **Step 4: Run all tests**

```bash
python -m pytest tests/ -v
```

Expected: all tests pass (API + device layer).

- [ ] **Step 5: Commit**

```bash
git add backend/device_minidsp.py backend/server.py tests/test_device.py
git commit -m "feat: real miniDSP CLI device controller (post-spike)"
```

#### Path B: Crossover control NOT available — preset fallback

If the spike finds that crossover frequency cannot be set live, the `set_hpf` and `set_lpf` methods in `MinidspController` would instead map frequency values to pre-baked Device Console config slots (presets), and the `_run` call would switch presets:

```python
def set_hpf(self, freq=None, bypass=None) -> DspState:
    state = super().set_hpf(freq, bypass)
    preset = self._resolve_preset(state.hpf.freq, state.lpf.freq)
    self._run("config", str(preset))
    return state
```

The UI remains identical — the user still taps ±5 Hz — but under the hood each tap snaps to the nearest pre-baked config. This requires pre-building a matrix of configs in Device Console (e.g., HPF 30/35/.../100 × LPF 80/85/.../250) and storing the mapping. More setup, same UX.

---

### Task 5: Run script + deployment docs

**Files:**
- Create: `run.py` (entry point)
- Create: `README.md`

**Interfaces:**
- Consumes: `backend/server.py` (Task 2)
- Produces: a one-command way to start the server

- [ ] **Step 1: Write the run script**

```python
# run.py
"""Start the AudioControl server.

Usage:
    python run.py [--port 8080] [--mock]
"""
import argparse
import os
import uvicorn

def main():
    parser = argparse.ArgumentParser(description="AudioControl — miniDSP control panel")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--mock", action="store_true", help="Use mock device (no hardware needed)")
    args = parser.parse_args()

    if args.mock:
        os.environ["AUDIOCONTROL_MOCK"] = "1"

    uvicorn.run("backend.server:app", host="0.0.0.0", port=args.port, reload=False)

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Write the README**

```markdown
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
```

- [ ] **Step 3: Commit**

```bash
git add run.py README.md
git commit -m "feat: run script and README"
```

---

### Task 6: End-to-end smoke test (mock mode)

**Files:** none (testing only)

**Interfaces:**
- Consumes: everything from Tasks 2, 3, 5
- Produces: confidence that the full stack works in mock mode

- [ ] **Step 1: Start the server in mock mode**

```bash
cd /Users/bpueschel/Documents/CodeProjects/AudioControl
python run.py --mock
```

Expected: server starts on port 8080, prints "Uvicorn running on http://0.0.0.0:8080"

- [ ] **Step 2: Test API directly**

```bash
curl http://localhost:8080/api/health
# → {"status":"ok","device":"mock"}

curl http://localhost:8080/api/state
# → {"master_gain":-18.0,"sub_gain":4.0,"hpf":{"freq":45,"bypass":false},"lpf":{"freq":200,"bypass":false}}

curl -X POST http://localhost:8080/api/sub-gain -H 'Content-Type: application/json' -d '{"value": 6.0}'
# → {"master_gain":-18.0,"sub_gain":6.0,...}

curl -X POST http://localhost:8080/api/reset
# → sub_gain back to 4.0, master unchanged
```

- [ ] **Step 3: Test the UI in a browser**

Open `http://brians-mac-mini.taildbeee4.ts.net:8080/` and verify:
- Page loads with defaults (Sub +4.0, HPF 45, LPF 200)
- Connection dot shows "minidsp connected" (mock mode)
- All controls update the UI AND persist across page refresh (re-fetched from server)
- Curve redraws on HPF/LPF changes and bypass toggles
- Reset works (two-tap, preserves master)

- [ ] **Step 4: Run the full test suite one final time**

```bash
python -m pytest tests/ -v
```

Expected: all tests pass.

- [ ] **Step 5: Final commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: smoke test findings"
```
