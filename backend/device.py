import copy
import threading
from pathlib import Path

from .models import DspState, DEFAULTS

# Safety cap on master output. The miniDSP itself goes to 0 dB (full output),
# but we never send louder than this to prevent accidental ear-blowing jumps.
MASTER_GAIN_MIN = -60.0
MASTER_GAIN_MAX = -20.0

CHANNEL_GAIN_MIN = -24.0
CHANNEL_GAIN_MAX = 12.0

HPF_FREQ_MIN = 20
LPF_FREQ_MAX = 500
FILTER_GAP = 10  # Hz minimum gap enforced between a channel's HPF and LPF


class DeviceController:
    """Abstract base + mock device. Holds the in-memory state mirror and,
    optionally, persists it to a JSON file so settings survive restarts.
    Subclass for real CLI/shim integration."""

    def __init__(self, state_path=None):
        self._lock = threading.RLock()
        self._state_path = Path(state_path) if state_path else None
        self._state = self._load()

    # --- persistence ---------------------------------------------------------
    def _load(self) -> DspState:
        if self._state_path and self._state_path.exists():
            try:
                return DspState.model_validate_json(self._state_path.read_text())
            except Exception:
                pass  # corrupt/incompatible -> fall back to defaults
        return copy.deepcopy(DEFAULTS)

    def _save(self):
        if self._state_path:
            try:
                self._state_path.write_text(self._state.model_dump_json(indent=2))
            except Exception:
                pass

    # --- reads ---------------------------------------------------------------
    def get_state(self) -> DspState:
        return self._state.model_copy(deep=True)

    def _channel(self, group: str):
        return getattr(self._state, group)

    # --- mutations -----------------------------------------------------------
    def set_master_gain(self, value: float) -> DspState:
        with self._lock:
            # Round to 0.5 dB resolution (matches sub-gain); whole-dB rounding
            # here silently dropped sub-dB master steps (e.g. a 0.5 dB step).
            self._state.master_gain = float(
                max(MASTER_GAIN_MIN, min(MASTER_GAIN_MAX, round(value * 2) / 2))
            )
            self._save()
            return self.get_state()

    def set_mute(self, value: bool) -> DspState:
        with self._lock:
            self._state.mute = bool(value)
            self._save()
            return self.get_state()

    def set_gain(self, group: str, value: float) -> DspState:
        with self._lock:
            ch = self._channel(group)
            ch.gain = max(CHANNEL_GAIN_MIN, min(CHANNEL_GAIN_MAX, round(value * 2) / 2))
            self._save()
            return self.get_state()

    def set_hpf(self, group: str, freq=None, bypass=None, type=None) -> DspState:
        with self._lock:
            ch = self._channel(group)
            if freq is not None:
                gap_max = ch.lpf.freq - FILTER_GAP
                # 1 Hz resolution; the client quantizes to the user's chosen
                # step, so don't re-snap to 5 Hz here (would drop a 1 Hz step).
                ch.hpf.freq = max(HPF_FREQ_MIN, min(gap_max, round(freq)))
            if bypass is not None:
                ch.hpf.bypass = bypass
            if type is not None:
                ch.hpf.type = type
            self._save()
            return self.get_state()

    def set_lpf(self, group: str, freq=None, bypass=None, type=None) -> DspState:
        with self._lock:
            ch = self._channel(group)
            if freq is not None:
                gap_min = ch.hpf.freq + FILTER_GAP
                # 1 Hz resolution (see set_hpf note).
                ch.lpf.freq = max(gap_min, min(LPF_FREQ_MAX, round(freq)))
            if bypass is not None:
                ch.lpf.bypass = bypass
            if type is not None:
                ch.lpf.type = type
            self._save()
            return self.get_state()

    def reset(self) -> DspState:
        with self._lock:
            master = self._state.master_gain
            self._state = copy.deepcopy(DEFAULTS)
            self._state.master_gain = master
            self._save()
            return self.get_state()

    @property
    def device_type(self) -> str:
        return "mock"
