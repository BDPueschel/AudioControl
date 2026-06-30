"""Real miniDSP Flex HTx device controller.

Live crossover control by computing Linkwitz-Riley biquad coefficients in
Python and writing them to the device via the minidsp-rs CLI. See
``docs/spike-crossover-results.md`` for the spike findings behind every
constant here.
"""
import math
import os
import re
import shutil
import subprocess

from .device import DeviceController, MASTER_GAIN_MIN, MASTER_GAIN_MAX


# --- hardware / profile configuration (from the spike) -----------------------

# hw_id 32 isn't auto-mapped by minidsp-rs v0.1.12 (detects "Generic", which
# has no channel map), so force the correct profile on every call.
FORCE_KIND = "flexhtx"

# Output channels per speaker group. Confirmed from the Device Console Matrix
# Mixer: 0=Main L, 1=Main R, 2=Sub L, 3=Sub R.
GROUP_OUTPUTS = {"mains": (0, 1), "subs": (2, 3)}

# Flex HTx internal DSP sample rate, used for biquad coefficient math.
SAMPLE_RATE = 96000.0

# Both filters live in crossover group 0 (the bank proven in-path). Each output
# has 2 groups x 4 biquads; we use group 0: biquads 0,1 = LR4 HPF, 2,3 = LR4 LPF.
CROSSOVER_GROUP = 0
HPF_INDICES = (0, 1)
LPF_INDICES = (2, 3)

# LR4 (24 dB/oct Linkwitz-Riley) = two cascaded 2nd-order Butterworth sections.
BUTTERWORTH_Q = 1.0 / math.sqrt(2.0)


def _resolve_binary() -> str:
    env = os.environ.get("AUDIOCONTROL_MINIDSP_BIN")
    if env:
        return env
    return shutil.which("minidsp") or "minidsp"


# --- biquad coefficient math -------------------------------------------------

def _normalize(b0, b1, b2, a0, a1, a2):
    """Normalize by a0 and sign-flip a1/a2 for miniDSP's convention
    (y = b0*x0 + b1*x1 + b2*x2 + a1*y1 + a2*y2)."""
    return (b0 / a0, b1 / a0, b2 / a0, -a1 / a0, -a2 / a0)


def lowpass_biquad(f0, fs=SAMPLE_RATE, q=BUTTERWORTH_Q):
    w0 = 2.0 * math.pi * f0 / fs
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    return _normalize((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)


def highpass_biquad(f0, fs=SAMPLE_RATE, q=BUTTERWORTH_Q):
    w0 = 2.0 * math.pi * f0 / fs
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    return _normalize((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)


# --- controller --------------------------------------------------------------

class MinidspController(DeviceController):
    """Drives the miniDSP Flex HTx (mains + subs) via the minidsp-rs CLI."""

    def __init__(self, state_path=None):
        super().__init__(state_path)
        self._bin = _resolve_binary()
        # `status` confirms connectivity and yields the device's master gain.
        status = self._run("status")
        self._sync_on_start(status)

    def _run(self, *args):
        cmd = [self._bin, "--force-kind", FORCE_KIND, *args]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        if result.returncode != 0:
            raise RuntimeError(
                f"minidsp CLI failed ({' '.join(args)}): {result.stderr.strip()}"
            )
        return result.stdout.strip()

    # --- low-level crossover helpers -----------------------------------------

    def _set_biquad(self, output, index, coeffs):
        self._run("output", str(output), "crossover", str(CROSSOVER_GROUP),
                  str(index), "set", "--", *(f"{c:.10f}" for c in coeffs))

    def _clear_biquad(self, output, index):
        self._run("output", str(output), "crossover", str(CROSSOVER_GROUP),
                  str(index), "clear")

    def _apply_filter(self, outputs, indices, sections, bypass):
        for out in outputs:
            for idx, coeffs in zip(indices, sections):
                if bypass:
                    self._clear_biquad(out, idx)
                else:
                    self._set_biquad(out, idx, coeffs)

    def _hpf_sections(self, freq):
        bq = highpass_biquad(freq)
        return (bq, bq)  # two identical Butterworth sections = LR4

    def _lpf_sections(self, freq):
        bq = lowpass_biquad(freq)
        return (bq, bq)

    def _write_gain(self, group):
        ch = getattr(self._state, group)
        for out in GROUP_OUTPUTS[group]:
            self._run("output", str(out), "gain", "--", str(ch.gain))

    def _write_hpf(self, group):
        ch = getattr(self._state, group)
        self._apply_filter(GROUP_OUTPUTS[group], HPF_INDICES,
                           self._hpf_sections(ch.hpf.freq), ch.hpf.bypass)

    def _write_lpf(self, group):
        ch = getattr(self._state, group)
        self._apply_filter(GROUP_OUTPUTS[group], LPF_INDICES,
                           self._lpf_sections(ch.lpf.freq), ch.lpf.bypass)

    # --- DeviceController overrides ------------------------------------------

    def set_master_gain(self, value):
        state = super().set_master_gain(value)
        self._run("gain", "--", str(state.master_gain))
        return state

    def set_mute(self, value):
        state = super().set_mute(value)
        self._run("mute", "on" if state.mute else "off")
        return state

    def set_gain(self, group, value):
        state = super().set_gain(group, value)
        self._write_gain(group)
        return state

    def set_hpf(self, group, freq=None, bypass=None):
        state = super().set_hpf(group, freq, bypass)
        self._write_hpf(group)
        return state

    def set_lpf(self, group, freq=None, bypass=None):
        state = super().set_lpf(group, freq, bypass)
        self._write_lpf(group)
        return state

    def reset(self):
        state = super().reset()
        self.push_state()
        return state

    # --- startup sync / full push --------------------------------------------

    def _sync_on_start(self, status):
        """Make device and UI agree at boot. Master has read-back, so seed the
        UI from the device and never raise volume (only lower if above the cap).
        Crossover + gains have no read-back, so push the (persisted) state."""
        m = re.search(r"Gain\(\s*(-?\d+(?:\.\d+)?)\s*\)", status)
        if m:
            dev_master = float(m.group(1))
            target = max(MASTER_GAIN_MIN, min(MASTER_GAIN_MAX, round(dev_master)))
            self._state.master_gain = target
            self._save()
            if target < dev_master:  # device louder than cap -> lower it
                self._run("gain", "--", str(target))
        mm = re.search(r"mute:\s*(true|false)", status)
        if mm:
            self._state.mute = (mm.group(1) == "true")
            self._save()
        self._push_channels()

    def push_state(self):
        with self._lock:
            self._run("gain", "--", str(self._state.master_gain))
            self._run("mute", "on" if self._state.mute else "off")
            self._push_channels()
            return self.get_state()

    def _push_channels(self):
        with self._lock:
            for group in GROUP_OUTPUTS:
                self._write_gain(group)
                self._write_hpf(group)
                self._write_lpf(group)

    @property
    def device_type(self) -> str:
        return "connected"
