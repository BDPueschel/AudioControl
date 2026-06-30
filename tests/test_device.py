import math

from backend.device import DeviceController
from backend.device_minidsp import lowpass_biquad, highpass_biquad


# --- base controller: clamping / overlap / reset / persistence ---------------

def test_overlap_guard_hpf():
    d = DeviceController()
    d.set_lpf("subs", freq=100)
    assert d.set_hpf("subs", freq=95).subs.hpf.freq == 90


def test_overlap_guard_lpf():
    d = DeviceController()
    d.set_hpf("subs", freq=80)
    assert d.set_lpf("subs", freq=85).subs.lpf.freq == 90


def test_gain_step_rounding():
    d = DeviceController()
    assert d.set_gain("subs", 4.3).subs.gain == 4.5


def test_gain_clamps():
    d = DeviceController()
    assert d.set_gain("mains", 99.0).mains.gain == 12.0
    assert d.set_gain("mains", -99.0).mains.gain == -24.0


def test_master_caps():
    d = DeviceController()
    assert d.set_master_gain(0.0).master_gain == -20.0


def test_master_half_db_resolution():
    d = DeviceController()
    # half-dB steps must survive (were previously rounded to whole dB)
    assert d.set_master_gain(-35.5).master_gain == -35.5
    assert d.set_master_gain(-35.7).master_gain == -35.5  # snaps to 0.5 grid


def test_reset_preserves_master():
    d = DeviceController()
    d.set_master_gain(-30.0)
    d.set_gain("subs", 8.0)
    s = d.reset()
    assert s.master_gain == -30.0
    assert s.subs.gain == 4.0


def test_groups_independent():
    d = DeviceController()
    d.set_gain("mains", -5.0)
    d.set_gain("subs", 6.0)
    s = d.get_state()
    assert s.mains.gain == -5.0
    assert s.subs.gain == 6.0


def test_hpf_bypass_toggle():
    d = DeviceController()
    assert d.set_hpf("mains", bypass=False).mains.hpf.bypass is False
    assert d.set_hpf("mains", bypass=True).mains.hpf.bypass is True


def test_mute_toggle():
    d = DeviceController()
    assert d.set_mute(True).mute is True
    assert d.set_mute(False).mute is False


def test_persistence_round_trip(tmp_path):
    p = str(tmp_path / "state.json")
    d = DeviceController(state_path=p)
    d.set_gain("subs", 7.0)
    d.set_hpf("mains", freq=70, bypass=False)
    d.set_mute(True)
    # New controller loads from the same file
    d2 = DeviceController(state_path=p)
    s = d2.get_state()
    assert s.subs.gain == 7.0
    assert s.mains.hpf.freq == 70
    assert s.mains.hpf.bypass is False
    assert s.mute is True


# --- biquad coefficient math -------------------------------------------------

def _dc_gain(c):
    b0, b1, b2, a1, a2 = c
    return (b0 + b1 + b2) / (1 - a1 - a2)


def _nyquist_gain(c):
    b0, b1, b2, a1, a2 = c
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
    fs, f0 = 96000.0, 120.0
    b0, b1, b2, a1, a2 = lowpass_biquad(f0, fs)
    w = 2 * math.pi * f0 / fs
    z1 = complex(math.cos(-w), math.sin(-w))
    z2 = z1 * z1
    mag_db = 20 * math.log10(abs((b0 + b1 * z1 + b2 * z2) / (1 - a1 * z1 - a2 * z2)))
    assert math.isclose(mag_db, -3.01, abs_tol=0.1)


def test_coefficients_are_finite():
    for f in (20.0, 45.0, 200.0, 500.0):
        for c in (lowpass_biquad(f), highpass_biquad(f)):
            assert all(math.isfinite(x) for x in c)
            assert len(c) == 5
