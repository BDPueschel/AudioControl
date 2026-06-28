import copy
from .models import DspState, FilterState, DEFAULTS

# Safety cap on master output. The miniDSP itself goes to 0 dB (full output),
# but we never send louder than this to prevent accidental ear-blowing jumps.
MASTER_GAIN_MIN = -60.0
MASTER_GAIN_MAX = -25.0


class DeviceController:
    """Abstract base for device communication.
    Subclass this for real CLI/shim integration (Task 4)."""

    def __init__(self):
        self._state = copy.deepcopy(DEFAULTS)

    def get_state(self) -> DspState:
        return self._state.model_copy(deep=True)

    def set_master_gain(self, value: float) -> DspState:
        clamped = float(max(MASTER_GAIN_MIN, min(MASTER_GAIN_MAX, round(value))))
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
