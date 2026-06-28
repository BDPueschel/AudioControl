import math

from backend.device import DeviceController
from backend.device_minidsp import lowpass_biquad, highpass_biquad


# --- base controller: clamping / overlap / reset -----------------------------

def test_overlap_guard_hpf():
    d = DeviceController()
    d.set_lpf(freq=100)
    s = d.set_hpf(freq=95)
    assert s.hpf.freq == 90  # clamped to lpf - 10


def test_overlap_guard_lpf():
    d = DeviceController()
    d.set_hpf(freq=80)
    s = d.set_lpf(freq=85)
    assert s.lpf.freq == 90  # clamped to hpf + 10


def test_sub_gain_step_rounding():
    d = DeviceController()
    assert d.set_sub_gain(4.3).sub_gain == 4.5


def test_reset_preserves_master():
    d = DeviceController()
    d.set_master_gain(-30.0)
    d.set_sub_gain(8.0)
    s = d.reset()
    assert s.master_gain == -30.0
    assert s.sub_gain == 4.0


def test_hpf_bypass_toggle():
    d = DeviceController()
    assert d.set_hpf(bypass=True).hpf.bypass is True
    assert d.set_hpf(bypass=False).hpf.bypass is False


def test_hpf_freq_snaps_to_step():
    d = DeviceController()
    assert d.set_hpf(freq=47).hpf.freq == 45


def test_lpf_freq_snaps_to_step():
    d = DeviceController()
    assert d.set_lpf(freq=203).lpf.freq == 205


# --- biquad coefficient math -------------------------------------------------

def _dc_gain(coeffs):
    # Device convention: y = b0 x0 + b1 x1 + b2 x2 + a1 y1 + a2 y2
    # H(z) at DC (z=1) = (b0+b1+b2) / (1 - a1 - a2)
    b0, b1, b2, a1, a2 = coeffs
    return (b0 + b1 + b2) / (1 - a1 - a2)


def _nyquist_gain(coeffs):
    # H(z) at Nyquist (z=-1) = (b0 - b1 + b2) / (1 + a1 - a2)
    b0, b1, b2, a1, a2 = coeffs
    return (b0 - b1 + b2) / (1 + a1 - a2)


def test_lowpass_passes_dc_blocks_nyquist():
    c = lowpass_biquad(100.0)
    assert math.isclose(_dc_gain(c), 1.0, abs_tol=1e-6)
    assert abs(_nyquist_gain(c)) < 1e-6


def test_highpass_blocks_dc_passes_nyquist():
    c = highpass_biquad(100.0)
    assert abs(_dc_gain(c)) < 1e-6
    assert math.isclose(_nyquist_gain(c), 1.0, abs_tol=1e-6)


def test_butterworth_minus_3db_at_corner():
    # A single 2nd-order Butterworth section is -3 dB at its corner frequency.
    fs, f0 = 96000.0, 120.0
    b0, b1, b2, a1, a2 = lowpass_biquad(f0, fs)
    w = 2 * math.pi * f0 / fs
    z1 = complex(math.cos(-w), math.sin(-w))
    z2 = z1 * z1
    num = b0 + b1 * z1 + b2 * z2
    den = 1 - a1 * z1 - a2 * z2
    mag_db = 20 * math.log10(abs(num / den))
    assert math.isclose(mag_db, -3.01, abs_tol=0.1)


def test_coefficients_are_finite():
    for f in (20.0, 45.0, 200.0, 500.0):
        for c in (lowpass_biquad(f), highpass_biquad(f)):
            assert all(math.isfinite(x) for x in c)
            assert len(c) == 5
