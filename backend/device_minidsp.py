"""Real miniDSP Flex HTx device controller (Task 4).

Implements live crossover control by computing Linkwitz-Riley biquad
coefficients in Python and writing them to the device via the minidsp-rs
CLI. See ``docs/spike-crossover-results.md`` for the spike findings that
drive every constant in this module.
"""
import math
import os
import shutil
import subprocess

from .device import DeviceController


# --- Hardware / profile configuration (from the spike) -----------------------

# The Flex HTx reports hw_id 32, which minidsp-rs v0.1.12 does not auto-map,
# so it detects "Generic" and per-channel commands fail "out of range".
# Forcing the profile loads the correct channel/crossover memory map.
FORCE_KIND = "flexhtx"

# Output channel indices for the two subwoofers. Confirmed from the Device
# Console Matrix Mixer: 0=Main L, 1=Main R, 2=Sub L, 3=Sub R.
SUB_OUTPUTS = (2, 3)

# Flex HTx internal DSP sample rate, used for biquad coefficient math.
SAMPLE_RATE = 96000.0

# Both filters live in crossover group 0 — the bank proven to be in the signal
# path during the spike. Each output has 2 groups x 4 biquads; we use group 0:
#   biquads 0,1 -> LR4 high-pass     biquads 2,3 -> LR4 low-pass
CROSSOVER_GROUP = 0
HPF_INDICES = (0, 1)
LPF_INDICES = (2, 3)

# LR4 (24 dB/oct Linkwitz-Riley) = two cascaded 2nd-order Butterworth sections.
BUTTERWORTH_Q = 1.0 / math.sqrt(2.0)


def _resolve_binary() -> str:
    """Locate the minidsp-rs executable."""
    env = os.environ.get("AUDIOCONTROL_MINIDSP_BIN")
    if env:
        return env
    found = shutil.which("minidsp")
    if found:
        return found
    return "minidsp"  # let subprocess surface a clear FileNotFoundError


# --- Biquad coefficient math -------------------------------------------------

def _normalize(b0, b1, b2, a0, a1, a2):
    """Normalize by a0 and sign-flip a1/a2 for miniDSP's convention.

    miniDSP biquads compute  y = b0*x0 + b1*x1 + b2*x2 + a1*y1 + a2*y2
    (note the + on the feedback terms), so the standard RBJ a1/a2 are negated.
    Returns the 5 device coefficients: (b0, b1, b2, a1, a2).
    """
    return (b0 / a0, b1 / a0, b2 / a0, -a1 / a0, -a2 / a0)


def lowpass_biquad(f0, fs=SAMPLE_RATE, q=BUTTERWORTH_Q):
    """RBJ low-pass biquad in miniDSP coefficient form."""
    w0 = 2.0 * math.pi * f0 / fs
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    return _normalize((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)


def highpass_biquad(f0, fs=SAMPLE_RATE, q=BUTTERWORTH_Q):
    """RBJ high-pass biquad in miniDSP coefficient form."""
    w0 = 2.0 * math.pi * f0 / fs
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    return _normalize((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)


# --- Controller --------------------------------------------------------------

class MinidspController(DeviceController):
    """Drives a miniDSP Flex HTx subwoofer chain via the minidsp-rs CLI.

    State is mirrored in memory (the base class); every mutation is also
    written through to the hardware. The device has no clean coefficient
    read-back, so the in-memory state is authoritative.
    """

    def __init__(self):
        super().__init__()
        self._bin = _resolve_binary()
        # Probe connectivity so the server can fall back to the mock if the
        # CLI or device is unavailable.
        self._run("status")

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
        # `--` guards the negative coefficients from being parsed as flags.
        self._run("output", str(output), "crossover", str(CROSSOVER_GROUP),
                  str(index), "set", "--", *(f"{c:.10f}" for c in coeffs))

    def _clear_biquad(self, output, index):
        self._run("output", str(output), "crossover", str(CROSSOVER_GROUP),
                  str(index), "clear")

    def _apply_filter(self, indices, sections, bypass):
        """Write an LR4 filter (2 biquads) to both subs, or clear it if bypassed."""
        for out in SUB_OUTPUTS:
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

    # --- DeviceController overrides ------------------------------------------

    def set_master_gain(self, value):
        state = super().set_master_gain(value)
        self._run("gain", "--", str(state.master_gain))
        return state

    def set_sub_gain(self, value):
        state = super().set_sub_gain(value)
        for out in SUB_OUTPUTS:
            self._run("output", str(out), "gain", "--", str(state.sub_gain))
        return state

    def set_hpf(self, freq=None, bypass=None):
        state = super().set_hpf(freq, bypass)
        self._apply_filter(HPF_INDICES, self._hpf_sections(state.hpf.freq),
                           state.hpf.bypass)
        return state

    def set_lpf(self, freq=None, bypass=None):
        state = super().set_lpf(freq, bypass)
        self._apply_filter(LPF_INDICES, self._lpf_sections(state.lpf.freq),
                           state.lpf.bypass)
        return state

    def reset(self):
        state = super().reset()
        self.push_state()
        return state

    def push_state(self):
        """Write the full in-memory state to the hardware."""
        s = self._state
        self._run("gain", "--", str(s.master_gain))
        for out in SUB_OUTPUTS:
            self._run("output", str(out), "gain", "--", str(s.sub_gain))
        self._apply_filter(HPF_INDICES, self._hpf_sections(s.hpf.freq), s.hpf.bypass)
        self._apply_filter(LPF_INDICES, self._lpf_sections(s.lpf.freq), s.lpf.bypass)
        return self.get_state()

    @property
    def device_type(self) -> str:
        return "connected"
